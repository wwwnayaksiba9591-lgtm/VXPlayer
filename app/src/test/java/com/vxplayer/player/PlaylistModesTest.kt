package com.vxplayer.player

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PlaylistModesTest {
    private val videos = listOf("a", "b", "c").map { PlaylistVideo(it, it) }
    @Test fun offStopsAtEndsAndRepeatOneOnlyRepeatsOnCompletion() {
        for (mode in listOf(PlaybackMode.Off, PlaybackMode.RepeatOne)) {
            assertNull(playlistStep(videos, "a", mode, -1))
            assertNull(playlistStep(videos, "c", mode, 1))
            assertEquals("b", playlistStep(videos, "a", mode, 1)?.video?.uri)
        }
        assertEquals("c", playlistStep(videos, "c", PlaybackMode.RepeatOne, 1, atEnd = true)?.video?.uri)
    }
    @Test fun repeatFolderWrapsInBothDirections() {
        assertEquals("a", playlistStep(videos, "c", PlaybackMode.RepeatFolder, 1)?.video?.uri)
        assertEquals("c", playlistStep(videos, "a", PlaybackMode.RepeatFolder, -1)?.video?.uri)
    }
    @Test fun shuffleExhaustsFolderThenRepeatsAndRetracesHistory() {
        val random = Random(71)
        var state = PlaylistCursor()
        var current = "a"
        val played = mutableListOf(current)
        repeat(8) {
            val step = playlistStep(videos, current, PlaybackMode.Shuffle, 1, state, random = random)!!
            state = step.cursor; current = step.video.uri; played.add(current)
        }
        assertEquals(3, played.take(3).toSet().size)
        assertEquals(3, played.drop(3).take(3).toSet().size)
        assertNotEquals(played[2], played[3])
        val back = playlistStep(videos, current, PlaybackMode.Shuffle, -1, state)!!
        assertEquals(played[played.lastIndex - 1], back.video.uri)
        val forward = playlistStep(videos, back.video.uri, PlaybackMode.Shuffle, 1, back.cursor)!!
        assertEquals(current, forward.video.uri)
        assertEquals(state.remaining, forward.cursor.remaining)
    }
    @Test fun emptyAndSingleVideoFoldersAreSafeInEveryMode() {
        for (mode in PlaybackMode.entries) {
            assertNull(playlistStep(emptyList(), "a", mode, 1))
            assertNull(playlistStep(videos, "missing", mode, -1))
            val one = videos.take(1)
            val next = playlistStep(one, "a", mode, 1, atEnd = true)
            if (mode == PlaybackMode.Off) assertNull(next) else assertEquals("a", next?.video?.uri)
        }
        assertNull(playlistStep(videos, "a", PlaybackMode.Shuffle, -1))
    }
}
