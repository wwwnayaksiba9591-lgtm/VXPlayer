package com.vxplayer.player

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import com.vxplayer.settings.AppSettings
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.MimeTypes
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    val audioEnhancer = AudioEnhancer(application)
    val videoEnhancer = VideoEnhancer(application)
    var enhanceOn by mutableStateOf(false)
        private set
    private var pipActive = false
    private var repaint: Job? = null
    private var rebuilding: Job? = null
    private var effectsConfigured = false
    var notice by mutableStateOf<String?>(null)
        private set
    private val preferences = application.getSharedPreferences("vxplayer", 0)
    var subtitleDelayMs by mutableIntStateOf(0)
        private set
    var hasExternalSrt by mutableStateOf(false)
        private set
    private var preparedSubtitle: Uri? = null
    private var preparedSource: String? = null
    private var preparedDelay = 0
    private var subtitleWork: Job? = null
    var subtitleSize by mutableStateOf(preferences.getFloat("subtitleSize", 20f))
        private set
    var subtitleColor by mutableStateOf(preferences.getInt("subtitleColor", android.graphics.Color.WHITE))
        private set
    var subtitleBackground by mutableStateOf(preferences.getInt("subtitleBackground", 0x99000000.toInt()))
        private set
    fun styleSubtitles(size: Float = subtitleSize, color: Int = subtitleColor, background: Int = subtitleBackground) {
        subtitleSize = size; subtitleColor = color; subtitleBackground = background
        preferences.edit().putFloat("subtitleSize", size).putInt("subtitleColor", color).putInt("subtitleBackground", background).apply()
    }
    var backgroundEnabled by mutableStateOf(false)
        private set
    var foreground = true
    var aMs by mutableStateOf<Long?>(null)
        private set
    var bMs by mutableStateOf<Long?>(null)
        private set
    var sleepLabel by mutableStateOf("Off")
        private set
    private var sleepDeadline = 0L
    private var sleepAtEnd = false
    private var monitor: Job? = null
    private var choice: TrackPreference? = null
    private var audioRestored = false
    private var textRestored = false
    var videoSize by mutableStateOf(VideoSize.UNKNOWN)
        private set
    var tracks by mutableStateOf(Tracks.EMPTY)
        private set
    var choosingSubtitle by mutableStateOf(false)
    var activeUri by mutableStateOf(savedState.get<String>("activeUri"))
        private set
    var activeTitle by mutableStateOf(savedState.get<String>("activeTitle").orEmpty())
        private set
    var playlist by mutableStateOf<List<PlaylistVideo>>(emptyList())
        private set
    private var advance: Job? = null
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var speed by mutableStateOf(savedState.get<Float>("speed") ?: AppSettings.get(application).state.value.speed)
        private set
    private val positions = PlaybackPositionStore.get(application)
    private var starting: Job? = null
    private var checkpoint: Job? = null
    private var currentUri: String? = null

    // Only lightweight playback state survives rotation; the decoder and surface are released.
    fun start(uri: Uri, title: String, folder: String = savedState.get<String>("folder").orEmpty()) {
        if (player != null || starting?.isActive == true) return
        failed = false
        notice = null
        savedState["folder"] = folder
        val selectedUri = activeUri ?: uri.toString()
        activeUri = selectedUri
        if (activeTitle.isBlank()) activeTitle = title
        savedState["activeUri"] = selectedUri
        savedState["activeTitle"] = activeTitle
        currentUri = selectedUri
        subtitleDelayMs = preferences.getInt(subtitleDelayKey(selectedUri), 0).coerceIn(-60_000, 60_000)
        audioEnhancer.newVideo(selectedUri)
        videoEnhancer.newVideo(selectedUri)
        starting = viewModelScope.launch {
            if (playlist.isEmpty()) {
                playlist = try { withContext(Dispatchers.IO) { loadFolderPlaylist(getApplication(), folder) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                if (playlist.none { it.uri == selectedUri }) playlist = listOf(PlaylistVideo(selectedUri, activeTitle))
            }
            val position = savedState.get<Long>("position") ?: try {
                positions.load(selectedUri) ?: 0L
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                0L
            }
            choice = try { positions.loadChoice(selectedUri) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
            audioRestored = false; textRestored = false
            choice?.let { selected ->
                if (savedState.get<String>("subtitleUri") == null && selected.externalUri != null) {
                    savedState["subtitleUri"] = selected.externalUri; savedState["subtitleName"] = selected.externalName
                }
            }
            savedState.get<String>("subtitleUri")?.let { subtitle ->
                val readable = withContext(Dispatchers.IO) {
                    try { getApplication<Application>().contentResolver.openAssetFileDescriptor(Uri.parse(subtitle), "r")?.use { true } ?: false }
                    catch (_: Exception) { false }
                }
                if (!readable) {
                    savedState.remove<String>("subtitleUri"); savedState.remove<String>("subtitleName")
                    savedState["selectExternal"] = false
                    rememberChoice { it.copy(externalUri = null, externalName = null, textId = null, textLanguage = null) }
                    notice = "Saved subtitle is unavailable; playing without it."
                }
            }
            hasExternalSrt = savedState.get<String>("subtitleUri") != null
            savedState.get<String>("subtitleUri")?.let { source ->
                try {
                    preparedSubtitle = withContext(Dispatchers.IO) { ShiftedSubtitleCache.prepare(getApplication(), Uri.parse(source), subtitleDelayMs, preparedSubtitle) }
                    preparedSource = source; preparedDelay = subtitleDelayMs
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    savedState.remove<String>("subtitleUri"); hasExternalSrt = false
                    notice = "External subtitle could not be loaded. Playing without subtitles."
                }
            }
            player = ExoPlayer.Builder(getApplication<Application>()).build().apply {
                setWakeMode(C.WAKE_MODE_LOCAL)
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                setHandleAudioBecomingNoisy(true)
                savedState.get<Bundle>("trackParameters")?.let {
                    trackSelectionParameters = TrackSelectionParameters.fromBundle(it)
                }
                // Audio-only is an explicit live-session mode, never a restored video default.
                trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
                if (choice?.textDisabled == true) trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                addListener(object : Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) { audioEnhancer.attach(audioSessionId) }
                    override fun onVideoSizeChanged(size: VideoSize) { this@PlayerViewModel.videoSize = size; checkEnhancePath() }
                    override fun onTracksChanged(value: Tracks) {
                        tracks = value
                        checkEnhancePath()
                        restoreTrackChoices(value)
                        if (savedState.get<Boolean>("selectExternal") == true) {
                            value.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
                                repeat(group.length) { index ->
                                    if (group.getTrackFormat(index).id?.contains("external-srt") == true && group.isTrackSupported(index)) {
                                        savedState["selectExternal"] = false
                                        selectTrack(group, index)
                                    }
                                }
                            }
                        }
                    }
                    override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                        savedState["trackParameters"] = parameters.toBundle()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        val current = player
                        if (effectsConfigured && current != null) {
                            videoEnhancer.unavailable("Enhancement is unsupported on this video path; playing the original.")
                            enhanceOn = false
                            effectsConfigured = false
                            rebuildEnhancementPlayer(videoEnhancer.reason)
                        } else failed = true
                    }
                    override fun onIsPlayingChanged(playing: Boolean) {
                        this@PlayerViewModel.isPlaying = playing
                        if (playing) currentUri?.let { positions.recordPlayed(it, activeTitle) }
                        if (!playing) savePlaybackState()
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            savePlaybackState()
                            if (sleepAtEnd || backgroundEnabled) {
                                sleepAtEnd = false; sleepLabel = "Off"; player?.pause()
                                setBackground(false)
                                if (!foreground) stop()
                                return
                            }
                            if (AppSettings.get(getApplication()).state.value.autoNext) advance = viewModelScope.launch {
                                yield()
                                if (player?.playbackState == Player.STATE_ENDED) moveInPlaylist(1)
                            }
                        }
                    }
                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                        savePlaybackState()
                    }
                    override fun onPlaybackParametersChanged(parameters: PlaybackParameters) {
                        speed = parameters.speed
                        savedState["speed"] = parameters.speed
                    }
                })
                setMediaItem(buildMediaItem(Uri.parse(selectedUri), activeTitle))
                seekTo(position.coerceAtLeast(0L))
                setPlaybackSpeed(this@PlayerViewModel.speed)
                playWhenReady = savedState.get<Boolean>("playWhenReady") ?: true
                configureEnhancement(this, force = true)
                prepare()
            }
            val originalSubtitle = savedState.get<String>("subtitleUri")
            if (originalSubtitle != null && (preparedSource != originalSubtitle || preparedDelay != subtitleDelayMs ||
                    player?.currentMediaItem?.localConfiguration?.subtitleConfigurations?.firstOrNull()?.uri != preparedSubtitle)) {
                reloadExternalSubtitle(select = savedState.get<Boolean>("selectExternal") == true)
            }
            checkpoint = viewModelScope.launch {
                while (true) {
                    delay(5000)
                    savePlaybackState()
                }
            }
            monitor = viewModelScope.launch {
                while (true) {
                    delay(100)
                    audioEnhancer.syncSystemVolume()
                    val current = player ?: break
                    val end = bMs
                    if (aMs != null && end != null && current.isPlaying && current.currentPosition >= end) current.seekTo(requireNotNull(aMs))
                    if (sleepDeadline > 0L && SystemClock.elapsedRealtime() >= sleepDeadline) {
                        sleepDeadline = 0L; sleepLabel = "Off"; current.pause(); setBackground(false)
                        if (!foreground) { stop(); break }
                    }
                }
            }
        }
    }

    private fun checkEnhancePath() {
        val current = player ?: return
        val format = current.videoFormat ?: return
        val transfer = format.colorInfo?.colorTransfer
        if (transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG || format.drmInitData != null) {
            videoEnhancer.unavailable("Enhancement is unavailable for HDR or protected video.")
            notice = videoEnhancer.reason
            configureEnhancement(current)
        }
    }

    private fun configureEnhancement(current: ExoPlayer, force: Boolean = false) {
        videoEnhancer.updateSuspended(pipActive || backgroundEnabled)
        val wanted = shouldEnhance(videoEnhancer.enabled, videoEnhancer.values, videoEnhancer.available, pipActive, backgroundEnabled)
        if (force && !wanted) {
            // Leave untouched playback on ExoPlayer's ordinary, non-GL output path.
            effectsConfigured = false; enhanceOn = false
            return
        }
        if (!force && !effectsConfigured) {
            if (wanted) rebuildEnhancementPlayer()
            return
        }
        if (!force && wanted == enhanceOn) return
        try {
            current.setVideoEffects(if (wanted) listOf(videoEnhancer.effect) else emptyList())
            effectsConfigured = true
            enhanceOn = wanted
        } catch (_: RuntimeException) {
            enhanceOn = false
            videoEnhancer.unavailable("Enhancement is unavailable on this device; playing the original.")
            notice = videoEnhancer.reason
            try { current.setVideoEffects(emptyList()) } catch (_: RuntimeException) { }
        }
    }

    private fun rebuildEnhancementPlayer(explanation: String? = null) {
        if (rebuilding?.isActive == true) return
        val current = player ?: return
        val uri = currentUri ?: return
        val position = current.currentPosition.coerceAtLeast(0L)
        val playing = current.playWhenReady
        if (backgroundEnabled) setBackground(false)
        rebuilding = viewModelScope.launch {
            yield()
            stop()
            savedState["position"] = position
            savedState["playWhenReady"] = playing && foreground
            start(Uri.parse(uri), activeTitle)
            if (explanation != null) notice = explanation
        }
    }

    fun setPipActive(value: Boolean) { pipActive = value; player?.let { configureEnhancement(it) } }
    fun updateEnhance(value: EnhanceValues) { videoEnhancer.change(value); refreshEnhancement() }
    fun enableEnhance(enabled: Boolean) { videoEnhancer.changeEnabled(enabled); refreshEnhancement() }
    fun presetEnhance(name: String) { videoEnhancer.selectPreset(name); refreshEnhancement() }
    private fun refreshEnhancement() {
        player?.let { configureEnhancement(it) }
        repaint?.cancel()
        repaint = viewModelScope.launch {
            delay(120)
            val current = player
            if (current != null && !current.isPlaying && current.playbackState == Player.STATE_READY && foreground && !backgroundEnabled && !pipActive) {
                val target = current.currentPosition + 1L
                current.seekTo(if (current.duration > 0L) target.coerceAtMost(current.duration) else target)
            }
        }
    }

    private fun restoreTrackChoices(value: Tracks) {
        val selected = choice ?: return
        val current = player ?: return
        listOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT).forEach { type ->
            if ((type == C.TRACK_TYPE_AUDIO && audioRestored) || (type == C.TRACK_TYPE_TEXT && (textRestored || selected.textDisabled))) return@forEach
            val id = if (type == C.TRACK_TYPE_AUDIO) selected.audioId else selected.textId
            val language = if (type == C.TRACK_TYPE_AUDIO) selected.audioLanguage else selected.textLanguage
            val candidates = value.groups.filter { it.type == type }.flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { group to it } }
            val index = preferredTrackIndex(candidates.map { (group, track) -> group.getTrackFormat(track).let { it.id to it.language } }, id, language)
            if (index >= 0) {
                if (type == C.TRACK_TYPE_AUDIO) audioRestored = true else textRestored = true
                val (group, track) = candidates[index]
                current.trackSelectionParameters = current.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track)).build()
            }
        }
    }

    private fun rememberChoice(transform: (TrackPreference) -> TrackPreference) {
        val uri = currentUri ?: return
        choice = transform(choice ?: TrackPreference(uri))
        positions.saveChoice(requireNotNull(choice))
    }

    fun setSleep(minutes: Int) {
        sleepAtEnd = minutes == -1
        sleepDeadline = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else 0L
        sleepLabel = when { minutes == -1 -> "End of video"; minutes > 0 -> "$minutes min"; else -> "Off" }
    }

    fun setA() { aMs = player?.currentPosition; bMs = null }
    fun setB(): Boolean {
        val start = aMs ?: return false
        val end = player?.currentPosition ?: return false
        if (end <= start + 500L) return false
        bMs = end; return true
    }
    fun clearRepeat() { aMs = null; bMs = null }
    fun onHidden() { foreground = false; if (!backgroundEnabled) stop() else savePlaybackState() }
    fun close() { setBackground(false); stop() }

    fun setBackground(enabled: Boolean): Boolean {
        val current = player ?: return false
        if (enabled) {
            current.play()
            if (!BackgroundPlaybackService.begin(getApplication(), current) { backgroundEnabled = false; stop() }) return false
        } else BackgroundPlaybackService.detach(getApplication(), current)
        backgroundEnabled = enabled
        configureEnhancement(current)
        current.trackSelectionParameters = current.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, enabled).build()
        return true
    }

    fun moveInPlaylist(offset: Int) {
        val next = adjacentVideo(playlist, activeUri, offset) ?: return
        setBackground(false)
        stop()
        clearRepeat()
        savedState.remove<Long>("position")
        savedState["playWhenReady"] = true
        savedState.remove<Bundle>("trackParameters")
        savedState.remove<String>("subtitleUri")
        savedState.remove<String>("subtitleName")
        savedState.remove<Boolean>("selectExternal")
        hasExternalSrt = false; preparedSubtitle = null; preparedSource = null
        tracks = Tracks.EMPTY
        activeUri = next.uri
        activeTitle = next.title
        start(Uri.parse(next.uri), next.title)
    }

    private fun buildMediaItem(uri: Uri, title: String): MediaItem {
        val builder = MediaItem.Builder().setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        savedState.get<String>("subtitleUri")?.let { subtitle ->
            val actual = if (preparedSource == subtitle && preparedDelay == subtitleDelayMs) preparedSubtitle ?: Uri.parse(subtitle) else Uri.parse(subtitle)
            builder.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(actual)
                .setMimeType(MimeTypes.APPLICATION_SUBRIP).setId("external-srt")
                .setLabel(savedState.get<String>("subtitleName") ?: "SRT")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
        }
        return builder.build()
    }

    fun selectTrack(group: Tracks.Group, index: Int) {
        val current = player ?: return
        if (!group.isTrackSupported(index)) return
        val format = group.getTrackFormat(index)
        if (group.type == C.TRACK_TYPE_AUDIO) { audioRestored = true; rememberChoice { it.copy(audioId = format.id, audioLanguage = format.language) } }
        if (group.type == C.TRACK_TYPE_TEXT) { textRestored = true; rememberChoice { it.copy(textId = format.id, textLanguage = format.language, textDisabled = false) } }
        if (group.type == C.TRACK_TYPE_TEXT) savedState["selectExternal"] = false
        current.trackSelectionParameters = current.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(group.type, false).clearOverridesOfType(group.type)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index)).build()
    }

    fun setTrackType(type: Int, enabled: Boolean) {
        if (type == C.TRACK_TYPE_AUDIO) { audioRestored = true; rememberChoice { it.copy(audioId = null, audioLanguage = null) } }
        if (type == C.TRACK_TYPE_TEXT) { textRestored = true; rememberChoice { it.copy(textDisabled = !enabled, textId = null, textLanguage = null) } }
        if (type == C.TRACK_TYPE_TEXT) savedState["selectExternal"] = false
        val current = player ?: return
        current.trackSelectionParameters = current.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(type).setTrackTypeDisabled(type, !enabled).build()
    }

    fun loadSubtitle(uri: Uri, name: String) {
        savedState["subtitleUri"] = uri.toString()
        savedState["subtitleName"] = name
        hasExternalSrt = true
        textRestored = true
        rememberChoice { it.copy(externalUri = uri.toString(), externalName = name, textDisabled = false, textId = "external-srt", textLanguage = null) }
        val parameters = player?.trackSelectionParameters ?: savedState.get<Bundle>("trackParameters")?.let {
            TrackSelectionParameters.fromBundle(it)
        } ?: TrackSelectionParameters.Builder(getApplication<Application>()).build()
        val updated = parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        savedState["trackParameters"] = updated.toBundle()
        savedState["selectExternal"] = true
        player?.trackSelectionParameters = updated
        reloadExternalSubtitle(select = true)
    }

    fun changeSubtitleDelay(delta: Int, reset: Boolean = false) {
        if (!hasExternalSrt) { notice = "Delay applies only to an external SRT file."; return }
        subtitleDelayMs = if (reset) 0 else (subtitleDelayMs + delta).coerceIn(-60_000, 60_000)
        currentUri?.let { preferences.edit().putInt(subtitleDelayKey(it), subtitleDelayMs).apply() }
        reloadExternalSubtitle(select = false)
    }

    private fun reloadExternalSubtitle(select: Boolean) {
        subtitleWork?.cancel()
        val source = savedState.get<String>("subtitleUri") ?: return
        val video = currentUri ?: return
        val offset = subtitleDelayMs
        subtitleWork = viewModelScope.launch {
            delay(120)
            try {
                val cached = withContext(Dispatchers.IO) { ShiftedSubtitleCache.prepare(getApplication(), Uri.parse(source), offset, preparedSubtitle) }
                if (video != currentUri) return@launch
                preparedSubtitle = cached; preparedSource = source; preparedDelay = offset
                val current = player ?: return@launch
                val position = current.currentPosition; val playing = current.playWhenReady
                val externalSelected = current.currentTracks.groups.any { group -> group.type == C.TRACK_TYPE_TEXT && (0 until group.length).any { group.isTrackSelected(it) && group.getTrackFormat(it).id?.contains("external-srt") == true } }
                savedState["selectExternal"] = select || externalSelected
                current.setMediaItem(buildMediaItem(Uri.parse(video), activeTitle), position)
                current.prepare(); current.playWhenReady = playing
                notice = "External SRT delay: ${if (offset >= 0) "+" else ""}$offset ms"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice = "Could not load external SRT. Check the file and its access permission." }
        }
    }

    fun savePlaybackState() {
        player?.let {
            val position = if (it.playbackState == Player.STATE_ENDED) 0L else it.currentPosition.coerceAtLeast(0L)
            savedState["position"] = position
            savedState["playWhenReady"] = it.playWhenReady && it.playbackState != Player.STATE_ENDED
            currentUri?.let { uri -> positions.save(uri, position) }
            if (it.playbackState == Player.STATE_ENDED) currentUri?.let { uri -> positions.finished(uri) }
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

    fun seekTo(position: Long) {
        val current = player ?: return
        if (!current.isCurrentMediaItemSeekable) return
        current.seekTo(position.coerceIn(0L, if (current.duration > 0) current.duration else Long.MAX_VALUE))
        savePlaybackState()
    }

    fun stop() {
        subtitleWork?.cancel(); subtitleWork = null
        rebuilding?.cancel(); rebuilding = null
        effectsConfigured = false
        repaint?.cancel(); repaint = null
        enhanceOn = false
        audioEnhancer.release()
        monitor?.cancel(); monitor = null
        advance?.cancel()
        advance = null
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

    override fun onCleared() { close() }

    companion object {
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    }
}
