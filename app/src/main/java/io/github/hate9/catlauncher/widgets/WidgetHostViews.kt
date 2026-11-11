/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

const val WIDGET_HOST_ID = 0xCA7

class CatAppWidgetHost(context: Context, private val onLongPress: () -> Unit) : AppWidgetHost(context, WIDGET_HOST_ID) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LongPressWidgetHostView(context, onLongPress)
}

/**
 * Widgets eat touches, so the launcher watches the stream itself: a still press longer than the
 * system long-press timeout fires [onLongPress] and the widget's own children get a cancel.
 */
class LongPressWidgetHostView(context: Context, private val onLongPress: () -> Unit) : AppWidgetHostView(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var armed = false
    private var fired = false
    private val fire = Runnable {
        armed = false
        fired = true
        isPressed = false
        onLongPress()
    }

    private fun track(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                fired = false
                armed = true
                postDelayed(fire, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (armed && (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop)) {
                armed = false
                removeCallbacks(fire)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                armed = false
                removeCallbacks(fire)
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        if (fired) return true
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Reached either because we intercepted, or because no child wanted the stream.
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) track(ev) else if (!fired) track(ev)
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(fire)
        super.onDetachedFromWindow()
    }
}
