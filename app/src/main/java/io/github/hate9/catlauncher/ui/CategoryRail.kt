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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
    // Vertical bounds of each entry, in the rail's own coordinates, for hit-testing a scrub.
    val bounds = remember { mutableStateOf(FloatArray(0)) }
    var last by remember { mutableStateOf(-1) }

    fun indexAt(y: Float): Int {
        val b = bounds.value
        if (b.isEmpty()) return -1
        for (i in 0 until b.size / 2) if (y >= b[i * 2] && y < b[i * 2 + 1]) return i
        return if (y < b[0]) 0 else b.size / 2 - 1
    }

    Column(
        modifier
            .width(RAIL_RESERVED_WIDTH - 16.dp)
            .background(textColor.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
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
        val arr = FloatArray(entries.size * 2)
        entries.forEachIndexed { i, entry ->
            val active = i == current || i == last
            val color = if (active) textColor else textColor.copy(alpha = 0.75f)
            val cell = Modifier
                .onGloballyPositioned { c ->
                    val top = c.positionInParent().y
                    arr[i * 2] = top; arr[i * 2 + 1] = top + c.size.height
                    if (i == entries.lastIndex) bounds.value = arr.copyOf()
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
