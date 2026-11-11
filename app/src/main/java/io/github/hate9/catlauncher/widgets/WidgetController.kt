/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.widgets

import android.app.Activity
import android.app.ActivityOptions
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import io.github.hate9.catlauncher.data.LauncherRepository
import io.github.hate9.catlauncher.data.WidgetEntry
import kotlin.math.roundToInt

/**
 * Owns the AppWidgetHost for the home screen and drives the add flow:
 * allocate id → bind (asking for permission if needed) → configure (if the provider has a
 * configure activity) → persist. Lives as long as the activity.
 */
class WidgetController(
    private val activity: Activity,
    private val repository: LauncherRepository,
    onLongPress: () -> Unit,
) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    val host = CatAppWidgetHost(activity, onLongPress)
    private val views = HashMap<Int, AppWidgetHostView>()
    private var pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingProvider: AppWidgetProviderInfo? = null

    fun startListening() {
        runCatching { host.startListening() }.onFailure { Log.w(TAG, "startListening", it) }
        pruneMissing()
    }

    fun stopListening() {
        runCatching { host.stopListening() }
    }

    /** Drops entries whose widget the system no longer knows (provider uninstalled, id revoked). */
    private fun pruneMissing() {
        repository.config.value.widgets.forEach { w ->
            if (manager.getAppWidgetInfo(w.appWidgetId) == null) {
                Log.i(TAG, "Widget ${w.appWidgetId} (${w.provider}) is gone, removing")
                remove(w.appWidgetId)
            }
        }
    }

    fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(appWidgetId)

    fun installedProviders(): List<AppWidgetProviderInfo> {
        val um = activity.getSystemService(Context.USER_SERVICE) as UserManager
        val pm = activity.packageManager
        return um.userProfiles.flatMap { profile ->
            runCatching { manager.getInstalledProvidersForProfile(profile) }.getOrDefault(emptyList())
        }.sortedWith(compareBy({ it.provider.packageName }, { it.loadLabel(pm) }))
    }

    /** The host view for a persisted widget, created once and re-used across recompositions. */
    fun viewFor(entry: WidgetEntry, widthDp: Int): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(entry.appWidgetId) ?: return null
        val view = views.getOrPut(entry.appWidgetId) { host.createView(activity, entry.appWidgetId, info) }
        (view.parent as? ViewGroup)?.removeView(view)
        @Suppress("DEPRECATION")
        view.updateAppWidgetSize(Bundle(), widthDp, entry.heightDp, widthDp, entry.heightDp)
        return view
    }

    fun beginAdd(provider: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        pendingProvider = provider
        val bound = runCatching {
            manager.bindAppWidgetIdIfAllowed(id, provider.profile, provider.provider, null)
        }.getOrDefault(false)
        if (bound) {
            configureOrFinish(id, provider)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile)
            try {
                activity.startActivityForResult(intent, REQ_BIND)
            } catch (e: Exception) {
                Log.w(TAG, "No bind permission activity", e)
                Toast.makeText(activity, "Cannot request widget permission on this device", Toast.LENGTH_LONG).show()
                abort(id)
            }
        }
    }

    private fun configureOrFinish(id: Int, provider: AppWidgetProviderInfo?) {
        val info = provider ?: manager.getAppWidgetInfo(id)
        if (info?.configure == null) {
            finishAdd(id, info)
            return
        }
        try {
            // Android 14+ refuses to start the configure activity from the host's PendingIntent
            // unless background activity starts are explicitly allowed in the options.
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else null
            host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE, options)
        } catch (e: Exception) {
            Log.w(TAG, "startAppWidgetConfigureActivityForResult failed, trying direct intent", e)
            try {
                val direct = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(info.configure)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                activity.startActivityForResult(direct, REQ_CONFIGURE)
            } catch (e2: Exception) {
                Log.w(TAG, "Direct configure failed too; adding unconfigured", e2)
                Toast.makeText(activity, "Widget could not be configured", Toast.LENGTH_SHORT).show()
                finishAdd(id, info)
            }
        }
    }

    private fun finishAdd(id: Int, info: AppWidgetProviderInfo?) {
        val density = activity.resources.displayMetrics.density
        val minHeightDp = ((info?.minHeight ?: 0) / density).roundToInt()
        repository.addWidget(WidgetEntry(id, info?.provider?.flattenToString() ?: "?", minHeightDp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)))
        clearPending()
    }

    private fun abort(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
        clearPending()
    }

    private fun clearPending() {
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider = null
    }

    /** Returns true when the result belonged to the widget flow. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId) ?: pendingId
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK) configureOrFinish(id, pendingProvider) else abort(id)
            REQ_CONFIGURE -> if (resultCode == Activity.RESULT_OK) finishAdd(id, manager.getAppWidgetInfo(id) ?: pendingProvider) else abort(id)
            else -> return false
        }
        return true
    }

    fun remove(appWidgetId: Int) {
        views.remove(appWidgetId)
        runCatching { host.deleteAppWidgetId(appWidgetId) }
        repository.removeWidget(appWidgetId)
    }

    companion object {
        private const val TAG = "WidgetController"
        const val REQ_BIND = 0xB1
        const val REQ_CONFIGURE = 0xC0
        const val MIN_HEIGHT_DP = 48
        const val MAX_HEIGHT_DP = 800
    }
}
