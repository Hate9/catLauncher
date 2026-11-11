/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.ui

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/**
 * Pixel-Launcher style long press: the row's own long click opens a menu and arms this
 * controller; if the finger then travels more than [thresholdPx] the menu is dismissed and
 * the row lifts and follows the finger. Hopping over a neighbouring row or header reorders the
 * working copy in the view model; the drop commits it.
 */
class DragController(
    private val listState: LazyListState,
    private val thresholdPx: Float,
    private val edgePx: Float,
    private val onBegin: (key: String) -> Unit,
    private val onMove: (key: String, from: Int, to: Int) -> Boolean,
    private val onEnd: (commit: Boolean) -> Unit,
    private val onDragStarted: () -> Unit,
) {
    /** Row armed by a long press whose menu is still open. */
    var pendingKey by mutableStateOf<String?>(null)
        private set

    var draggingKey by mutableStateOf<String?>(null)
        private set

    private var armedPointerTravel = Offset.Zero
    private var startOffset = 0
    private var startSize = 0
    private var delta by mutableFloatStateOf(0f)

    /** Positive scrolls towards the end, negative towards the start, zero idle. */
    var autoScroll by mutableFloatStateOf(0f)
        private set

    val isActive: Boolean get() = pendingKey != null || draggingKey != null

    fun arm(key: String) {
        pendingKey = key
        armedPointerTravel = Offset.Zero
    }

    fun disarm() {
        if (draggingKey == null) pendingKey = null
    }

    /** Called from the list's pointer handler for every move while [isActive]. */
    fun onPointerMove(change: Offset) {
        val dragging = draggingKey
        if (dragging != null) {
            delta += change.y
            settle()
            return
        }
        val pending = pendingKey ?: return
        armedPointerTravel += change
        if (abs(armedPointerTravel.y) < thresholdPx && abs(armedPointerTravel.x) < thresholdPx) return
        val info = itemInfo(pending) ?: run { pendingKey = null; return }
        pendingKey = null
        draggingKey = pending
        startOffset = info.offset
        startSize = info.size
        delta = armedPointerTravel.y
        onBegin(pending)
        onDragStarted()
        settle()
    }

    fun onPointerUp() {
        if (draggingKey != null) {
            onEnd(true)
            draggingKey = null
            autoScroll = 0f
        }
        // A plain long press without travel leaves the menu open; the menu dismisses itself.
    }

    fun cancel() {
        if (draggingKey != null) onEnd(false)
        draggingKey = null
        pendingKey = null
        autoScroll = 0f
    }

    /** Translation to apply to the dragged row so it stays under the finger while the list shuffles. */
    fun translationFor(key: String): Float {
        if (key != draggingKey) return 0f
        val info = itemInfo(key) ?: return 0f
        return startOffset + delta - info.offset
    }

    private fun itemInfo(key: String): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    /** Hop over neighbours whose middle the dragged row has crossed, and update auto-scroll. */
    fun settle() {
        val key = draggingKey ?: return
        val layout = listState.layoutInfo
        val dragged = layout.visibleItemsInfo.firstOrNull { it.key == key }
        val top = startOffset + delta
        val bottom = top + startSize
        val mid = (top + bottom) / 2f
        if (dragged != null) {
            val target = layout.visibleItemsInfo.firstOrNull { it.key != key && mid >= it.offset && mid < it.offset + it.size }
            if (target != null) {
                val targetMid = target.offset + target.size / 2f
                val crossing = (target.index > dragged.index && mid > targetMid) || (target.index < dragged.index && mid < targetMid)
                if (crossing) onMove(key, dragged.index, target.index)
            }
        }
        autoScroll = when {
            top < layout.viewportStartOffset + edgePx -> -((layout.viewportStartOffset + edgePx - top) / edgePx).coerceAtMost(1f)
            bottom > layout.viewportEndOffset - edgePx -> ((bottom - (layout.viewportEndOffset - edgePx)) / edgePx).coerceAtMost(1f)
            else -> 0f
        }
    }
}
