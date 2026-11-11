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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A menu drawn inside the activity window (not a Popup), so the touch stream that opened it can
 * continue into a drag. [anchor] is in the coordinates of the overlay's parent.
 */
@Composable
fun ContextMenuOverlay(
    anchor: Offset,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.widthIn(min = 200.dp, max = 300.dp),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) { content() }
            }
        },
    ) { measurables, constraints ->
        val scrim = measurables[0].measure(constraints)
        val maxH = (constraints.maxHeight * 0.7f).roundToInt()
        val menu = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = maxH))
        val margin = 8.dp.roundToPx()
        val x = anchor.x.roundToInt().coerceIn(margin, (constraints.maxWidth - menu.width - margin).coerceAtLeast(margin))
        val below = anchor.y.roundToInt() + margin
        val y = if (below + menu.height + margin <= constraints.maxHeight) below
        else (anchor.y.roundToInt() - menu.height - margin).coerceAtLeast(margin)
        layout(constraints.maxWidth, constraints.maxHeight) {
            scrim.place(0, 0)
            menu.place(x, y)
        }
    }
}

@Composable
fun MenuTitle(text: String, icon: ImageBitmap? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Box(Modifier.width(12.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
    HorizontalDivider()
}

@Composable
fun MenuItem(text: String, icon: ImageBitmap? = null, enabled: Boolean = true, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Box(Modifier.width(12.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}
