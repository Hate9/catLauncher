/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.hate9.catlauncher.LauncherApp
import io.github.hate9.catlauncher.data.ItemRef
import io.github.hate9.catlauncher.data.UNCATEGORIZED_NAME
import io.github.hate9.catlauncher.ui.theme.CatLauncherTheme
import io.github.hate9.catlauncher.widgets.WidgetController

/**
 * Handles ACTION_CONFIRM_PIN_SHORTCUT / ACTION_CONFIRM_PIN_APPWIDGET (Android 8+). The system only
 * sends these to the default launcher. Accepted shortcuts land in Uncategorized; accepted widgets
 * go through the same configure flow as widgets added from settings.
 */
class PinShortcutActivity : ComponentActivity() {
    private var widgets: WidgetController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            finish(); return
        }
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull()
        if (request == null || !request.isValid) {
            finish(); return
        }
        when (request.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> confirmShortcut(request)
            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> confirmWidget(request)
            else -> finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun confirmShortcut(request: LauncherApps.PinItemRequest) {
        val info = request.shortcutInfo ?: run { finish(); return }
        val repo = LauncherApp.repository(this)
        val label = (info.shortLabel ?: info.longLabel ?: info.id).toString()
        val iconPx = (48 * resources.displayMetrics.density).toInt()
        val icon = repo.apps.shortcutIcon(info)?.toBitmap(iconPx, iconPx)?.asImageBitmap()
        setContent {
            CatLauncherTheme {
                val bitmap = remember { icon }
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Add shortcut?") },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (bitmap != null) {
                                Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.width(12.dp))
                            }
                            Text("\"$label\" will be added to $UNCATEGORIZED_NAME.")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val accepted = runCatching { request.accept() }.getOrDefault(false)
                            if (accepted) {
                                val ref = ItemRef.Shortcut(info.`package`, info.id, repo.apps.serialOf(info.userHandle))
                                repo.addItem(ref, label)
                                Toast.makeText(this@PinShortcutActivity, "Added \"$label\"", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@PinShortcutActivity, "The app withdrew the shortcut", Toast.LENGTH_SHORT).show()
                            }
                            finish()
                        }) { Text("Add") }
                    },
                    dismissButton = { TextButton(onClick = { finish() }) { Text("Cancel") } },
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun confirmWidget(request: LauncherApps.PinItemRequest) {
        val provider = request.getAppWidgetProviderInfo(this) ?: run { finish(); return }
        val repo = LauncherApp.repository(this)
        val controller = WidgetController(this, repo) {}.also { widgets = it }
        val label = provider.loadLabel(packageManager)
        setContent {
            CatLauncherTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Add widget?") },
                    text = { Text("\"$label\" will be added to the top of the launcher.") },
                    confirmButton = {
                        TextButton(onClick = {
                            if (!controller.acceptPinRequest(request, provider)) {
                                Toast.makeText(this@PinShortcutActivity, "Could not add widget", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            // Otherwise the configure activity (if any) reports back via onActivityResult.
                        }) { Text("Add") }
                    },
                    dismissButton = { TextButton(onClick = { finish() }) { Text("Cancel") } },
                )
            }
        }
    }

    @Deprecated("Widget configure results arrive here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (widgets?.onActivityResult(requestCode, resultCode, data) == true) {
            finish()
        } else {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
