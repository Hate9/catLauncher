/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.shortcuts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.IntentCompat
import io.github.hate9.catlauncher.LauncherApp
import io.github.hate9.catlauncher.data.ItemRef

/**
 * Legacy com.android.launcher.action.INSTALL_SHORTCUT broadcast (pre-Oreo style). The shortcut is
 * an arbitrary intent plus a name; it is stored as an [ItemRef.IntentShortcut] in Uncategorized.
 */
class InstallShortcutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val target = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
        val name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)?.trim()
        if (target == null || name.isNullOrEmpty()) {
            Log.w(TAG, "Ignoring INSTALL_SHORTCUT without intent or name")
            return
        }
        val packageName = target.`package` ?: target.component?.packageName ?: ""
        val ref = ItemRef.IntentShortcut(target.toUri(Intent.URI_INTENT_SCHEME), packageName)
        val repo = LauncherApp.repository(context)
        if (repo.config.value.findItem(ref.id) != null) {
            if (!intent.getBooleanExtra(EXTRA_DUPLICATE, true)) return
        }
        repo.addItem(ref, name)
        Toast.makeText(context, "Added \"$name\"", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "InstallShortcut"
        const val ACTION = "com.android.launcher.action.INSTALL_SHORTCUT"
        private const val EXTRA_DUPLICATE = "duplicate"
    }
}
