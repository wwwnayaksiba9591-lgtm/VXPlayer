package com.vxplayer.player
import org.junit.Assert.assertEquals
import org.junit.Test
class TrackPreferenceTest {
    private val tracks = listOf("a" to "en", "b" to "en", "c" to "fr")
    @Test fun exactIdentityWinsOverLanguageFallback() {
        assertEquals(1, preferredTrackIndex(tracks, "b", "fr"))
        assertEquals(2, preferredTrackIndex(tracks, "missing", "fr"))
    }
    @Test fun missingPreferenceLeavesAutomaticSelection() {
        assertEquals(-1, preferredTrackIndex(tracks, null, null))
        assertEquals(-1, preferredTrackIndex(tracks, "missing", "de"))
    }
}
