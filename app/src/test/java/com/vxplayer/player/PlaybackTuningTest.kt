package com.vxplayer.player
import org.junit.Assert.*
import org.junit.Test
class PlaybackTuningTest {
    @Test fun largeJumpsUseSyncButSmallExplicitAdjustmentsStayExact() {
        assertTrue(exactSeekForJump(true,1000,2000))
        assertFalse(exactSeekForJump(true,1000,120_000))
        assertFalse(exactSeekForJump(false,1000,2000))
        assertFalse(exactSeekForJump(true,Long.MAX_VALUE,0))
    }
    @Test fun localBuffersAreSmallerAndAllThresholdsAreValid() {
        val local=playbackBuffer(false);val network=playbackBuffer(true)
        assertTrue(local.bytes<network.bytes);assertTrue(local.maxMs<network.maxMs)
        listOf(local,network).forEach { assertTrue(it.minMs>=it.playbackMs);assertTrue(it.minMs>=it.rebufferMs);assertTrue(it.maxMs>=it.minMs) }
    }
    @Test fun localStartupWaitIsShortWithoutRemovingReadAheadOrRebufferProtection() {
        val local=playbackBuffer(false)
        assertTrue(local.playbackMs in 1..250)
        assertTrue(local.rebufferMs>=local.playbackMs)
        assertTrue(local.minMs>=8000)
        assertEquals(24*1024*1024,local.bytes)
        assertTrue(playbackBuffer(true).playbackMs>local.playbackMs)
    }
    @Test fun originalPlaybackNeverWarmsEffectsAndEnhanceWarmsOnlyOnceWhenSupported() {
        assertFalse(needsEnhancementWarmup(false,false,false,true,true,false,true))
        assertTrue(needsEnhancementWarmup(true,false,false,true,true,false,true))
        assertFalse(needsEnhancementWarmup(true,true,false,true,true,false,true))
        assertFalse(needsEnhancementWarmup(true,false,true,true,true,false,true))
        assertFalse(needsEnhancementWarmup(true,false,false,false,true,false,true))
        assertFalse(needsEnhancementWarmup(true,false,false,true,false,false,true))
        assertFalse(needsEnhancementWarmup(true,false,false,true,true,true,true))
        assertFalse(needsEnhancementWarmup(true,false,false,true,true,false,false))
    }
}
