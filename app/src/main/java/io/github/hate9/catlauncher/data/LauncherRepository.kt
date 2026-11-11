/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.data

import android.content.Context
import io.github.hate9.catlauncher.apps.AppSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single source of truth for the persisted launcher state. All mutations go through [update],
 * which normalises the config and schedules a debounced write.
 */
class LauncherRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = ConfigStore(appContext)
    val apps = AppSource(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    private val _config = MutableStateFlow(store.load() ?: LauncherConfig())
    val config: StateFlow<LauncherConfig> = _config.asStateFlow()

    fun update(transform: (LauncherConfig) -> LauncherConfig) {
        val before = _config.value
        _config.update { transform(it).normalized() }
        if (_config.value != before) scheduleSave()
    }

    @Synchronized
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(250)
            store.save(_config.value)
        }
    }

    fun exportText(): String = ConfigStore.encode(_config.value)

    /** Replaces the whole config. Apps that no longer exist are pruned by the following sync. */
    fun importText(text: String) {
        val parsed = ConfigStore.decode(text)
        update { parsed }
        syncInstalledApps()
    }

    // ---- items ------------------------------------------------------------------------------

    fun addItem(ref: ItemRef, label: String? = null, categoryId: String = UNCATEGORIZED_ID) = update { cfg ->
        if (cfg.findItem(ref.id) != null) return@update cfg
        val target = cfg.category(categoryId)?.id ?: UNCATEGORIZED_ID
        cfg.mapCategory(target) { c -> c.copy(items = c.items + Item(ref, System.currentTimeMillis(), label)) }
    }

    fun removeItem(itemId: String) {
        val removed = _config.value.findItem(itemId)?.second ?: return
        update { cfg -> cfg.copy(categories = cfg.categories.map { c -> c.copy(items = c.items.filter { it.id != itemId }) }) }
        (removed.ref as? ItemRef.Shortcut)?.let { apps.unpinShortcut(it, remainingPinnedIds(it)) }
    }

    private fun remainingPinnedIds(ref: ItemRef.Shortcut): List<String> =
        _config.value.allItems().map { it.ref }.filterIsInstance<ItemRef.Shortcut>()
            .filter { it.packageName == ref.packageName && it.userSerial == ref.userSerial }
            .map { it.shortcutId }.toList()

    /** Appends the item to the bottom of [categoryId] with a fresh timestamp. */
    fun moveItemToCategory(itemId: String, categoryId: String) = update { cfg ->
        val (from, item) = cfg.findItem(itemId) ?: return@update cfg
        if (from.id == categoryId) return@update cfg
        val moved = item.copy(addedAt = System.currentTimeMillis())
        cfg.copy(categories = cfg.categories.map { c ->
            when (c.id) {
                from.id -> c.copy(items = c.items.filter { it.id != itemId })
                categoryId -> c.copy(items = c.items + moved)
                else -> c
            }
        })
    }

    /**
     * Applies the order the user produced by dragging: a flat sequence of category ids and item ids.
     * Items that changed category get a fresh timestamp; a category becomes manual when its new
     * order differs from its automatic order.
     */
    fun applyFlatOrder(flat: List<FlatEntry>) = update { cfg ->
        val lookup = cfg.allItems().associateBy { it.id }
        val previousCategory = HashMap<String, String>()
        cfg.categories.forEach { c -> c.items.forEach { previousCategory[it.id] = c.id } }
        val now = System.currentTimeMillis()
        val buckets = LinkedHashMap<String, MutableList<Item>>()
        var current: String? = null
        for (entry in flat) {
            when (entry) {
                is FlatEntry.CategoryStart -> current = entry.categoryId.also { buckets.getOrPut(it) { mutableListOf() } }
                is FlatEntry.ItemEntry -> {
                    val item = lookup[entry.itemId] ?: continue
                    val target = current ?: continue
                    val moved = if (previousCategory[entry.itemId] != target) item.copy(addedAt = now) else item
                    buckets.getValue(target).add(moved)
                }
            }
        }
        cfg.copy(categories = cfg.categories.map { c ->
            val items = buckets[c.id] ?: return@map c
            val auto = items.sortedBy { it.addedAt }
            c.copy(items = items, manual = items != auto)
        })
    }

    // ---- categories -------------------------------------------------------------------------

    fun createCategory(name: String, firstItemId: String? = null): String {
        val id = Category.newId()
        update { cfg ->
            val others = cfg.categories.filter { !it.isUncategorized }
            cfg.copy(categories = others + Category(id, name.trim().ifEmpty { "Category" }) + cfg.uncategorized)
        }
        if (firstItemId != null) moveItemToCategory(firstItemId, id)
        return id
    }

    fun renameCategory(categoryId: String, name: String) = update { cfg ->
        if (categoryId == UNCATEGORIZED_ID) cfg else cfg.mapCategory(categoryId) { it.copy(name = name.trim().ifEmpty { it.name }) }
    }

    /** Deleting a category drops its items into Uncategorized. */
    fun deleteCategory(categoryId: String) = update { cfg ->
        val victim = cfg.category(categoryId) ?: return@update cfg
        if (victim.isUncategorized) return@update cfg
        val now = System.currentTimeMillis()
        val orphans = victim.items.map { it.copy(addedAt = now) }
        cfg.copy(categories = cfg.categories.filter { it.id != categoryId }
            .map { c -> if (c.isUncategorized) c.copy(items = c.items + orphans) else c })
    }

    /** Moves a category up (delta < 0) or down (delta > 0). Uncategorized stays last. */
    fun moveCategory(categoryId: String, delta: Int) = update { cfg ->
        val others = cfg.categories.filter { !it.isUncategorized }.toMutableList()
        val index = others.indexOfFirst { it.id == categoryId }
        if (index < 0) return@update cfg
        val target = (index + delta).coerceIn(0, others.lastIndex)
        others.add(target, others.removeAt(index))
        cfg.copy(categories = others + cfg.uncategorized)
    }

    fun resetCategoryOrder(categoryId: String) = update { cfg ->
        cfg.mapCategory(categoryId) { it.copy(items = it.autoOrdered(), manual = false) }
    }

    // ---- widgets / settings -----------------------------------------------------------------

    fun addWidget(entry: WidgetEntry) = update { cfg -> cfg.copy(widgets = cfg.widgets.filter { it.appWidgetId != entry.appWidgetId } + entry) }
    fun removeWidget(appWidgetId: Int) = update { cfg -> cfg.copy(widgets = cfg.widgets.filter { it.appWidgetId != appWidgetId }) }
    fun setShowIcons(show: Boolean) = update { it.copy(showIcons = show) }
    fun setIconPack(packageName: String?) = update { it.copy(iconPack = packageName) }
    fun setFavorite(itemId: String, favorite: Boolean) = update { cfg ->
        val stamp = if (favorite) System.currentTimeMillis() else null
        cfg.copy(categories = cfg.categories.map { c -> c.copy(items = c.items.map { if (it.id == itemId) it.copy(favoritedAt = stamp) else it }) })
    }
    fun setHidden(itemId: String, hidden: Boolean) = update { cfg ->
        val stamp = if (hidden) System.currentTimeMillis() else null
        cfg.copy(categories = cfg.categories.map { c -> c.copy(items = c.items.map { if (it.id == itemId) it.copy(hiddenAt = stamp) else it }) })
    }
    fun setShowHidden(show: Boolean) = update { it.copy(showHidden = show) }
    fun setItemLabel(itemId: String, label: String?) = update { cfg ->
        val clean = label?.trim()?.takeIf { it.isNotEmpty() }
        cfg.copy(categories = cfg.categories.map { c -> c.copy(items = c.items.map { if (it.id == itemId) it.copy(customLabel = clean) else it }) })
    }
    fun setSnapMode(mode: String) = update { it.copy(snapMode = mode) }
    fun setScrollWallpaper(on: Boolean) = update { it.copy(scrollWallpaper = on) }

    /** Rewrites favoritedAt stamps so the favorites block shows [orderedIds] in that order. */
    fun applyFavoritesOrder(orderedIds: List<String>) =
        restamp(orderedIds, { it.favoritedAt }) { item, t -> item.copy(favoritedAt = t) }

    fun applyHiddenOrder(orderedIds: List<String>) =
        restamp(orderedIds, { it.hiddenAt }) { item, t -> item.copy(hiddenAt = t) }

    private fun restamp(orderedIds: List<String>, read: (Item) -> Long?, write: (Item, Long) -> Item) = update { cfg ->
        val base = cfg.allItems().mapNotNull(read).minOrNull() ?: System.currentTimeMillis()
        val stamp = orderedIds.withIndex().associate { (i, id) -> id to base + i }
        cfg.copy(categories = cfg.categories.map { c ->
            c.copy(items = c.items.map { item -> stamp[item.id]?.let { write(item, it) } ?: item })
        })
    }

    fun setItemIcon(itemId: String, icon: IconRef?) = update { cfg ->
        cfg.copy(categories = cfg.categories.map { c -> c.copy(items = c.items.map { if (it.id == itemId) it.copy(icon = icon) else it }) })
    }

    // ---- sync with the system ---------------------------------------------------------------

    /**
     * Reconciles the config with what is installed: new launcher activities land in
     * Uncategorized, vanished apps and shortcuts are dropped. Safe to call often.
     */
    fun syncInstalledApps() {
        val installed = apps.installedApps()
        val installedIds = installed.mapTo(HashSet()) { it.ref.id }
        val knownUsers = installed.mapTo(HashSet()) { it.ref.userSerial }
        val validShortcuts = apps.validShortcutIds(_config.value.allItems().map { it.ref }.filterIsInstance<ItemRef.Shortcut>().toList())
        update { cfg ->
            val present = cfg.allItems().mapTo(HashSet()) { it.id }
            val now = System.currentTimeMillis()
            val fresh = installed.filter { it.ref.id !in present }.sortedBy { it.label.lowercase() }
                .map { Item(it.ref, now, it.label) }
            cfg.copy(categories = cfg.categories.map { c ->
                val kept = c.items.filter { item ->
                    when (val ref = item.ref) {
                        is ItemRef.App -> ref.id in installedIds
                        is ItemRef.Shortcut -> validShortcuts?.let { ref.id in it } ?: (ref.userSerial in knownUsers)
                        is ItemRef.IntentShortcut -> true
                    }
                }
                if (c.isUncategorized) c.copy(items = kept + fresh) else c.copy(items = kept)
            })
        }
    }
}

sealed interface FlatEntry {
    data class CategoryStart(val categoryId: String) : FlatEntry
    data class ItemEntry(val itemId: String) : FlatEntry
}
