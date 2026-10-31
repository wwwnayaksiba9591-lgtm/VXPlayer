package com.vxplayer.player

data class PlaybackBuffer(val minMs: Int,val maxMs: Int,val playbackMs: Int,val rebufferMs: Int,val bytes: Int)
fun playbackBuffer(network: Boolean) = if(network)PlaybackBuffer(15_000,40_000,1500,2500,48*1024*1024)
    else PlaybackBuffer(8000,20_000,150,500,24*1024*1024)

/** Normal playback must not restart just to warm an unused enhancement pipeline. */
fun needsEnhancementWarmup(enabled: Boolean, installed: Boolean, attempted: Boolean, available: Boolean,
    foreground: Boolean, background: Boolean, supportedVideo: Boolean) =
    enabled && !installed && !attempted && available && foreground && !background && supportedVideo
fun exactSeekForJump(requestedExact: Boolean,from: Long,to: Long) = requestedExact &&
    kotlin.math.abs(to.coerceAtLeast(0)-from.coerceAtLeast(0)) < 30_000L
