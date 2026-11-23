/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Grid of [CategoryIcons] with a name filter; picks one for a category or clears it. */
@Composable
fun CategoryIconDialog(categoryName: String, current: String?, onDismiss: () -> Unit, onChoose: (String?) -> Unit) {
    var filter by remember { mutableStateOf("") }
    val icons = remember(filter) {
        val q = filter.trim()
        if (q.isEmpty()) CategoryIcons.all else CategoryIcons.all.filter { it.first.contains(q, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Icon for $categoryName") },
        text = {
            Column {
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it }, singleLine = true,
                    placeholder = { Text("Filter by name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
                if (icons.isEmpty()) Text("No icons match.", modifier = Modifier.padding(8.dp))
                else LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.height(320.dp)) {
                    items(icons, key = { it.first }) { (name, vector) ->
                        val selected = name == current
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .padding(3.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { onChoose(name) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(vector, contentDescription = name, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { if (current != null) TextButton(onClick = { onChoose(null) }) { Text("Remove icon") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
