package io.github.hate9.catlauncher

import io.github.hate9.catlauncher.data.Category
import io.github.hate9.catlauncher.data.ConfigStore
import io.github.hate9.catlauncher.data.Item
import io.github.hate9.catlauncher.data.ItemRef
import io.github.hate9.catlauncher.data.LauncherConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {
    private fun app(pkg: String, at: Long) = Item(ItemRef.App(pkg, "$pkg.Main", 0), at)

    @Test
    fun roundTripsThroughJson() {
        val cfg = LauncherConfig(
            categories = listOf(
                Category("c1", "Work", manual = true, items = listOf(app("a", 2), app("b", 1))),
                Category.uncategorized(listOf(Item(ItemRef.Shortcut("m", "nav", 0), 3, "Navigate home"))),
            )
        )
        val text = ConfigStore.encode(cfg)
        assertEquals(cfg, ConfigStore.decode(text))
        assertTrue(text.contains("\"type\": \"shortcut\""))
    }

    @Test
    fun normalizedKeepsUncategorizedLastAndDedupes() {
        val cfg = LauncherConfig(
            categories = listOf(
                Category.uncategorized(listOf(app("a", 1))),
                Category("c1", "Work", items = listOf(app("a", 2), app("b", 1))),
            )
        ).normalized()
        assertEquals(listOf("c1", "uncategorized"), cfg.categories.map { it.id })
        assertEquals(listOf("a", "b"), cfg.categories[0].items.map { it.ref.packageName })
        assertTrue(cfg.uncategorized.items.isEmpty())
    }

    @Test
    fun decodeToleratesMissingFields() {
        val cfg = ConfigStore.decode("""{"categories":[{"id":"x","name":"X"}]}""")
        assertEquals("X", cfg.categories[0].name)
        assertEquals("uncategorized", cfg.categories.last().id)
    }
}
