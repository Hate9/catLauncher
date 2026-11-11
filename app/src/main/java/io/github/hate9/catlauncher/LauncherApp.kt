/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher

import android.app.Application
import android.content.Context
import io.github.hate9.catlauncher.data.LauncherRepository

class LauncherApp : Application() {
    val repository: LauncherRepository by lazy { LauncherRepository(this) }

    companion object {
        fun repository(context: Context): LauncherRepository = (context.applicationContext as LauncherApp).repository
    }
}
