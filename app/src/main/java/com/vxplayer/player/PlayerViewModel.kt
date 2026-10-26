package com.vxplayer.player

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class PlayerViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    // Only lightweight playback state survives rotation; the decoder and surface are released.
    fun start(uri: Uri, title: String) {
        if (player != null) return
        failed = false
        player = ExoPlayer.Builder(getApplication<Application>()).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { failed = true }
                override fun onIsPlayingChanged(playing: Boolean) { this@PlayerViewModel.isPlaying = playing }
            })
            setMediaItem(MediaItem.Builder().setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build())
            seekTo(savedState.get<Long>("position") ?: 0L)
            playWhenReady = savedState.get<Boolean>("playWhenReady") ?: true
            prepare()
        }
    }

    fun savePlaybackState() {
        player?.let {
            savedState["position"] = it.currentPosition.coerceAtLeast(0L)
            savedState["playWhenReady"] = it.playWhenReady
        }
    }

    fun stop() {
        savePlaybackState()
        val previous = player
        player = null
        previous?.release()
        isPlaying = false
    }

    override fun onCleared() { stop() }
}
