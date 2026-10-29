package com.vxplayer.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackSafetyTest {
    @Test fun audioSelectionKeepsSupportedSelectionOrFallsBackAndHandlesSilence() {
        assertEquals(1, supportedAudioIndex(listOf(false, true, true)))
        assertEquals(2, supportedAudioIndex(listOf(false, true, true), 2))
        assertEquals(1, supportedAudioIndex(listOf(false, true), 0))
        assertEquals(-1, supportedAudioIndex(listOf(false, false)))
        assertEquals(-1, supportedAudioIndex(emptyList()))
    }
    @Test fun highDepthHdrDolbyAndProtectedVideoDisableEffects() {
        assertTrue(unsafeVideoEffects("video/hevc", "hvc1.2.4.L153", -1, -1, false))
        assertTrue(unsafeVideoEffects("video/avc", null, 6, 8, false))
        assertTrue(unsafeVideoEffects("video/av01", "av01.0.08M.10", -1, -1, false))
        assertTrue(unsafeVideoEffects("video/dolby-vision", null, -1, -1, false))
        assertTrue(unsafeVideoEffects("video/avc", null, -1, 10, false))
        assertTrue(unsafeVideoEffects("video/avc", "avc1.6e0028", -1, -1, false))
        assertTrue(unsafeVideoEffects("video/avc", null, -1, 8, true))
        assertFalse(unsafeVideoEffects("video/avc", "avc1.640028", 3, 8, false))
        assertTrue(shouldRetryWithoutEffects(true, false))
        assertFalse(shouldRetryWithoutEffects(true, true))
        assertFalse(shouldRetryWithoutEffects(false, false))
    }
    @Test fun seekingHandlesUnknownZeroDurationOverflowAndIndexedFormats() {
        assertEquals(20_000L, boundedSeek(20_000, -9223372036854775807L))
        assertEquals(20_000L, boundedSeek(20_000, 0))
        assertEquals(10L, boundedSeek(20_000, 10))
        assertEquals(0L, seekWithOffset(100, -1000, -1))
        assertEquals(Long.MAX_VALUE, seekWithOffset(Long.MAX_VALUE - 1, 500, 0))
        assertTrue(preferSyncSeek("video/x-matroska", "Video"))
        assertTrue(preferSyncSeek(null, "movie.M2TS"))
        assertFalse(preferSyncSeek("video/mp4", "movie.mp4"))
    }
    @Test fun geometryAccountsForRotationAndNonSquarePixels() {
        assertEquals(1080 to 1920, displayDimensions(1920, 1080, 90))
        assertEquals(1280 to 480, displayDimensions(640, 480, 0, 2f))
        assertEquals(640 to 480, displayDimensions(640, 480, 0, Float.NaN))
    }
    @Test fun reportDoesNotLeakLinksTokensPathsOrPrivateNames() {
        val text = sanitizeProblemText("Failed https://host.example/movie?token=SECRET\nPath /storage/emulated/0/John Smith/film.mkv\nC:\\Users\\John Smith\\movie.mp4\ntoken=SECRET\naccount person@example.com")
        assertFalse(text.contains("https://")); assertFalse(text.contains("SECRET"))
        assertFalse(text.contains("John")); assertFalse(text.contains("person@example.com"))
        assertEquals("film.mkv", reportFileName("/storage/emulated/0/film.mkv", false, false))
        assertEquals("Network stream", reportFileName("https://host/token", true, false))
        assertEquals("Private video", reportFileName("Secret title", false, true))
    }
}
