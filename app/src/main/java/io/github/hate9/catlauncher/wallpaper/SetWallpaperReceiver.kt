/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Automation hook. From Tasker, use Send Intent with
 *   Action: io.github.hate9.catlauncher.SET_WALLPAPER
 *   Extra:  path:/storage/emulated/0/Pictures/whatever.jpg
 *   Package: io.github.hate9.catlauncher   Target: Broadcast Receiver
 * or from a shell:
 *   am broadcast -a io.github.hate9.catlauncher.SET_WALLPAPER --es path /sdcard/x.jpg -n io.github.hate9.catlauncher/.wallpaper.SetWallpaperReceiver
 * A content:// URI works in place of a path. An empty path returns to the plain colour.
 */
class SetWallpaperReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val path = intent.getStringExtra(EXTRA_PATH)?.trim()
            ?: intent.getStringExtra(Intent.EXTRA_STREAM)?.trim()
            ?: intent.data?.toString()
        Log.i(TAG, "wallpaper source -> ${path ?: "(cleared)"}")
        WallpaperPrefs(context).source = path?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val TAG = "SetWallpaperReceiver"
        const val ACTION = "io.github.hate9.catlauncher.SET_WALLPAPER"
        const val EXTRA_PATH = "path"
    }
}
