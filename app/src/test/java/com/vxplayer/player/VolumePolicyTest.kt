package com.vxplayer.player
import org.junit.Assert.*
import org.junit.Test
class VolumePolicyTest {
    @Test fun volumeRespectsUserCaps() {
        assertEquals(100, boundedVolume(200, false, 200))
        assertEquals(150, boundedVolume(200, true, 150))
        assertEquals(200, boundedVolume(999, true, 200))
        assertEquals(0, boundedVolume(-5, true, 200))
    }
    @Test fun gainUsesAmplitudeRatioAndNeverNegativeGain() {
        assertEquals(0, loudnessGainMb(50)); assertEquals(0, loudnessGainMb(100))
        assertEquals(352, loudnessGainMb(150)); assertEquals(602, loudnessGainMb(200))
    }
    @Test fun warningIsRequiredOnlyBeyond150AndUntilAcknowledged() {
        assertFalse(needsBoostWarning(150, false)); assertTrue(needsBoostWarning(151, false)); assertFalse(needsBoostWarning(200, true))
    }
    @Test fun changingVideoResetsOnlyWhenRequested() {
        assertEquals(100, volumeAfterVideoChange(200, true, false))
        assertEquals(200, volumeAfterVideoChange(200, true, true))
        assertEquals(200, volumeAfterVideoChange(200, false, false))
        assertEquals(70, volumeAfterVideoChange(70, true, false))
    }
}
