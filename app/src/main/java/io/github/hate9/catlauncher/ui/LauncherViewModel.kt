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
import io.github.hate9.catlauncher.data.FAVORITES_ID
import io.github.hate9.catlauncher.data.FAVORITES_NAME
import io.github.hate9.catlauncher.data.HIDDEN_ID
import io.github.hate9.catlauncher.data.HIDDEN_NAME
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

    /**
     * [block] is null for a row inside its real category, or FAVORITES_ID / HIDDEN_ID for the copy
     * shown in a virtual block; those reorder only among themselves.
     */
    data class Entry(val item: Item, val categoryId: String, val label: String, val icon: ImageBitmap?, val block: String? = null) : Row {
        val inFavorites get() = block == FAVORITES_ID
        val inHidden get() = block == HIDDEN_ID
        override val key get() = when (block) { FAVORITES_ID -> "f:"; HIDDEN_ID -> "x:"; else -> "i:" } + item.id
    }

    data object Footer : Row {
        override val key get() = "footer"
    }
}

/** [iconKey] records which pack/override produced the icon so a change invalidates it. */
data class ResolvedEntry(val label: String, val icon: ImageBitmap?, val iconKey: String = "")

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

    private fun iconKey(cfg: LauncherConfig, item: Item) = "${cfg.iconPack}|${item.icon?.pack}/${item.icon?.drawable}"

    private fun resolveMissing(cfg: LauncherConfig, force: Boolean = false) {
        val wantIcons = cfg.showIcons
        val missing = cfg.allItems().filter { item ->
            force || resolved.value[item.id].let { it == null || (wantIcons && (it.icon == null || it.iconKey != iconKey(cfg, item))) }
        }.toList()
        if (missing.isEmpty()) return
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch(Dispatchers.Default) {
            val fresh = HashMap<String, ResolvedEntry>()
            for (item in missing) {
                val r = apps.resolve(item, iconPx, wantIcons, cfg.iconPack)
                fresh[item.id] = ResolvedEntry(r.label, r.icon?.asImageBitmap(), iconKey(cfg, item))
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
        fun labelOf(item: Item) = item.customLabel ?: res[item.id]?.label ?: item.label ?: item.ref.packageName
        fun matches(item: Item) = q.isEmpty() || labelOf(item).contains(q, ignoreCase = true)
        fun visible(item: Item) = item.hiddenAt == null

        if (q.isEmpty()) {
            // A starred item stays in Favorites even while hidden from its category.
            val favorites = cfg.allItems().filter { it.favoritedAt != null }.sortedBy { it.favoritedAt }.toList()
            if (favorites.isNotEmpty()) {
                out += Row.Header(Category(FAVORITES_ID, FAVORITES_NAME, items = favorites), first = true)
                first = false
                favorites.forEach { out += Row.Entry(it, FAVORITES_ID, labelOf(it), res[it.id]?.icon, block = FAVORITES_ID) }
            }
        }
        for (category in cfg.categories) {
            val entries = category.items.filter { visible(it) && matches(it) }
                .map { Row.Entry(it, category.id, labelOf(it), res[it.id]?.icon) }
            if (q.isNotEmpty() && entries.isEmpty()) continue
            out += Row.Header(category, first)
            first = false
            out += entries
        }
        // Hidden items always answer a search, in their own block at the end; otherwise only on request.
        if (cfg.showHidden || q.isNotEmpty()) {
            val hidden = cfg.allItems().filter { it.hiddenAt != null && matches(it) }.sortedBy { it.hiddenAt }.toList()
            if (hidden.isNotEmpty()) {
                out += Row.Header(Category(HIDDEN_ID, HIDDEN_NAME, items = hidden), first = false)
                hidden.forEach { out += Row.Entry(it, HIDDEN_ID, labelOf(it), res[it.id]?.icon, block = HIDDEN_ID) }
            }
        }
        if (q.isEmpty()) out += Row.Footer
        return out
    }

    fun setQuery(value: String) {
        queryFlow.value = value
    }

    // ---- drag ---------------------------------------------------------------------------------

    private var dragKey: String? = null

    fun beginDrag(key: String) {
        dragKey = key
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
        val firstReal = list.indexOfFirst { it is Row.Header && it.category.id != FAVORITES_ID && it.category.id != HIDDEN_ID }
        val hiddenHeader = list.indexOfFirst { it is Row.Header && it.category.id == HIDDEN_ID }.let { if (it < 0) list.size else it }
        val dragged = list[from]
        val target = list[to]
        if (dragged is Row.Entry && dragged.block != null) {
            // Virtual blocks reorder among themselves only.
            if (target !is Row.Entry || target.block != dragged.block) return false
        } else {
            // Nothing else may land above the first real category or in the Hidden block.
            if (to <= firstReal || to >= hiddenHeader) return false
            if (target !is Row.Entry && target !is Row.Header) return false
        }
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
        when {
            dragKey?.startsWith("f:") == true ->
                repository.applyFavoritesOrder(list.filterIsInstance<Row.Entry>().filter { it.inFavorites }.map { it.item.id })
            dragKey?.startsWith("x:") == true ->
                repository.applyHiddenOrder(list.filterIsInstance<Row.Entry>().filter { it.inHidden }.map { it.item.id })
            else -> repository.applyFlatOrder(list.mapNotNull {
                when (it) {
                    is Row.Header -> if (it.category.id == FAVORITES_ID || it.category.id == HIDDEN_ID) null else FlatEntry.CategoryStart(it.category.id)
                    is Row.Entry -> if (it.block != null) null else FlatEntry.ItemEntry(it.item.id)
                    else -> null
                }
            })
        }
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

    /** A pack drawable at list size, for the icon picker grid. */
    suspend fun packBitmap(pack: String, drawable: String): ImageBitmap? = withContext(Dispatchers.IO) {
        apps.iconPacks.load(pack)?.let { apps.iconPacks.bitmap(it, drawable, iconPx) }?.asImageBitmap()
    }

    companion object {
        private const val TAG = "LauncherViewModel"
        const val ICON_DP = 28
    }
}
