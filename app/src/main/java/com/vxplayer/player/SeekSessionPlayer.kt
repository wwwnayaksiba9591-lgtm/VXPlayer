package com.vxplayer.player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
/** Session/headset seek commands use exactly the same policy as gestures and keyboard keys. */
@androidx.annotation.OptIn(UnstableApi::class)
class SeekSessionPlayer(private val engine: Player,private val relative: (Long)->Boolean,private val absolute: (Long)->Unit): ForwardingPlayer(engine) {
    fun withEngine(replacement: Player)=SeekSessionPlayer(replacement,relative,absolute)
    override fun seekBack(){relative(-10_000)}
    override fun seekForward(){relative(10_000)}
    override fun seekTo(positionMs: Long){absolute(positionMs)}
    override fun seekTo(mediaItemIndex: Int,positionMs: Long) {
        if(mediaItemIndex==engine.currentMediaItemIndex)absolute(positionMs) else super.seekTo(mediaItemIndex,positionMs)
    }
}
