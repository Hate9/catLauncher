/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.wallpaper

import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.BroadcastReceiver
import android.app.WallpaperManager
import android.app.KeyguardManager
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A live wallpaper that shows one image and pans it vertically (and horizontally, if there is
 * slack) by the offsets a launcher reports. Static wallpapers stopped honouring vertical offsets
 * around Android 10, which is the whole reason this exists.
 *
 * The image source is a file path or content URI in [WallpaperPrefs]; Tasker-style automation
 * changes it through [SetWallpaperReceiver].
 */
class CatWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ImageEngine()

    private inner class ImageEngine : Engine() {
        private val prefs = WallpaperPrefs(this@CatWallpaperService)
        private var bitmap: Bitmap? = null
        private var loadedFor: String? = null
        private var width = 0
        private var height = 0
        private var xOffset = 0.5f
        private var yOffset = 0f
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> reload(); draw() }
        private val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        /** The lock screen always sees the top of the image, whatever the launcher last reported. */
        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = draw()
        }

        private fun showingLockScreen(): Boolean {
            val lockEngine = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                wallpaperFlags and WallpaperManager.FLAG_LOCK != 0 && wallpaperFlags and WallpaperManager.FLAG_SYSTEM == 0
            return lockEngine || keyguard.isKeyguardLocked
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.register(listener)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
            else registerReceiver(screenReceiver, filter)
        }

        override fun onDestroy() {
            runCatching { unregisterReceiver(screenReceiver) }
            prefs.unregister(listener)
            bitmap?.recycle()
            bitmap = null
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w; height = h
            reload()
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) draw()
        }

        override fun onOffsetsChanged(x: Float, y: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int) {
            xOffset = x; yOffset = y
            draw()
        }

        /** Decodes the configured image scaled so it covers the surface; extra height is what gets panned. */
        private fun reload() {
            val source = prefs.source
            if (width == 0 || height == 0) return
            if (source == loadedFor && bitmap != null) return
            bitmap?.recycle()
            bitmap = source?.let { decodeCover(this@CatWallpaperService, it, width, height) }
            loadedFor = source
            Log.i(TAG, "loaded ${source ?: "(none)"} -> ${bitmap?.let { "${it.width}x${it.height}" } ?: "null"} for ${width}x$height")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) notifyColorsChanged()
        }

        /** Lets the system (and the launcher) pick readable text colours for the image. */
        override fun onComputeColors(): android.app.WallpaperColors? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
            val bmp = bitmap ?: return android.app.WallpaperColors(android.graphics.Color.valueOf(FALLBACK), null, null)
            return runCatching { android.app.WallpaperColors.fromBitmap(bmp) }.getOrNull()
        }

        private fun draw() {
            val holder = surfaceHolder
            val canvas = try { holder.lockCanvas() } catch (e: Exception) { null } ?: return
            try {
                val bmp = bitmap
                if (bmp == null) {
                    canvas.drawColor(FALLBACK)
                } else {
                    val slackX = max(0, bmp.width - canvas.width)
                    val slackY = max(0, bmp.height - canvas.height)
                    val left = -(slackX * xOffset).roundToInt()
                    val top = -(slackY * (if (showingLockScreen()) 0f else yOffset)).roundToInt()
                    canvas.drawColor(Color.BLACK)
                    canvas.drawBitmap(bmp, left.toFloat(), top.toFloat(), paint)
                }
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }

    companion object {
        private const val TAG = "CatWallpaper"
        const val FALLBACK = 0xFF2E2A47.toInt()

        private fun open(context: Context, source: String): InputStream? = runCatching {
            if (source.startsWith("content:") || source.startsWith("file:")) {
                context.contentResolver.openInputStream(Uri.parse(source))
            } else File(source).inputStream()
        }.onFailure { Log.w(TAG, "Cannot open $source", it) }.getOrNull()

        /**
         * Scales the image so it fills [w] wide; if that leaves it shorter than [h], scales to
         * fill the height instead (then it pans horizontally). Height is capped at 4 screens so a
         * giant panorama cannot blow the heap.
         */
        fun decodeCover(context: Context, source: String, w: Int, h: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // decodeStream returns null for a bounds-only pass; only the stream itself can fail here.
            val probe = open(context, source) ?: return null
            probe.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.w(TAG, "Not an image: $source"); return null
            }
            val scale = max(w.toFloat() / bounds.outWidth, h.toFloat() / bounds.outHeight)
            var targetW = (bounds.outWidth * scale).roundToInt()
            var targetH = (bounds.outHeight * scale).roundToInt()
            val maxH = h * 4
            if (targetH > maxH) { targetW = (targetW * maxH.toFloat() / targetH).roundToInt(); targetH = maxH }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (bounds.outWidth / (inSampleSize * 2) >= targetW && bounds.outHeight / (inSampleSize * 2) >= targetH) inSampleSize *= 2
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = open(context, source)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            if (raw.width == targetW && raw.height == targetH) return raw
            val scaled = Bitmap.createBitmap(max(1, targetW), max(1, targetH), Bitmap.Config.ARGB_8888)
            Canvas(scaled).drawBitmap(raw, Rect(0, 0, raw.width, raw.height), RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            raw.recycle()
            return scaled
        }

        /** Whether arbitrary file paths can be read: All files access on 11+, the storage permission before. */
        fun hasFileAccess(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
            else androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

/** The one setting the wallpaper has: where its image comes from. */
class WallpaperPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)

    /** A file path or a content/file URI string, or null for the plain fallback colour. */
    var source: String?
        get() = prefs.getString(KEY_SOURCE, null)
        set(value) { prefs.edit().putString(KEY_SOURCE, value).apply() }

    fun register(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(l)
    fun unregister(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val KEY_SOURCE = "source"
    }
}
