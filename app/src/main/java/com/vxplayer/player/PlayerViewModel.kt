package com.vxplayer.player

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    var tracks by mutableStateOf(Tracks.EMPTY)
        private set
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var speed by mutableStateOf(savedState.get<Float>("speed") ?: 1f)
        private set
    private val positions = PlaybackPositionStore.get(application)
    private var starting: Job? = null
    private var checkpoint: Job? = null
    private var currentUri: String? = null

    // Only lightweight playback state survives rotation; the decoder and surface are released.
    fun start(uri: Uri, title: String) {
        if (player != null || starting?.isActive == true) return
        failed = false
        currentUri = uri.toString()
        starting = viewModelScope.launch {
            val position = savedState.get<Long>("position") ?: try {
                positions.load(uri.toString()) ?: 0L
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                0L
            }
            player = ExoPlayer.Builder(getApplication<Application>()).build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                setHandleAudioBecomingNoisy(true)
                savedState.get<Bundle>("trackParameters")?.let {
                    trackSelectionParameters = TrackSelectionParameters.fromBundle(it)
                }
                addListener(object : Player.Listener {
                    override fun onTracksChanged(value: Tracks) { tracks = value }
                    override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                        savedState["trackParameters"] = parameters.toBundle()
                    }
                    override fun onPlayerError(error: PlaybackException) { failed = true }
                    override fun onIsPlayingChanged(playing: Boolean) {
                        this@PlayerViewModel.isPlaying = playing
                        if (!playing) savePlaybackState()
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) savePlaybackState()
                    }
                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                        savePlaybackState()
                    }
                    override fun onPlaybackParametersChanged(parameters: PlaybackParameters) {
                        speed = parameters.speed
                        savedState["speed"] = parameters.speed
                    }
                })
                setMediaItem(buildMediaItem(uri, title))
                seekTo(position.coerceAtLeast(0L))
                setPlaybackSpeed(this@PlayerViewModel.speed)
                playWhenReady = savedState.get<Boolean>("playWhenReady") ?: true
                prepare()
            }
            checkpoint = viewModelScope.launch {
                while (true) {
                    delay(5000)
                    savePlaybackState()
                }
            }
        }
    }

    private fun buildMediaItem(uri: Uri, title: String): MediaItem {
        val builder = MediaItem.Builder().setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        savedState.get<String>("subtitleUri")?.let { subtitle ->
            builder.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle))
                .setMimeType(MimeTypes.APPLICATION_SUBRIP).setId("external-srt")
                .setLabel(savedState.get<String>("subtitleName") ?: "SRT")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
        }
        return builder.build()
    }

    fun selectTrack(group: Tracks.Group, index: Int) {
        val current = player ?: return
        if (!group.isTrackSupported(index)) return
        current.trackSelectionParameters = current.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(group.type, false).clearOverridesOfType(group.type)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index)).build()
    }

    fun setTrackType(type: Int, enabled: Boolean) {
        val current = player ?: return
        current.trackSelectionParameters = current.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(type).setTrackTypeDisabled(type, !enabled).build()
    }

    fun loadSubtitle(uri: Uri, name: String) {
        savedState["subtitleUri"] = uri.toString()
        savedState["subtitleName"] = name
        val current = player ?: return
        val media = current.currentMediaItem ?: return
        val position = current.currentPosition
        val playing = current.playWhenReady
        setTrackType(C.TRACK_TYPE_TEXT, true)
        current.setMediaItem(buildMediaItem(Uri.parse(currentUri), media.mediaMetadata.title.toString()), position)
        current.prepare()
        current.playWhenReady = playing
    }

    fun savePlaybackState() {
        player?.let {
            val position = if (it.playbackState == Player.STATE_ENDED) 0L else it.currentPosition.coerceAtLeast(0L)
            savedState["position"] = position
            savedState["playWhenReady"] = it.playWhenReady && it.playbackState != Player.STATE_ENDED
            currentUri?.let { uri -> positions.save(uri, position) }
        }
    }

    fun changeSpeed(value: Float) {
        if (value !in SPEEDS) return
        speed = value
        savedState["speed"] = value
        player?.setPlaybackSpeed(value)
    }

    fun seekBy(offsetMs: Long): Boolean {
        val current = player ?: return false
        if (!current.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) || !current.isCurrentMediaItemSeekable) return false
        val duration = current.duration
        val target = (current.currentPosition + offsetMs).coerceAtLeast(0L)
        current.seekTo(if (duration != C.TIME_UNSET) target.coerceAtMost(duration.coerceAtLeast(0L)) else target)
        savePlaybackState()
        return true
    }

    fun stop() {
        starting?.cancel()
        starting = null
        checkpoint?.cancel()
        checkpoint = null
        savePlaybackState()
        val previous = player
        player = null
        previous?.release()
        isPlaying = false
    }

    override fun onCleared() { stop() }

    companion object {
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    }
}
