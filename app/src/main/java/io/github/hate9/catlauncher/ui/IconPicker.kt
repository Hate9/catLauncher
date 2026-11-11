/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import io.github.hate9.catlauncher.apps.IconPackManager
import io.github.hate9.catlauncher.data.IconRef
import io.github.hate9.catlauncher.data.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks a drawable from any installed icon pack for one item. The initial pack is the item's
 * current override, else the global pack, else the first installed.
 */
@Composable
fun IconPickerDialog(
    vm: LauncherViewModel,
    item: Item,
    label: String,
    globalPack: String?,
    onDismiss: () -> Unit,
) {
    val packs = remember { vm.repository.apps.iconPacks.installedPacks() }
    var selectedPack by remember { mutableStateOf(item.icon?.pack ?: globalPack ?: packs.firstOrNull()?.packageName) }
    var filter by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf<IconPackManager.Loaded?>(null) }
    LaunchedEffect(selectedPack) {
        loaded = null
        loaded = selectedPack?.let { pkg -> withContext(Dispatchers.IO) { vm.repository.apps.iconPacks.load(pkg) } }
    }
    val names = remember(loaded, filter) {
        val all = loaded?.drawables ?: emptyList()
        if (filter.isBlank()) all else all.filter { it.contains(filter.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Icon for $label") },
        text = {
            Column {
                if (packs.isEmpty()) {
                    Text("No icon packs installed. Packs in the ADW/Nova format show up here.")
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        packs.forEach { p ->
                            FilterChip(
                                selected = p.packageName == selectedPack,
                                onClick = { selectedPack = p.packageName },
                                label = { Text(p.label) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        singleLine = true,
                        placeholder = { Text("Filter by name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                    val pack = selectedPack
                    if (loaded == null) Text("Loading…", modifier = Modifier.padding(8.dp))
                    else if (names.isEmpty()) Text("No icons match.", modifier = Modifier.padding(8.dp))
                    else if (pack != null) LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.height(320.dp),
                    ) {
                        items(names, key = { it }) { name ->
                            val bitmap by produceState<ImageBitmap?>(null, pack, name) { value = vm.packBitmap(pack, name) }
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .clickable {
                                        vm.repository.setItemIcon(item.id, IconRef(pack, name))
                                        onDismiss()
                                    }
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                bitmap?.let { Image(it, contentDescription = name, modifier = Modifier.fillMaxWidth()) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (item.icon != null) TextButton(onClick = { vm.repository.setItemIcon(item.id, null); onDismiss() }) { Text("Reset") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun IconPackChooserDialog(packs: List<IconPackManager.Pack>, current: String?, onDismiss: () -> Unit, onChoose: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Icon pack") },
        text = {
            Column {
                PackRow("System icons", current == null) { onChoose(null) }
                packs.forEach { p -> PackRow(p.label, p.packageName == current) { onChoose(p.packageName) } }
                if (packs.isEmpty()) Text("No icon packs installed.", modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
