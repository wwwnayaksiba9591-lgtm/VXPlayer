package com.vxplayer.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import com.vxplayer.music.VideoSessionHandoff

/** Compatibility entry point: music and background video use one foreground service. */
@androidx.annotation.OptIn(UnstableApi::class)
object BackgroundPlaybackService {
    fun begin(context: Context, player: ExoPlayer, stopped: () -> Unit) = VideoSessionHandoff.begin(context,player,stopped)
    fun detach(context: Context, player: ExoPlayer?) = VideoSessionHandoff.detach(player)
    fun replaceForRecovery(previous: ExoPlayer, replacement: ExoPlayer) = VideoSessionHandoff.replace(previous,replacement)
}
