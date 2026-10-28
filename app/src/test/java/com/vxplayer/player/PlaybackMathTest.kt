package com.vxplayer.player
import org.junit.Assert.assertEquals
import org.junit.Test
class PlaybackMathTest {
    @Test fun seekPreviewClampsAtBothEnds() {
        assertEquals(0L, seekPreview(5000L, -1f, 60000L))
        assertEquals(60000L, seekPreview(5000L, 1f, 60000L))
        assertEquals(35000L, seekPreview(5000L, .5f, 60000L))
    }
    @Test fun unknownDurationUsesBoundedGestureSpan() {
        assertEquals(65000L, seekPreview(5000L, .5f, -1L))
    }
}
