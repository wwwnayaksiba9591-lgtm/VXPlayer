package com.vxplayer.player
import org.junit.Assert.*
import org.junit.Test
class EnhancePolicyTest {
    @Test fun allPresetsAreValidAndOriginalIsNeutral() {
        assertEquals(listOf("Original", "Vivid", "Cinema", "Soft", "Sharp", "Night"), ENHANCE_PRESETS.keys.toList())
        ENHANCE_PRESETS.values.forEach { assertEquals(it, it.bounded()) }
        assertEquals(EnhanceValues(), ENHANCE_PRESETS["Original"])
        assertTrue(ENHANCE_PRESETS.filterKeys { it != "Original" }.values.all { it != EnhanceValues() })
    }
    @Test fun invalidAndOutOfRangeValuesAreSanitized() {
        val value = EnhanceValues(Float.NaN, 999f, -2f, Float.POSITIVE_INFINITY, -5f).bounded()
        assertEquals(EnhanceValues(0f, .5f, 0f, 0f, -.2f), value)
    }
    @Test fun bypassesNeutralPipBackgroundAndUnsupportedPaths() {
        val vivid = ENHANCE_PRESETS.getValue("Vivid")
        assertTrue(shouldEnhance(true, vivid, true, false, false))
        assertFalse(shouldEnhance(false, vivid, true, false, false))
        assertFalse(shouldEnhance(true, EnhanceValues(), true, false, false))
        assertFalse(shouldEnhance(true, vivid, true, true, false))
        assertFalse(shouldEnhance(true, vivid, true, false, true))
        assertFalse(shouldEnhance(true, vivid, false, false, false))
    }
}
