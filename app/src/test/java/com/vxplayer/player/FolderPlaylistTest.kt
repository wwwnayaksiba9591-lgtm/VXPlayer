package com.vxplayer.player

import org.junit.Assert.*
import org.junit.Test

class FolderPlaylistTest {
    private val videos = listOf(PlaylistVideo("a", "A"), PlaylistVideo("b", "B"), PlaylistVideo("c", "C"))
    @Test fun movesInFolderOrderWithoutWrapping() {
        assertEquals("b", adjacentVideo(videos, "a", 1)?.uri)
        assertEquals("a", adjacentVideo(videos, "b", -1)?.uri)
        assertNull(adjacentVideo(videos, "a", -1))
        assertNull(adjacentVideo(videos, "c", 1))
    }
    @Test fun missingOrEmptyQueueDoesNotMove() {
        assertNull(adjacentVideo(videos, "missing", 1))
        assertNull(adjacentVideo(emptyList(), "a", 1))
    }
}
