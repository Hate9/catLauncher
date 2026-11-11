/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.ui

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.abs

/**
 * Category-header snapping.
 *
 * A fling lands on the next header in the direction of travel, whether the decay would have
 * stopped short of it or sailed past. Headers that are not yet composed have their position
 * estimated from the average sizes of the rows that are, and the final snap step corrects the
 * estimate once the header is on screen. A slow release (no real fling) settles on the nearest
 * header within half a viewport, or stays put.
 */
@Composable
fun rememberHeaderSnapFlingBehavior(
    state: LazyListState,
    headerTopGapPx: Float,
    rows: () -> List<Row>,
    enabled: () -> Boolean,
): FlingBehavior {
    // The provider is remembered once; read the latest lambdas through state so they do not go stale.
    val rowsState = rememberUpdatedState(rows)
    val enabledState = rememberUpdatedState(enabled)
    val provider = remember(state, headerTopGapPx) {
        object : SnapLayoutInfoProvider {
            private val LazyListItemInfo.isHeader get() = (key as? String)?.startsWith("h:") == true
            private fun enabled() = enabledState.value()
            private fun rows() = rowsState.value()

            /** Headers all measure the same, so one sighting makes later estimates exact. */
            private var knownHeaderSize: Float? = null
            private fun noteHeaderSize(visible: List<LazyListItemInfo>) {
                visible.firstOrNull { it.isHeader }?.let { knownHeaderSize = it.size.toFloat() }
            }

            override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float {
                if (!enabled()) return decayOffset
                val layout = state.layoutInfo
                val visible = layout.visibleItemsInfo
                if (visible.isEmpty()) return decayOffset
                // Treat anything shorter than a quarter viewport as a nudge, not a fling.
                if (abs(decayOffset) < layout.viewportSize.height / 4f) return decayOffset
                val start = layout.viewportStartOffset
                val forward = decayOffset > 0
                val list = rows()
                noteHeaderSize(visible)

                val headerSize = knownHeaderSize ?: 144f
                val rowSize = (visible.filter { !it.isHeader }.map { it.size }.average().takeIf { !it.isNaN() } ?: 120.0).toFloat()
                fun estimate(index: Int) = if (list.getOrNull(index) is Row.Header) headerSize else rowSize
                var estimated = false

                val target: Float? = if (forward) {
                    visible.firstOrNull { it.isHeader && it.offset > start + 1 }?.let { it.offset.toFloat() } ?: run {
                        estimated = true
                        val last = visible.last()
                        var y = (last.offset + last.size).toFloat()
                        var i = last.index + 1
                        var found: Float? = null
                        while (i < list.size) {
                            if (list[i] is Row.Header) { found = y; break }
                            y += estimate(i); i++
                        }
                        found
                    }
                } else {
                    visible.lastOrNull { it.isHeader && it.offset < start - 1 }?.let { it.offset.toFloat() } ?: run {
                        estimated = true
                        val first = visible.first()
                        var y = first.offset.toFloat()
                        var i = first.index - 1
                        var found: Float? = null
                        while (i >= 0) {
                            y -= estimate(i)
                            if (list[i] is Row.Header) { found = y; break }
                            i--
                        }
                        found
                    }
                }
                if (target == null) return decayOffset
                // An estimate can be off by a little; stop short of it so the exact snap that follows
                // keeps moving the same way instead of pulling back.
                val backoff = if (estimated) (rowSize * 0.6f) * (if (forward) -1f else 1f) else 0f
                return target + headerTopGapPx - start + backoff
            }

            override fun calculateSnapOffset(velocity: Float): Float {
                if (!enabled()) return 0f
                val layout = state.layoutInfo
                noteHeaderSize(layout.visibleItemsInfo)
                val start = layout.viewportStartOffset
                var lower = Float.NEGATIVE_INFINITY
                var upper = Float.POSITIVE_INFINITY
                for (item in layout.visibleItemsInfo) {
                    val isHeader = item.isHeader
                    if (!isHeader && item.index != 0) continue
                    // Land the divider at the top, not the breathing room above it.
                    val target = item.offset + if (isHeader) headerTopGapPx else 0f
                    val distance = target - start
                    if (distance <= 0f && distance > lower) lower = distance
                    if (distance >= 0f && distance < upper) upper = distance
                }
                val chosen = when {
                    lower == Float.NEGATIVE_INFINITY && upper == Float.POSITIVE_INFINITY -> 0f
                    lower == Float.NEGATIVE_INFINITY -> upper
                    upper == Float.POSITIVE_INFINITY -> lower
                    abs(velocity) < 1f -> if (abs(lower) < upper) lower else upper
                    velocity > 0 -> upper
                    else -> lower
                }
                // Do not yank the list across more than half a screen just to line up a header.
                return if (abs(chosen) > layout.viewportSize.height / 2f) 0f else chosen
            }
        }
    }
    // The default snap spring inherits the fling's leftover velocity and overshoots; a tween does not.
    val decay = rememberSplineBasedDecay<Float>()
    return remember(provider, decay) {
        snapFlingBehavior(
            snapLayoutInfoProvider = provider,
            decayAnimationSpec = decay,
            snapAnimationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        )
    }
}
