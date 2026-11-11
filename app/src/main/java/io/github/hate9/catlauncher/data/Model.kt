/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

const val UNCATEGORIZED_ID = "uncategorized"
const val UNCATEGORIZED_NAME = "Uncategorized"

/** Virtual category shown first, listing every item with a [Item.favoritedAt]; not stored as a category. */
const val FAVORITES_ID = "favorites"
const val FAVORITES_NAME = "Favorites"

/** Virtual category shown last, and only with [LauncherConfig.showHidden], listing items with a [Item.hiddenAt]. */
const val HIDDEN_ID = "hidden"
const val HIDDEN_NAME = "Hidden"

const val SNAP_BOTH = "both"
const val SNAP_DOWN = "down"
const val SNAP_OFF = "off"

/** What a list row points at. The `id` is stable across launches and across profiles. */
@Serializable
sealed class ItemRef {
    abstract val packageName: String
    abstract val userSerial: Long
    abstract val id: String

    /** A launcher activity: package + user, plus the class so multi-activity packages do not collapse. */
    @Serializable
    @SerialName("app")
    data class App(
        override val packageName: String,
        val className: String,
        override val userSerial: Long,
    ) : ItemRef() {
        override val id: String get() = "app:$userSerial:$packageName/$className"
    }

    /** A LauncherApps shortcut (dynamic, manifest, or pinned through a PinItemRequest). */
    @Serializable
    @SerialName("shortcut")
    data class Shortcut(
        override val packageName: String,
        val shortcutId: String,
        override val userSerial: Long,
    ) : ItemRef() {
        override val id: String get() = "sc:$userSerial:$packageName/$shortcutId"
    }

    /** A legacy INSTALL_SHORTCUT broadcast: an arbitrary intent. */
    @Serializable
    @SerialName("intent")
    data class IntentShortcut(
        val intentUri: String,
        override val packageName: String = "",
        override val userSerial: Long = 0,
    ) : ItemRef() {
        override val id: String get() = "intent:" + intentUri.hashCode().toUInt().toString(16)
    }
}

/** A drawable inside an icon pack, chosen by the user for one item. */
@Serializable
data class IconRef(val pack: String, val drawable: String)

@Serializable
data class Item(
    val ref: ItemRef,
    /** Epoch millis of when the item entered its current category. */
    val addedAt: Long,
    /** Cached label, used for shortcuts when the system will not resolve them (e.g. not default launcher). */
    val label: String? = null,
    /** Per-item icon override; null means the global pack (or the app's own icon). */
    val icon: IconRef? = null,
    /** When the item was starred; null means not a favorite. Favorites order by this, newest last. */
    val favoritedAt: Long? = null,
    /** When the item was hidden; null means visible. Hidden items leave their category's rows. */
    val hiddenAt: Long? = null,
    /** User-chosen display name; null shows the app's own label. */
    val customLabel: String? = null,
) {
    val id: String get() = ref.id
}

@Serializable
data class Category(
    val id: String,
    val name: String,
    /** False: `items` is in addedAt order (newest last). True: the user has arranged it by hand. */
    val manual: Boolean = false,
    val items: List<Item> = emptyList(),
) {
    val isUncategorized: Boolean get() = id == UNCATEGORIZED_ID

    /** The order this category would have if it were automatic. */
    fun autoOrdered(): List<Item> = items.sortedBy { it.addedAt }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
        fun uncategorized(items: List<Item> = emptyList()) =
            Category(UNCATEGORIZED_ID, UNCATEGORIZED_NAME, manual = false, items = items)
    }
}

@Serializable
data class WidgetEntry(
    val appWidgetId: Int,
    /** Flattened ComponentName of the provider, for display and for re-validation. */
    val provider: String,
    val heightDp: Int,
)

@Serializable
data class LauncherConfig(
    val version: Int = 1,
    /** Always ends with the Uncategorized category; see [normalized]. */
    val categories: List<Category> = listOf(Category.uncategorized()),
    val widgets: List<WidgetEntry> = emptyList(),
    val showIcons: Boolean = true,
    /** Package name of the icon pack applied to every app, or null for system icons. */
    val iconPack: String? = null,
    /** Reveal hidden items in a Hidden block at the bottom. */
    val showHidden: Boolean = false,
    /** Header snapping: [SNAP_BOTH], [SNAP_DOWN] (only when flinging towards the end) or [SNAP_OFF]. */
    val snapMode: String = SNAP_DOWN,
    /** Pan the wallpaper vertically with the list (live wallpapers, or static ones taller than the screen). */
    val scrollWallpaper: Boolean = true,
    /** Return the list to the top whenever the launcher leaves the foreground (app launch, lock). */
    val scrollToTopOnLeave: Boolean = true,
) {
    val uncategorized: Category get() = categories.last { it.isUncategorized }

    fun category(id: String): Category? = categories.firstOrNull { it.id == id }

    fun findItem(itemId: String): Pair<Category, Item>? {
        for (c in categories) for (i in c.items) if (i.id == itemId) return c to i
        return null
    }

    fun allItems(): Sequence<Item> = categories.asSequence().flatMap { it.items.asSequence() }

    /** Enforces the invariants: exactly one Uncategorized bucket, last; no duplicate items. */
    fun normalized(): LauncherConfig {
        val seen = HashSet<String>()
        val others = categories.filter { !it.isUncategorized }.map { c ->
            c.copy(items = c.items.filter { seen.add(it.id) })
        }
        val unc = categories.filter { it.isUncategorized }
            .fold(Category.uncategorized()) { acc, c -> acc.copy(items = acc.items + c.items) }
            .let { c -> c.copy(items = c.items.filter { seen.add(it.id) }) }
        return copy(categories = others + unc)
    }

    fun mapCategory(id: String, f: (Category) -> Category): LauncherConfig =
        copy(categories = categories.map { if (it.id == id) f(it) else it })
}
