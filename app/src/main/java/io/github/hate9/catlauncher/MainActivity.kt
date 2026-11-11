/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.hate9.catlauncher.ui.LauncherScreen
import io.github.hate9.catlauncher.ui.LauncherViewModel
import io.github.hate9.catlauncher.ui.theme.CatLauncherTheme
import io.github.hate9.catlauncher.widgets.WidgetController

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()
    private lateinit var widgets: WidgetController
    private var settingsRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgets = WidgetController(this, viewModel.repository) { settingsRequests++ }
        if (intent?.action == Intent.ACTION_APPLICATION_PREFERENCES) settingsRequests++
        setContent {
            CatLauncherTheme {
                LauncherScreen(viewModel, widgets, settingsRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_APPLICATION_PREFERENCES) settingsRequests++
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resync(invalidate = false)
    }

    override fun onStop() {
        widgets.stopListening()
        super.onStop()
    }

    @Deprecated("AppWidgetHost.startAppWidgetConfigureActivityForResult only reports through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgets.onActivityResult(requestCode, resultCode, data)) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
