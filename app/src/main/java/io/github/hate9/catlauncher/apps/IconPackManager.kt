/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStreamReader

/**
 * Icon packs in the de-facto ADW/Nova format: an app answering one of the theme intents, with
 * `appfilter.xml` mapping components to drawable names and `drawable.xml` listing everything
 * the pack offers. Parsed once per pack and cached for the process lifetime.
 */
class IconPackManager(private val context: Context) {
    data class Pack(val packageName: String, val label: String)

    class Loaded(
        val packageName: String,
        val resources: Resources,
        /** "pkg/cls" → drawable name. */
        val byComponent: Map<String, String>,
        /** Every drawable the pack lists, in pack order. */
        val drawables: List<String>,
    )

    private val pm = context.packageManager
    private val cache = HashMap<String, Loaded?>()

    fun installedPacks(): List<Pack> = THEME_ACTIONS
        .flatMap { action -> runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList()) }
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != context.packageName }
        .map { pkg -> Pack(pkg, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)) }
        .sortedBy { it.label.lowercase() }

    @Synchronized
    fun load(packageName: String): Loaded? = cache.getOrPut(packageName) { parse(packageName) }

    @Synchronized
    fun evict(packageName: String) {
        cache.remove(packageName)
    }

    private fun parse(packageName: String): Loaded? {
        val res = runCatching { pm.getResourcesForApplication(packageName) }.getOrNull() ?: return null
        val byComponent = HashMap<String, String>()
        val drawables = LinkedHashSet<String>()
        parseXml(res, packageName, "appfilter") { parser ->
            if (parser.name == "item") {
                val component = parser.getAttributeValue(null, "component") ?: return@parseXml
                val drawable = parser.getAttributeValue(null, "drawable") ?: return@parseXml
                COMPONENT_RE.matchEntire(component)?.let { byComponent[it.groupValues[1]] = drawable }
            }
        }
        parseXml(res, packageName, "drawable") { parser ->
            if (parser.name == "item") parser.getAttributeValue(null, "drawable")?.let { drawables += it }
        }
        if (drawables.isEmpty()) drawables += byComponent.values
        if (byComponent.isEmpty() && drawables.isEmpty()) {
            Log.w(TAG, "$packageName has no appfilter.xml or drawable.xml")
            return null
        }
        return Loaded(packageName, res, byComponent, drawables.toList())
    }

    /** Packs ship these either as an XML resource or as a raw asset; try both. */
    private fun parseXml(res: Resources, packageName: String, name: String, onTag: (XmlPullParser) -> Unit) {
        val parser: XmlPullParser = run {
            val id = res.getIdentifier(name, "xml", packageName)
            if (id != 0) return@run res.getXml(id)
            val stream = runCatching { res.assets.open("$name.xml") }.getOrNull() ?: return
            XmlPullParserFactory.newInstance().newPullParser().apply { setInput(InputStreamReader(stream)) }
        }
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) onTag(parser)
                event = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing $name.xml of $packageName", e)
        }
    }

    fun bitmap(pack: Loaded, drawable: String, px: Int): Bitmap? {
        val id = pack.resources.getIdentifier(drawable, "drawable", pack.packageName)
        if (id == 0) return null
        return runCatching { pack.resources.getDrawable(id, null).toBitmap(px, px) }.getOrNull()
    }

    fun iconFor(pack: Loaded, component: ComponentName, px: Int): Bitmap? {
        val name = pack.byComponent[component.packageName + "/" + component.className] ?: return null
        return bitmap(pack, name, px)
    }

    companion object {
        private const val TAG = "IconPackManager"
        private val COMPONENT_RE = Regex("""ComponentInfo\{([^}]+)\}""")
        val THEME_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "org.adw.launcher.icons.ACTION_PICK_ICON",
        )
    }
}
