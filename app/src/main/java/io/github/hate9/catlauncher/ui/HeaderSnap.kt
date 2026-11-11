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
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs

/**
 * Lets a fling decay normally, then settles so the nearest visible category header sits at the
 * top of the viewport. With no header in view the list stays where the fling left it.
 */
@Composable
fun rememberHeaderSnapFlingBehavior(state: LazyListState, headerTopGapPx: Float, enabled: () -> Boolean): FlingBehavior {
    val provider = remember(state, headerTopGapPx) {
        object : SnapLayoutInfoProvider {
            override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = decayOffset

            override fun calculateSnapOffset(velocity: Float): Float {
                if (!enabled()) return 0f
                val layout = state.layoutInfo
                val start = layout.viewportStartOffset
                var lower = Float.NEGATIVE_INFINITY
                var upper = Float.POSITIVE_INFINITY
                for (item in layout.visibleItemsInfo) {
                    val key = item.key as? String ?: continue
                    // Headers snap, and so does the very start of the list so the widget area
                    // above the first header stays reachable.
                    val isHeader = key.startsWith("h:")
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
    return rememberSnapFlingBehavior(provider)
}
