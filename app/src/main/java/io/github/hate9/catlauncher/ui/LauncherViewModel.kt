/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.ui

import android.app.Application
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hate9.catlauncher.LauncherApp
import io.github.hate9.catlauncher.data.Category
import io.github.hate9.catlauncher.data.FlatEntry
import io.github.hate9.catlauncher.data.Item
import io.github.hate9.catlauncher.data.ItemRef
import io.github.hate9.catlauncher.data.LauncherConfig
import io.github.hate9.catlauncher.data.WidgetEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One row of the flat list the LazyColumn renders. Keys are stable across reorders. */
sealed interface Row {
    val key: String

    data class Widget(val entry: WidgetEntry) : Row {
        override val key get() = "w:${entry.appWidgetId}"
    }

    data class Header(val category: Category, val first: Boolean) : Row {
        override val key get() = "h:${category.id}"
    }

    data class Entry(val item: Item, val categoryId: String, val label: String, val icon: ImageBitmap?) : Row {
        override val key get() = "i:${item.id}"
    }

    data object Footer : Row {
        override val key get() = "footer"
    }
}

data class ResolvedEntry(val label: String, val icon: ImageBitmap?)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    val repository = LauncherApp.repository(app)
    private val apps = repository.apps
    val config: StateFlow<LauncherConfig> = repository.config

    private val iconPx = (ICON_DP * app.resources.displayMetrics.density).toInt()
    private val resolved = MutableStateFlow<Map<String, ResolvedEntry>>(emptyMap())
    private var resolveJob: Job? = null

    private val queryFlow = MutableStateFlow("")
    val query: StateFlow<String> = queryFlow

    /** While a drag is in progress the list shows this working copy instead of [rows]. */
    var dragRows by mutableStateOf<List<Row>?>(null)
        private set

    val rows: StateFlow<List<Row>> = combine(config, resolved, queryFlow) { cfg, res, q -> buildRows(cfg, res, q) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = packagesChanged("removed", packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = packagesChanged("added", packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = packagesChanged("changed", packageName)
        override fun onPackagesAvailable(p: Array<out String>, user: UserHandle, replacing: Boolean) = packagesChanged("available", *p)
        override fun onPackagesUnavailable(p: Array<out String>, user: UserHandle, replacing: Boolean) = packagesChanged("unavailable", *p)
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) = packagesChanged("shortcuts", packageName)
    }

    private var resyncJob: Job? = null

    /** Drops cached labels/icons for just the affected packages and resyncs after a short debounce. */
    private fun packagesChanged(why: String, vararg packages: String) {
        Log.d(TAG, "packages $why: ${packages.joinToString()}")
        val affected = packages.toSet()
        resolved.update { map -> map.filterKeys { id -> affected.none { pkg -> id.contains(":$pkg/") } } }
        resyncJob?.cancel()
        resyncJob = viewModelScope.launch {
            delay(400)
            resync(invalidate = false)
        }
    }

    /** Key sequence of a committed drop; the working copy stays on screen until [rows] shows it. */
    private var pendingCommitKeys: List<String>? = null

    init {
        apps.launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        resync(invalidate = false)
        viewModelScope.launch { config.collect { resolveMissing(it) } }
        viewModelScope.launch {
            rows.collect { r ->
                val pending = pendingCommitKeys ?: return@collect
                if (r.map { it.key } == pending) {
                    pendingCommitKeys = null
                    dragRows = null
                }
            }
        }
    }

    override fun onCleared() {
        apps.launcherApps.unregisterCallback(callback)
    }

    /** Re-reads the installed apps. Called on start and whenever the system reports a package change. */
    fun resync(invalidate: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.syncInstalledApps()
            if (invalidate) resolved.value = emptyMap()
            resolveMissing(config.value, force = invalidate)
        }
    }

    private fun resolveMissing(cfg: LauncherConfig, force: Boolean = false) {
        val wantIcons = cfg.showIcons
        val missing = cfg.allItems().filter { item ->
            force || resolved.value[item.id].let { it == null || (wantIcons && it.icon == null) }
        }.toList()
        if (missing.isEmpty()) return
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch(Dispatchers.Default) {
            val fresh = HashMap<String, ResolvedEntry>()
            for (item in missing) {
                val r = apps.resolve(item, iconPx, wantIcons)
                fresh[item.id] = ResolvedEntry(r.label, r.icon?.asImageBitmap())
                if (fresh.size % 16 == 0) resolved.update { it + fresh }
            }
            resolved.update { it + fresh }
        }
    }

    private fun buildRows(cfg: LauncherConfig, res: Map<String, ResolvedEntry>, query: String): List<Row> {
        val q = query.trim()
        val out = ArrayList<Row>()
        if (q.isEmpty()) cfg.widgets.forEach { out += Row.Widget(it) }
        var first = true
        for (category in cfg.categories) {
            val entries = category.items.mapNotNull { item ->
                val r = res[item.id]
                val label = r?.label ?: item.label ?: item.ref.packageName
                if (q.isNotEmpty() && !label.contains(q, ignoreCase = true)) null
                else Row.Entry(item, category.id, label, r?.icon)
            }
            if (q.isNotEmpty() && entries.isEmpty()) continue
            out += Row.Header(category, first)
            first = false
            out += entries
        }
        if (q.isEmpty()) out += Row.Footer
        return out
    }

    fun setQuery(value: String) {
        queryFlow.value = value
    }

    // ---- drag ---------------------------------------------------------------------------------

    fun beginDrag() {
        dragRows = rows.value
    }

    /**
     * Moves the row at [from] to [to] in the working copy. [key] must still be at [from]: the
     * list layout lags a frame behind the copy, so a stale request is ignored rather than
     * swapping the rows back.
     */
    fun moveRow(key: String, from: Int, to: Int): Boolean {
        val list = dragRows?.toMutableList() ?: return false
        if (from !in list.indices || to !in list.indices || from == to) return false
        if (list[from].key != key) return false
        val firstHeader = list.indexOfFirst { it is Row.Header }
        if (to <= firstHeader) return false
        if (list[to] !is Row.Entry && list[to] !is Row.Header) return false
        list.add(to, list.removeAt(from))
        dragRows = list
        return true
    }

    fun endDrag(commit: Boolean) {
        val list = dragRows ?: return
        if (!commit) {
            dragRows = null
            return
        }
        pendingCommitKeys = list.map { it.key }
        repository.applyFlatOrder(list.mapNotNull {
            when (it) {
                is Row.Header -> FlatEntry.CategoryStart(it.category.id)
                is Row.Entry -> FlatEntry.ItemEntry(it.item.id)
                else -> null
            }
        })
        viewModelScope.launch {
            // Safety net: if the rebuilt rows never match (item vanished mid-drag), stop showing the copy.
            delay(500)
            if (pendingCommitKeys != null) {
                pendingCommitKeys = null
                dragRows = null
            }
        }
    }

    // ---- actions ------------------------------------------------------------------------------

    fun launch(item: Item) {
        if (apps.launch(item.ref)) queryFlow.value = ""
    }

    fun launchShortcut(info: ShortcutInfo, userSerial: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            apps.launch(ItemRef.Shortcut(info.`package`, info.id, userSerial))
        }
    }

    /** Adds one of an app's own shortcuts to the list, pinned so it survives the app dropping it. */
    fun addShortcut(info: ShortcutInfo, userSerial: Long, categoryId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val ref = ItemRef.Shortcut(info.`package`, info.id, userSerial)
        val pinned = config.value.allItems().map { it.ref }.filterIsInstance<ItemRef.Shortcut>()
            .filter { it.packageName == ref.packageName && it.userSerial == ref.userSerial }.map { it.shortcutId }.toList()
        apps.pinShortcut(ref, pinned)
        repository.addItem(ref, (info.shortLabel ?: info.longLabel)?.toString(), categoryId)
    }

    suspend fun shortcutsFor(ref: ItemRef): List<ShortcutInfo> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || ref is ItemRef.IntentShortcut) emptyList()
        else apps.appShortcuts(ref.packageName, ref.userSerial)
    }

    fun shortcutIcon(info: ShortcutInfo): ImageBitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
            apps.shortcutIcon(info)?.toBitmap(iconPx, iconPx)?.asImageBitmap()
        else null

    companion object {
        private const val TAG = "LauncherViewModel"
        const val ICON_DP = 28
    }
}
