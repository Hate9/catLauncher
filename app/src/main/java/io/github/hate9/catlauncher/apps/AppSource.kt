/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmap
import io.github.hate9.catlauncher.data.Item
import io.github.hate9.catlauncher.data.ItemRef

/** Everything that talks to LauncherApps / PackageManager, with user-profile handling. */
class AppSource(private val context: Context) {
    val launcherApps: LauncherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager: UserManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val pm = context.packageManager
    val iconPacks = IconPackManager(context)

    data class InstalledApp(val ref: ItemRef.App, val label: String)
    data class Resolved(val label: String, val icon: Bitmap?)

    fun profiles(): List<UserHandle> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) launcherApps.profiles else userManager.userProfiles

    fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)
    fun userFor(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

    fun installedApps(): List<InstalledApp> = profiles().flatMap { user ->
        val serial = serialOf(user)
        try {
            launcherApps.getActivityList(null, user)
        } catch (e: Exception) {
            Log.w(TAG, "getActivityList failed for $user", e); emptyList()
        }.filter { it.componentName.packageName != context.packageName }
            .map { InstalledApp(ItemRef.App(it.componentName.packageName, it.componentName.className, serial), it.label.toString()) }
    }

    // ---- launching ---------------------------------------------------------------------------

    fun launch(ref: ItemRef, sourceBounds: Rect? = null): Boolean = try {
        launchOrThrow(ref, sourceBounds)
    } catch (e: Exception) {
        Log.w(TAG, "launch failed for ${ref.id}", e); false
    }

    private fun launchOrThrow(ref: ItemRef, sourceBounds: Rect?): Boolean {
        return when (ref) {
            is ItemRef.App -> {
                val user = userFor(ref.userSerial) ?: return false
                launcherApps.startMainActivity(ComponentName(ref.packageName, ref.className), user, sourceBounds, null)
                true
            }
            is ItemRef.Shortcut -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return false
                val user = userFor(ref.userSerial) ?: return false
                launcherApps.startShortcut(ref.packageName, ref.shortcutId, sourceBounds, null, user)
                true
            }
            is ItemRef.IntentShortcut -> {
                val intent = Intent.parseUri(ref.intentUri, Intent.URI_INTENT_SCHEME)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }
        }
    }

    fun openAppInfo(ref: ItemRef, sourceBounds: Rect? = null) {
        val user = userFor(ref.userSerial) ?: return
        val component = when (ref) {
            is ItemRef.App -> ComponentName(ref.packageName, ref.className)
            else -> launcherApps.getActivityList(ref.packageName, user).firstOrNull()?.componentName ?: return
        }
        try {
            launcherApps.startAppDetailsActivity(component, user, sourceBounds, null)
        } catch (e: Exception) {
            Log.w(TAG, "app details failed", e)
        }
    }

    // ---- resolving labels and icons ----------------------------------------------------------

    fun resolve(item: Item, iconPx: Int, wantIcon: Boolean, iconPack: String?): Resolved {
        val base = resolveSystem(item, iconPx, wantIcon)
        if (!wantIcon) return base
        item.icon?.let { override ->
            iconPacks.load(override.pack)?.let { pack -> iconPacks.bitmap(pack, override.drawable, iconPx) }?.let { return base.copy(icon = it) }
        }
        val ref = item.ref as? ItemRef.App ?: return base
        val pack = iconPack?.let { iconPacks.load(it) } ?: return base
        return iconPacks.iconFor(pack, ComponentName(ref.packageName, ref.className), iconPx)?.let { base.copy(icon = it) } ?: base
    }

    private fun resolveSystem(item: Item, iconPx: Int, wantIcon: Boolean): Resolved = when (val ref = item.ref) {
        is ItemRef.App -> {
            val user = userFor(ref.userSerial)
            val info = user?.let { runCatching { launcherApps.resolveActivity(mainIntent(ref), it) }.getOrNull() }
            Resolved(
                label = info?.label?.toString() ?: item.label ?: ref.packageName,
                icon = if (wantIcon) info?.let { runCatching { it.getBadgedIcon(0) }.getOrNull() }?.toBitmap(iconPx, iconPx) else null,
            )
        }
        is ItemRef.Shortcut -> {
            var label: String? = null
            var icon: Bitmap? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val info = shortcutInfo(ref)
                if (info != null) {
                    label = shortcutLabel(info)
                    if (wantIcon) icon = shortcutIcon(info)?.toBitmap(iconPx, iconPx)
                }
            }
            if (wantIcon && icon == null) icon = packageIcon(ref.packageName, ref.userSerial, iconPx)
            Resolved(label ?: item.label ?: ref.shortcutId, icon)
        }
        is ItemRef.IntentShortcut -> {
            val intent = runCatching { Intent.parseUri(ref.intentUri, Intent.URI_INTENT_SCHEME) }.getOrNull()
            val resolved = intent?.let { runCatching { pm.resolveActivity(it, 0) }.getOrNull() }
            val icon = if (!wantIcon) null else resolved?.loadIcon(pm)?.toBitmap(iconPx, iconPx)
                ?: ref.packageName.takeIf { it.isNotEmpty() }?.let { packageIcon(it, ref.userSerial, iconPx) }
            Resolved(item.label ?: resolved?.loadLabel(pm)?.toString() ?: "Shortcut", icon)
        }
    }

    private fun mainIntent(ref: ItemRef.App) = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(ref.packageName, ref.className))

    private fun packageIcon(packageName: String, userSerial: Long, iconPx: Int): Bitmap? {
        val user = userFor(userSerial) ?: return null
        val info = runCatching { launcherApps.getActivityList(packageName, user).firstOrNull() }.getOrNull()
        return info?.let { runCatching { it.getBadgedIcon(0) }.getOrNull() }?.toBitmap(iconPx, iconPx)
    }

    // ---- shortcuts ---------------------------------------------------------------------------

    /** True when the system lets us read other apps' shortcuts, i.e. we are the default launcher. */
    val hasShortcutHostPermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun shortcutInfo(ref: ItemRef.Shortcut): ShortcutInfo? {
        val user = userFor(ref.userSerial) ?: return null
        return queryShortcuts(ref.packageName, user, ALL_FLAGS, listOf(ref.shortcutId)).firstOrNull()
    }

    /** The dynamic and manifest shortcuts an app publishes, as shown in the long-press menu. */
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun appShortcuts(packageName: String, userSerial: Long): List<ShortcutInfo> {
        val user = userFor(userSerial) ?: return emptyList()
        val flags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
        return queryShortcuts(packageName, user, flags, null).filter { it.isEnabled }.sortedBy { it.rank }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun queryShortcuts(packageName: String, user: UserHandle, flags: Int, ids: List<String>?): List<ShortcutInfo> {
        if (!hasShortcutHostPermission) return emptyList()
        val query = LauncherApps.ShortcutQuery().setPackage(packageName).setQueryFlags(flags)
        if (ids != null) query.setShortcutIds(ids)
        return try {
            launcherApps.getShortcuts(query, user) ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "getShortcuts failed", e); emptyList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun shortcutLabel(info: ShortcutInfo): String = (info.shortLabel ?: info.longLabel ?: info.id).toString()

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun shortcutIcon(info: ShortcutInfo): Drawable? =
        runCatching { launcherApps.getShortcutBadgedIconDrawable(info, context.resources.displayMetrics.densityDpi) }.getOrNull()

    /**
     * Returns the ids of the given shortcuts that still exist, or null when we cannot tell
     * (no host permission), in which case callers should keep them.
     */
    fun validShortcutIds(refs: List<ItemRef.Shortcut>): Set<String>? {
        if (refs.isEmpty()) return emptySet()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || !hasShortcutHostPermission) return null
        val valid = HashSet<String>()
        refs.groupBy { it.packageName to it.userSerial }.forEach { (key, group) ->
            val user = userFor(key.second) ?: return@forEach
            val ids = group.map { it.shortcutId }
            queryShortcuts(key.first, user, ALL_FLAGS, ids).forEach { info ->
                valid += ItemRef.Shortcut(key.first, info.id, key.second).id
            }
        }
        return valid
    }

    /** Pins [ref] in addition to whatever is already pinned for its package. */
    fun pinShortcut(ref: ItemRef.Shortcut, alreadyPinned: List<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val user = userFor(ref.userSerial) ?: return
        runCatching { launcherApps.pinShortcuts(ref.packageName, (alreadyPinned + ref.shortcutId).distinct(), user) }
            .onFailure { Log.w(TAG, "pinShortcuts failed", it) }
    }

    /** Sets the pinned set for the package to [remaining] (which must not contain [ref]). */
    fun unpinShortcut(ref: ItemRef.Shortcut, remaining: List<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val user = userFor(ref.userSerial) ?: return
        runCatching { launcherApps.pinShortcuts(ref.packageName, remaining.filter { it != ref.shortcutId }, user) }
            .onFailure { Log.w(TAG, "pinShortcuts failed", it) }
    }

    companion object {
        private const val TAG = "AppSource"

        @RequiresApi(Build.VERSION_CODES.N_MR1)
        private const val ALL_FLAGS = LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
    }
}
