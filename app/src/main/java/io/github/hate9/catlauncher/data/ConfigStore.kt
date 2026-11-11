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
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File

/** JSON on disk. The same text is what import/export shows the user. */
class ConfigStore(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)

    fun load(): LauncherConfig? {
        if (!file.exists()) return null
        return try {
            decode(file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Config unreadable, starting fresh", e)
            null
        }
    }

    fun save(config: LauncherConfig) {
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(encode(config))
        if (!tmp.renameTo(file)) {
            file.writeText(encode(config))
            tmp.delete()
        }
    }

    companion object {
        private const val TAG = "ConfigStore"
        private const val FILE_NAME = "launcher.json"

        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(config: LauncherConfig): String = json.encodeToString(LauncherConfig.serializer(), config)

        /** Throws on malformed input; callers surface the message to the user. */
        fun decode(text: String): LauncherConfig =
            json.decodeFromString(LauncherConfig.serializer(), text).normalized()
    }
}
