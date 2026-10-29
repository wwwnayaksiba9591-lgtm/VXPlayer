package com.vxplayer.player
import org.junit.Assert.assertEquals
import org.junit.Test
class PlaybackMathTest {
    @Test fun normalSwipeDoesNotDependOnVideoDuration() {
        for (duration in listOf(300_000L, 10_800_000L)) {
            assertEquals(80_000L, seekPreview(60_000, 100f, 600f, duration))
            assertEquals(40_000L, seekPreview(60_000, -100f, 600f, duration))
        }
    }
    @Test fun eachSensitivityHasFixedFullWidthSpan() {
        assertEquals(listOf(60, 120, 240), SeekSensitivity.entries.map { it.seconds })
        for (level in SeekSensitivity.entries) {
            assertEquals(level, SeekSensitivity.valueOf(level.name))
            assertEquals(200_000L + level.seconds * 1000L, seekPreview(200_000, 400f, 400f, 1_000_000, level))
        }
    }
    @Test fun clampsAtBothEndsAndHandlesUnknownDurationAndZeroWidth() {
        assertEquals(0L, seekPreview(5000, -400f, 400f, 60_000))
        assertEquals(60_000L, seekPreview(5000, 400f, 400f, 60_000))
        assertEquals(65_000L, seekPreview(5000, 200f, 400f, -1))
        assertEquals(5000L, seekPreview(5000, 200f, 0f, 60_000))
    }
    @Test fun slopAndDiagonalsDoNotSelectAnAxis() {
        assertEquals(0, swipeAxis(15f, 0f))
        assertEquals(0, swipeAxis(30f, 30f))
        assertEquals(2, swipeAxis(-20f, 5f))
        assertEquals(1, swipeAxis(5f, 20f))
    }
}
