package com.vxplayer.player
import com.vxplayer.ui.resumeCandidate
import org.junit.Assert.*
import org.junit.Test
class ResumeCandidateTest {
    @Test fun selectsMostRecentCheckpoint() { assertEquals(10L, resumeCandidate(listOf(0L, 10L, 20L)) { it }) }
    @Test fun completedHistoryFallsBackToLatest() { assertEquals(0L, resumeCandidate(listOf(0L, 0L)) { it }) }
    @Test fun emptyHistoryHasNoResumeAction() { assertNull(resumeCandidate(emptyList<Long>()) { it }) }
}
