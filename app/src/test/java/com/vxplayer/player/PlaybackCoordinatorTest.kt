package com.vxplayer.player
import org.junit.Assert.*
import org.junit.Test

class PlaybackCoordinatorTest {
    @Test fun backgroundAndForegroundVideoOwnersAreBothPausedAndUnregisterSafely() {
        val old=Any();val current=Any();var paused=0
        PlaybackCoordinator.registerVideo(old){paused++;PlaybackCoordinator.unregisterVideo(old)}
        PlaybackCoordinator.registerVideo(current){paused++}
        try { PlaybackCoordinator.startingMusic();assertEquals(2,paused);PlaybackCoordinator.startingVideo(current);assertEquals(2,paused) }
        finally { PlaybackCoordinator.unregisterVideo(old);PlaybackCoordinator.unregisterVideo(current) }
    }
    @Test fun startingEitherPlayerPausesTheOther() {
        var video = true; var music = false
        val owner = Any()
        PlaybackCoordinator.registerVideo(owner) { video = false }
        PlaybackCoordinator.pauseMusic = { music = false }
        try {
            PlaybackCoordinator.startingMusic(); music = true
            assertFalse(video); assertTrue(music)
            PlaybackCoordinator.startingVideo(owner); video = true
            assertFalse(music); assertTrue(video)
        } finally { PlaybackCoordinator.unregisterVideo(owner); PlaybackCoordinator.pauseMusic = null }
    }
}
