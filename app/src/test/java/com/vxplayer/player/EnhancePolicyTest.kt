package com.vxplayer.player
import org.junit.Assert.*
import org.junit.Test
class EnhancePolicyTest {
    @Test fun allPresetsAreValidAndOriginalIsNeutral() {
        assertEquals(listOf("Auto", "Vivid", "Cinema", "Sports/Action", "Anime/Cartoon", "Night", "Original"), ENHANCE_PRESETS.keys.toList())
        ENHANCE_PRESETS.values.forEach { assertEquals(it, it.bounded()) }
        assertEquals(EnhanceValues(), ENHANCE_PRESETS["Original"])
        assertTrue(ENHANCE_PRESETS.filterKeys { it != "Original" }.values.all { it != EnhanceValues() })
    }
    @Test fun togglingAndOriginalKeepLastModeAndRememberPolicy() {
        var state = SmartEnhanceState()
        assertFalse(state.enabled); assertEquals("Auto", state.lastMode)
        state = state.toggle(); assertTrue(state.enabled)
        state = state.select("Cinema").toggle(); assertFalse(state.enabled)
        assertEquals("Cinema", state.toggle().lastMode)
        assertEquals("Cinema", state.select("Original").lastMode)
        assertEquals(state, state.restored(true))
        assertEquals(SmartEnhanceState(), state.restored(false))
        assertEquals("Sports/Action", smartMode("Sharp")); assertEquals("Cinema", smartMode("Soft"))
    }
    @Test fun autoSmoothingIsBoundedAndMakesSmallSteps() {
        var current = ENHANCE_PRESETS.getValue("Auto")
        repeat(100) { index ->
            val next = smoothAutoEnhance(current, if (index % 2 == 0) FrameSummary(0f, 0f) else FrameSummary(1f, 1f))
            assertTrue(next.brightness in -.06f.. .10f)
            assertTrue(next.contrast in .04f.. .16f)
            assertTrue(next.saturation in .95f..1.18f)
            assertTrue(kotlin.math.abs(next.brightness - current.brightness) <= .03f)
            current = next
        }
        assertEquals(current, smoothAutoEnhance(current, null, 0f))
        assertEquals(smoothAutoEnhance(current, null), smoothAutoEnhance(current, FrameSummary(Float.NaN, 0f)))
        assertEquals(smoothAutoEnhance(current, null).bounded(), smoothAutoEnhance(current, null))
    }
    @Test fun frameSummaryAndAudioProfilesHaveSafeRanges() {
        assertNull(summarizeFrame(intArrayOf()))
        assertEquals(FrameSummary(0f, 0f), summarizeFrame(intArrayOf(0xFF000000.toInt())))
        assertEquals(1f, summarizeFrame(intArrayOf(0xFFFFFFFF.toInt()))!!.luminance, .0001f)
        for (mode in ENHANCE_PRESETS.keys) {
            val profile = smartAudioProfile(mode)
            assertTrue(profile.bass in 0..1000); assertTrue(profile.spatial in 0..1000)
            assertTrue(listOf(profile.lowMb, profile.midMb, profile.highMb).all { it in -200..200 })
        }
        assertTrue(smartAudioProfile("Night").night)
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
