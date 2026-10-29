package com.vxplayer.player
import org.junit.Assert.*
import org.junit.Test
class SrtTimingTest {
    @Test fun shiftsForwardAndHandlesHourRollover() {
        val shifted = SrtTiming.shift("1\n00:59:59,950 --> 01:00:01,000\nHello", 100)
        assertTrue(shifted.contains("01:00:00,050 --> 01:00:01,100"))
    }
    @Test fun clipsPartialNegativeCuesAndDropsExpiredOnes() {
        val shifted = SrtTiming.shift("1\n00:00:00,000 --> 00:00:00,100\nGone\n\n2\n00:00:00,200 --> 00:00:01,000\nKeep", -300)
        assertFalse(shifted.contains("Gone")); assertTrue(shifted.startsWith("1\n00:00:00,000 --> 00:00:00,700"))
    }
    @Test fun supportsBomCrlfMultilineTextAndCoordinates() {
        val shifted = SrtTiming.shift("\uFEFF1\r\n00:00:01,000 --> 00:00:02,000 X1:1\r\nFirst\r\nSecond\r\n", 0)
        assertTrue(shifted.contains("X1:1\nFirst\nSecond"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsMalformedSubtitles() { SrtTiming.shift("not an SRT", 100) }
    @Test(expected = IllegalArgumentException::class) fun rejectsExcessiveOffsets() { SrtTiming.shift("", 60_100) }
    @Test fun rejectsMalformedTimingAndReversedRanges() {
        listOf("00:61:01,000 --> 00:62:02,000", "00:00:02,000 --> 00:00:01,000",
            "00:00:01,00 --> 00:00:02,000").forEach { timing ->
            assertThrows(IllegalArgumentException::class.java) { SrtTiming.shift("1\n$timing\nCaption", -100) }
        }
    }
    @Test fun oneSecondStepsShiftBothEndsAndClampAtZero() {
        assertTrue(SrtTiming.shift("1\n00:00:00,500 --> 00:00:02,000\nCaption", -1000)
            .contains("00:00:00,000 --> 00:00:01,000"))
        assertTrue(SrtTiming.shift("1\n00:00:00,500 --> 00:00:02,000\nCaption", 1000)
            .contains("00:00:01,500 --> 00:00:03,000"))
    }
}
