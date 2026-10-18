package com.cloudix.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceModelsTest {
    @Test fun hexColorAcceptsOnlyOpaqueRgb() {
        assertEquals("#D8B65A", CloudixHex.normalize("d8b65a"))
        assertEquals("#6750A4", CloudixHex.normalize(" #6750a4 "))
        assertNull(CloudixHex.normalize("#00112233"))
        assertNull(CloudixHex.normalize("#abcd"))
        assertNull(CloudixHex.normalize("#GGGGGG"))
        assertNull(CloudixHex.normalize(""))
    }

    @Test fun presetsAreUniqueAndValid() {
        val presets = CloudixAppearanceCatalog.presets
        assertTrue(presets.size >= 30)
        assertEquals(presets.size, presets.map { it.labelRes }.distinct().size)
        presets.forEach { assertEquals(it.hex, CloudixHex.normalize(it.hex)) }
    }

    @Test fun supportedStyleIdentifiersAreUnique() {
        assertEquals(
            setOf("soft", "strong", "amoled", "amoled_plain"),
            CloudixAppearanceCatalog.styles.map { it.first }.toSet()
        )
    }

    @Test fun immutableSnapshotUpdatesDontDestroyOtherSettings() {
        val before = AppearanceSnapshot(theme = "dark", accent = 4,
            overrides = mapOf("selection" to "#D8B65A"))
        val after = before.copy(accent = 20)
        assertEquals("#D8B65A", after.overrides["selection"])
        assertEquals(4, before.accent)
        assertEquals(20, after.accent)
    }
}
