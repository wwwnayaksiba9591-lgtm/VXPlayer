package com.vxplayer.player

import com.vxplayer.ui.*
import org.junit.Assert.*
import org.junit.Test

class ResumePolicyTest {
    @Test fun newestUnfinishedWinsOverFinishedAndOlderCheckpoints() {
        val records = listOf(ResumeVideo("done", "Done", playedAt = 30, watched = true),
            ResumeVideo("older", "Older", positionMs = 100, playedAt = 10), ResumeVideo("network", "Stream", positionMs = 200, playedAt = 20))
        assertEquals("network", selectResumeVideo(records)?.uri)
    }
    @Test fun finishedHistoryFallsBackToLatestAndUnknownDurationIsSafe() {
        assertEquals("latest", selectResumeVideo(listOf(ResumeVideo("old", "Old", playedAt = 1, watched = true),
            ResumeVideo("latest", "Latest", playedAt = 2, watched = true)))?.uri)
        assertEquals("stream", selectResumeVideo(listOf(ResumeVideo("stream", "Stream", positionMs = 900, durationMs = -1)))?.uri)
    }
    @Test fun deletedPrivateAndEmptyHistoryAreExcluded() {
        val records = listOf(ResumeVideo("private", "Private", playedAt = 30, privateVideo = true),
            ResumeVideo("deleted", "Missing", playedAt = 20, accessible = false), ResumeVideo("ok", "OK", playedAt = 10))
        assertEquals("ok", selectResumeVideo(records)?.uri)
        assertNull(selectResumeVideo(records.take(2)))
        assertNull(selectResumeVideo(emptyList()))
    }
    @Test fun replayCheckpointWinsEvenWhenPreviouslyWatched() {
        assertEquals("replay", selectResumeVideo(listOf(ResumeVideo("replay", "Replay", positionMs = 500, durationMs = 1000, playedAt = 2, watched = true),
            ResumeVideo("old", "Old", positionMs = 100, playedAt = 1)))?.uri)
    }
}
