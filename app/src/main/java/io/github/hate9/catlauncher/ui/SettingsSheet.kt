/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.hate9.catlauncher.data.LauncherConfig
import io.github.hate9.catlauncher.data.SNAP_BOTH
import io.github.hate9.catlauncher.data.SNAP_DOWN
import io.github.hate9.catlauncher.data.SNAP_OFF
import androidx.compose.material3.RadioButton
import io.github.hate9.catlauncher.data.LauncherRepository
import io.github.hate9.catlauncher.widgets.WidgetController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    config: LauncherConfig,
    repository: LauncherRepository,
    widgets: WidgetController?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var pickingWidget by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var choosingPack by remember { mutableStateOf(false) }
    var choosingSnap by remember { mutableStateOf(false) }
    val packs = remember { repository.apps.iconPacks.installedPacks() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            SettingRow("Show icons", onClick = { repository.setShowIcons(!config.showIcons) }) {
                Switch(checked = config.showIcons, onCheckedChange = { repository.setShowIcons(it) })
            }
            SettingRow("Show hidden", subtitle = "List hidden apps in a block at the bottom", onClick = { repository.setShowHidden(!config.showHidden) }) {
                Switch(checked = config.showHidden, onCheckedChange = { repository.setShowHidden(it) })
            }
            SettingRow("Snap to headers", subtitle = snapLabel(config.snapMode), onClick = { choosingSnap = true }) {}
            SettingRow(
                "Icon pack",
                subtitle = packs.firstOrNull { it.packageName == config.iconPack }?.label ?: "System icons",
                onClick = { choosingPack = true },
            ) {}
            HorizontalDivider()

            Text("Widgets", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            config.widgets.forEach { w ->
                val label = widgets?.providerInfo(w.appWidgetId)?.loadLabel(context.packageManager) ?: w.provider.substringAfterLast('/')
                SettingRow(label, subtitle = "${w.heightDp} dp tall") {
                    TextButton(onClick = { repository.addWidget(w.copy(heightDp = (w.heightDp - 40).coerceAtLeast(40))) }) { Text("−") }
                    TextButton(onClick = { repository.addWidget(w.copy(heightDp = (w.heightDp + 40).coerceAtMost(800))) }) { Text("+") }
                    TextButton(onClick = { widgets?.remove(w.appWidgetId) ?: repository.removeWidget(w.appWidgetId) }) { Text("Remove") }
                }
            }
            ActionRow("Add widget…", enabled = widgets != null) { pickingWidget = true }
            HorizontalDivider()

            ActionRow("Change wallpaper") {
                runCatching {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
                }.onFailure { Toast.makeText(context, "No wallpaper picker found", Toast.LENGTH_SHORT).show() }
            }
            ActionRow("Default launcher settings") {
                runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                    .onFailure { Toast.makeText(context, "Not available on this device", Toast.LENGTH_SHORT).show() }
            }
            HorizontalDivider()

            ActionRow("Export config to clipboard") {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("catLauncher config", repository.exportText()))
                Toast.makeText(context, "Config copied", Toast.LENGTH_SHORT).show()
            }
            ActionRow("Share config") {
                val send = Intent(Intent.ACTION_SEND).setType("application/json")
                    .putExtra(Intent.EXTRA_TEXT, repository.exportText())
                    .putExtra(Intent.EXTRA_SUBJECT, "catLauncher config")
                runCatching { context.startActivity(Intent.createChooser(send, "Share config")) }
            }
            ActionRow("Import config…") { importing = true }
        }
    }

    if (importing) {
        TextInputDialog(
            title = "Paste config JSON",
            confirmLabel = "Import",
            singleLine = false,
            monospace = true,
            onDismiss = { importing = false },
            onConfirm = { text ->
                runCatching { repository.importText(text) }
                    .onSuccess { Toast.makeText(context, "Config imported", Toast.LENGTH_SHORT).show(); importing = false }
                    .onFailure { Toast.makeText(context, "Invalid config: ${it.message}", Toast.LENGTH_LONG).show() }
            },
        )
    }

    if (choosingSnap) {
        AlertDialog(
            onDismissRequest = { choosingSnap = false },
            title = { Text("Snap to headers") },
            text = {
                Column {
                    listOf(SNAP_DOWN, SNAP_BOTH, SNAP_OFF).forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { repository.setSnapMode(mode); choosingSnap = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = config.snapMode == mode, onClick = null)
                            Text(snapLabel(mode), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingSnap = false }) { Text("Cancel") } },
        )
    }

    if (choosingPack) {
        IconPackChooserDialog(packs, config.iconPack, onDismiss = { choosingPack = false }) {
            repository.setIconPack(it); choosingPack = false
        }
    }

    if (pickingWidget && widgets != null) {
        WidgetPickerDialog(widgets, onDismiss = { pickingWidget = false }) { provider ->
            pickingWidget = false
            onDismiss()
            widgets.beginAdd(provider)
        }
    }
}

private fun snapLabel(mode: String) = when (mode) {
    SNAP_BOTH -> "Both directions"
    SNAP_OFF -> "Off"
    else -> "Only when scrolling down"
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        trailing()
    }
}

@Composable
private fun ActionRow(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.bodyLarge,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

@Composable
private fun WidgetPickerDialog(widgets: WidgetController, onDismiss: () -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val providers = remember { widgets.installedProviders() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add widget") },
        text = {
            if (providers.isEmpty()) Text("No widgets installed.")
            else LazyColumn(Modifier.height(420.dp)) {
                items(providers, key = { it.provider.flattenToString() + it.profile.hashCode() }) { p ->
                    val pm = context.packageManager
                    val label = remember(p) { p.loadLabel(pm) }
                    val appLabel = remember(p) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p.provider.packageName, 0)).toString() }.getOrDefault(p.provider.packageName) }
                    val preview = remember(p) {
                        runCatching { (p.loadPreviewImage(context, 0) ?: p.loadIcon(context, 0))?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(p) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (preview != null) Image(preview, contentDescription = null, modifier = Modifier.size(48.dp))
                        else Spacer(Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            Text(appLabel, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
