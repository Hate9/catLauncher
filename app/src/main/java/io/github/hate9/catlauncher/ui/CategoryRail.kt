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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Width the list reserves on the right so row labels stay clear of the rail. */
val RAIL_RESERVED_WIDTH = 96.dp

/**
 * A stack of category names on the right edge. Tap one to jump to it; drag along the stack to
 * scrub. [current] is the index of the category at the top of the viewport.
 */
@Composable
fun CategoryRail(
    names: List<String>,
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
            .widthIn(max = RAIL_RESERVED_WIDTH - 8.dp)
            .background(textColor.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp)
            .pointerInput(names) {
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
        val arr = FloatArray(names.size * 2)
        names.forEachIndexed { i, name ->
            val active = i == current || i == last
            Text(
                text = name,
                color = if (active) textColor else textColor.copy(alpha = 0.75f),
                style = textStyle,
                fontSize = if (active) 13.sp else 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .onGloballyPositioned { c ->
                        val top = c.positionInParent().y
                        arr[i * 2] = top; arr[i * 2 + 1] = top + c.size.height
                        if (i == names.lastIndex) bounds.value = arr.copyOf()
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}
