/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.ui

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Width the list reserves on the right so row labels stay clear of the rail. */
val RAIL_RESERVED_WIDTH = 56.dp

/** Gap between the rail and the screen edge. */
val RAIL_END_MARGIN = 8.dp

/** One rail entry: an icon when the category has one, otherwise its initial letter. */
data class RailEntry(val name: String, val icon: ImageVector?) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
}

/**
 * A stack of category names on the right edge. Tap one to jump to it; drag along the stack to
 * scrub. [current] is the index of the category at the top of the viewport.
 */
@Composable
fun CategoryRail(
    entries: List<RailEntry>,
    current: Int,
    textColor: Color,
    textStyle: TextStyle,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val latestSelect by rememberUpdatedState(onSelect)
    // Vertical bounds of each entry in the rail's coordinates, each entry keeping its own slot
    // up to date, so a partial relayout never leaves stale rows behind.
    val bounds = remember(entries.size) { HashMap<Int, Pair<Float, Float>>() }
    var last by remember { mutableStateOf(-1) }

    fun indexAt(y: Float): Int {
        if (bounds.isEmpty()) return -1
        bounds.entries.firstOrNull { (_, b) -> y >= b.first && y < b.second }?.let { return it.key }
        // Between or outside rows: the nearest row centre.
        return bounds.entries.minByOrNull { (_, b) -> kotlin.math.abs((b.first + b.second) / 2f - y) }?.key ?: -1
    }

    Column(
        modifier
            .width(RAIL_RESERVED_WIDTH - 16.dp)
            // No fill: instead knock a rounded hole through whatever the list drew underneath
            // (the parent composites offscreen), so dividers stop at the rail like an opaque
            // background would hide them, but the wallpaper still shows through.
            .drawBehind {
                // Extends past the rail to the screen edge so no divider tail shows on its right.
                val r = CornerRadius(20.dp.toPx())
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            Rect(0f, 0f, size.width + RAIL_END_MARGIN.toPx() + 2f, size.height),
                            topLeft = r, topRight = CornerRadius.Zero, bottomRight = CornerRadius.Zero, bottomLeft = r,
                        )
                    )
                }
                drawPath(path, Color.Black, blendMode = BlendMode.Clear)
            }
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp)
            .pointerInput(entries) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var index = indexAt(down.position.y)
                    if (index >= 0) { latestSelect(index); last = index; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        val next = indexAt(change.position.y)
                        if (next >= 0 && next != index) {
                            index = next; last = next
                            latestSelect(next)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                    last = -1
                }
            },
    ) {
        entries.forEachIndexed { i, entry ->
            val active = i == current || i == last
            val color = if (active) textColor else textColor.copy(alpha = 0.75f)
            val cell = Modifier
                .onGloballyPositioned { c ->
                    val top = c.positionInParent().y
                    bounds[i] = top to top + c.size.height
                }
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 6.dp)
            if (entry.icon != null) {
                Icon(entry.icon, contentDescription = entry.name, tint = color, modifier = cell.size(if (active) 26.dp else 22.dp))
            } else {
                Text(
                    text = entry.initial,
                    color = color,
                    style = textStyle,
                    fontSize = if (active) 17.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = cell.semantics { contentDescription = entry.name },
                )
            }
        }
    }
}
