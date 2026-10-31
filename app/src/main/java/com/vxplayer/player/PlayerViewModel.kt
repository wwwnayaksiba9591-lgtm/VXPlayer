package com.vxplayer.player

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import com.vxplayer.settings.AppSettings
import com.vxplayer.privatefiles.PrivateRepository
import com.vxplayer.privatefiles.PrivateAccess
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.HttpDataSource
import com.vxplayer.library.StreamPageProbe
import com.vxplayer.library.userMediaSessionAllowed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.async

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
    private val diagnostics = PlaybackDiagnostics()
    private fun seekMessage(message: String) {notice=message;android.widget.Toast.makeText(getApplication(),message,android.widget.Toast.LENGTH_SHORT).show()}
    private val seekScope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+Dispatchers.Main.immediate)
    private var castBridge: com.vxplayer.cast.CastBridge? = null
    var casting by mutableStateOf(false); private set
    val playback: Player? get() = if(casting && castBridge?.ownedBy(this)==true)castBridge?.player else player
    fun prepareCast() {
        if(castBridge?.ownedBy(this)==true || isPrivatePlayback)return
        runCatching {
            castBridge=com.vxplayer.cast.CastBridge.obtain(this,getApplication(),{player},{active ->
                casting=active
                if(active)castBridge?.player?.let {isPlaying=it.isPlaying;buffering=it.playbackState==Player.STATE_BUFFERING}
            },{position,playing ->
                casting=false;savedState["position"]=position
                currentUri?.let {positions.save(it,position)}
                player?.let {it.seekTo(position);it.playWhenReady=playing && foreground}
            })
            casting=castBridge?.active==true
            if(casting)castBridge?.player?.let {isPlaying=it.isPlaying;buffering=it.playbackState !in listOf(Player.STATE_READY,Player.STATE_ENDED)}
        }.onFailure {notice="Cast is unavailable. Use Cast screen in playback options."}
    }
    var softwareDecoding by mutableStateOf(application.getSharedPreferences("vxplayer",0).all["softwareDecoding"] as? Boolean ?: false)
        private set
    private var decoderRetried = false
    fun toggleDecoder() {
        softwareDecoding=!softwareDecoding; decoderRetried=false
        preferences.edit().putBoolean("softwareDecoding",softwareDecoding).apply()
        rebuildEnhancementPlayer(if(softwareDecoding)"Software decoding" else "Hardware decoding preferred")
    }
    private var sourceFormat: androidx.media3.common.Format? = null
    private var formatUri: String? = null
    private var warmingAttempted = false
    private var effectsDisabled by mutableStateOf(false)
    val enhancementUnavailable get() = effectsDisabled || sourceFormat?.let(::videoEffectsUnsafe) == true
    var audioWarning by mutableStateOf<String?>(null)
        private set
    var subtitleEncoding by mutableStateOf("Auto")
        private set
    private var pendingBackgroundReplacement: ExoPlayer? = null
    private var sourceSize = 0L
    private var muteProblemAudio = false
    private val failedAudioFormats = mutableSetOf<String>()
    private fun audioFormatKey(format: androidx.media3.common.Format) = "${format.sampleMimeType}|${format.codecs}|${format.channelCount}|${format.sampleRate}"
    private fun effectsKey(uri: String) = "effectsDisabled:" + subtitleDelayKey(uri).substringAfter(':')
    var notice by mutableStateOf<String?>(null)
        private set
    private val preferences = application.getSharedPreferences("vxplayer", 0)
    var subtitleDelayMs by mutableIntStateOf(0)
        private set
    var hasExternalSrt by mutableStateOf(false)
        private set
    val canDelayExternalSubtitle: Boolean
        get() = hasExternalSrt && !tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_TEXT && (0 until group.length).any { index ->
                group.isTrackSelected(index) && group.getTrackFormat(index).id?.contains("external-srt") != true
            }
        }
    private var preparedSubtitle: Uri? = null
    private var preparedSource: String? = null
    private var preparedDelay = 0
    private var subtitleWork: Job? = null
    var subtitleSize by mutableStateOf(preferences.getFloat("subtitleSize", 20f))
        private set
    var subtitleColor by mutableStateOf(preferences.all["subtitleColor"] as? Int ?: android.graphics.Color.WHITE)
        private set
    var subtitleBackground by mutableStateOf(preferences.all["subtitleBackground"] as? Int ?: 0x99000000.toInt())
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
    var playbackMode by mutableStateOf(AppSettings.get(application).state.value.playbackMode)
        private set
    private var playlistCursor by mutableStateOf(PlaylistCursor(
        savedState.get<ArrayList<String>>("shuffleHistory").orEmpty(), savedState.get<Int>("shuffleIndex") ?: 0,
        savedState.get<ArrayList<String>>("shuffleRemaining").orEmpty()))
    fun cyclePlaybackMode() {
        selectPlaybackMode(playbackMode.next())
    }
    fun selectPlaybackMode(mode: PlaybackMode) {
        playbackMode = mode
        AppSettings.get(getApplication()).update { it.copy(playbackMode = playbackMode) }
        playlistCursor = PlaylistCursor()
        savePlaylistCursor()
    }
    private fun savePlaylistCursor() {
        savedState["shuffleHistory"] = ArrayList(playlistCursor.history)
        savedState["shuffleIndex"] = playlistCursor.index
        savedState["shuffleRemaining"] = ArrayList(playlistCursor.remaining)
    }
    fun canMoveInPlaylist(offset: Int) = playlistStep(playlist, activeUri, playbackMode, offset, playlistCursor) != null
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    var failureMessage by mutableStateOf("Could not play this video.")
        private set
    fun retryPlayback() {
        failed = false
        player?.prepare()
        player?.play()
    }
    fun playWithoutEnhance() {
        disableEffectsForVideo("Enhancement is unavailable for this video; playing without effects.")
        if (player != null) rebuildEnhancementPlayer(videoEnhancer.reason)
    }
    private fun disableEffectsForVideo(reason: String) {
        effectsDisabled = true
        currentUri?.let { preferences.edit().putBoolean(effectsKey(it), true).apply() }
        videoEnhancer.unavailable(reason)
        audioEnhancer.blockEffects(true)
        enhanceOn = false
    }
    fun problemReport(): String = diagnostics.report(activeTitle, currentUri?.startsWith("http", true) == true,
        isPrivatePlayback, sourceSize, sourceFormat ?: player?.videoFormat, tracks,
        "video=$effectsConfigured, audio=${audioEnhancer.effectsInUse}, blocked=$effectsDisabled, sampling=${videoEnhancer.enabled && videoEnhancer.available && videoEnhancer.lastMode == "Auto"}")
    fun changeSubtitleEncoding(value: String) {
        if (value !in SubtitleText.encodings) return
        subtitleEncoding = value
        currentUri?.let { preferences.edit().putString("srtEncoding:${subtitleDelayKey(it)}", value).apply() }
        reloadExternalSubtitle(select = false)
    }
    var isPlaying by mutableStateOf(false)
        private set
    var muted by mutableStateOf(false)
        private set
    private var audibleVolume = 1f
    fun toggleMute() {
        val engine = player ?: return
        if (engine.volume > 0) { audibleVolume = engine.volume; engine.volume = 0f }
        else engine.volume = audibleVolume.takeIf { it > 0 } ?: 1f
        muted = engine.volume == 0f
    }
    var speed by mutableStateOf(savedState.get<Float>("speed") ?: AppSettings.get(application).state.value.speed)
        private set
    private val positions = PlaybackPositionStore.get(application)
    private var starting: Job? = null
    private var checkpoint: Job? = null
    private val seekRequests = SeekRequests()
    private var seekWork: Job? = null
    private var seekWatchdog: Job? = null
    private var seekSave: Job? = null
    private var recoverySeek: Long? = null
    var seekDragging by mutableStateOf(false)
        private set
    var seeking by mutableStateOf(false)
        private set
    fun changeSeekDragging(value: Boolean) {
        seekDragging = value
        if(value)player?.setSeekParameters(SeekParameters.CLOSEST_SYNC)
    }
    private var currentUri: String? = null
    private var privateId: String? = null
    private val pauseForMusic: () -> Unit = {savedState["playWhenReady"] = false; player?.pause(); if (backgroundEnabled) setBackground(false); if (!foreground) stop() }
    val isPrivatePlayback: Boolean get() = privateId != null
    private val privatePositions = application.getSharedPreferences("private-playback", 0)

    // Only lightweight playback state survives rotation; the decoder and surface are released.
    fun start(uri: Uri, title: String, folder: String = savedState.get<String>("folder").orEmpty()) {
        PlaybackCoordinator.registerVideo(this,pauseForMusic)
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
        if (formatUri != selectedUri) {
            softwareDecoding=preferences.all["softwareDecoding"] as? Boolean ?: false;decoderRetried=false
            formatUri = selectedUri; sourceFormat = null; warmingAttempted = false; diagnostics.reset(); sourceSize = 0
            muteProblemAudio = false; failedAudioFormats.clear()
        }
        if (!muteProblemAudio) audioWarning = null
        effectsDisabled = preferences.all[effectsKey(selectedUri)] as? Boolean ?: false
        audioEnhancer.blockEffects(effectsDisabled)
        privateId = PrivateRepository.privateId(getApplication(), Uri.parse(selectedUri))
        if (isPrivatePlayback && !PrivateAccess.unlocked) {
            failed = true; failureMessage = "Unlock Private to play this video."; return
        }
        subtitleDelayMs = (preferences.all[subtitleDelayKey(selectedUri)] as? Int ?: 0).coerceIn(-60_000, 60_000)
        audioEnhancer.newVideo(selectedUri)
        videoEnhancer.newVideo(selectedUri)
        if (effectsDisabled) videoEnhancer.unavailable("Enhancement is unavailable for this video after a playback problem.")
        subtitleEncoding = (preferences.all["srtEncoding:${subtitleDelayKey(selectedUri)}"] as? String)?.takeIf { it in SubtitleText.encodings } ?: "Auto"
        starting = viewModelScope.launch(kotlinx.coroutines.CoroutineExceptionHandler {_,_->
            failed=true;buffering=false;isPlaying=false
            failureMessage="Could not initialize playback. Retry or choose another decoder."
            val current=player;player=null
            runCatching {BackgroundPlaybackService.detach(getApplication(),current)}
            runCatching {current?.release()}
        }) {
            // Folder scans run alongside preparation; the first frame does not depend on the playlist.
            val folderLoad: kotlinx.coroutines.Deferred<List<PlaylistVideo>>? = if (playlist.isEmpty()) async(Dispatchers.IO) {
                try { loadFolderPlaylist(getApplication(), folder) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            } else null
            if (!selectedUri.startsWith("http", true)) sourceSize = withContext(Dispatchers.IO) {
                runCatching { getApplication<Application>().contentResolver.openFileDescriptor(Uri.parse(selectedUri), "r")?.use { it.statSize.coerceAtLeast(0) } ?: 0L }.getOrDefault(0L)
            }
            val resumeAlias = if (!isPrivatePlayback && !selectedUri.startsWith("http", true)) withContext(Dispatchers.IO) {
                com.vxplayer.library.legacyResumeAlias(getApplication(), Uri.parse(selectedUri))
            } else null
            val position = savedState.get<Long>("position") ?: try {
                privateId?.let { (privatePositions.all[it] as? Number)?.toLong()?.coerceAtLeast(0) ?: 0L } ?: positions.load(selectedUri) ?: resumeAlias?.let { alias ->
                    positions.load(alias)?.also { old -> positions.save(selectedUri, old); positions.save(alias, 0) }
                } ?: 0L
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                0L
            }
            choice = try { positions.loadChoice(selectedUri) ?: resumeAlias?.let { positions.loadChoice(it)?.copy(uri = selectedUri) } }
                catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
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
                    preparedSubtitle = withContext(Dispatchers.IO) { ShiftedSubtitleCache.prepare(getApplication(), Uri.parse(source), subtitleDelayMs, preparedSubtitle, subtitleEncoding) }
                    preparedSource = source; preparedDelay = subtitleDelayMs
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    savedState.remove<String>("subtitleUri"); hasExternalSrt = false
                    notice = "External subtitle could not be loaded. Playing without subtitles."
                }
            }
            val networkApproved = userMediaSessionAllowed(selectedUri, preferences.contains("streamType:$selectedUri"))
            val network = selectedUri.startsWith("http",true)
            val sourceFactory = withContext(Dispatchers.IO) {
                val stored=preferences.all
                directMediaFactory(getApplication(),networkApproved,buildMap {
                    (stored["mediaReferer:$selectedUri"] as? String)?.let {put("Referer",it)}
                    (stored["mediaUserAgent:$selectedUri"] as? String)?.let {put("User-Agent",it)}
                })
            }
            val buffer = playbackBuffer(network)
            val renderers = object : DefaultRenderersFactory(getApplication<Application>()) {
                override fun getCodecAdapterFactory(): androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Factory =
                    diagnostics.trackAdapters(super.getCodecAdapterFactory())
            }.setEnableDecoderFallback(true).setMediaCodecSelector(androidx.media3.exoplayer.mediacodec.MediaCodecSelector { mime,secure,tunnel ->
                val candidates=diagnostics.selector.getDecoderInfos(mime,secure,tunnel)
                if(softwareDecoding && mime.startsWith("video/"))candidates.filter {it.softwareOnly}
                else candidates.sortedBy {if(it.hardwareAccelerated)0 else 1}
            })
            player = ExoPlayer.Builder(getApplication<Application>(), renderers)
                .setUsePlatformDiagnostics(false)
                .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000)
                .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(buffer.minMs,buffer.maxMs,buffer.playbackMs,buffer.rebufferMs)
                    .setTargetBufferBytes(buffer.bytes).setPrioritizeTimeOverSizeThresholds(false)
                    .setBackBuffer(10_000,true).build())
                .setMediaSourceFactory(DefaultMediaSourceFactory(sourceFactory,androidx.media3.extractor.DefaultExtractorsFactory()
                    .setConstantBitrateSeekingEnabled(true).setConstantBitrateSeekingAlwaysEnabled(true))
                    .setLoadErrorHandlingPolicy(DirectMediaLoadErrorPolicy()))
                .build().apply {
                val owner = this
                addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                    override fun onVideoDecoderInitialized(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,decoderName: String,initializedTimestampMs: Long,initializationDurationMs: Long) {
                        if(player===owner && diagnostics.isSoftwareDecoder(decoderName) && !softwareDecoding) {
                            softwareDecoding=true;notice="Hardware decoder unavailable. Using software decoding."
                        }
                    }
                })
                addAnalyticsListener(diagnostics.listener)
                setWakeMode(C.WAKE_MODE_LOCAL)
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                setHandleAudioBecomingNoisy(true)
                savedState.get<Bundle>("trackParameters")?.let {
                    trackSelectionParameters = TrackSelectionParameters.fromBundle(it)
                }
                // Audio-only is an explicit live-session mode, never a restored video default.
                trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, backgroundEnabled).build()
                if (muteProblemAudio) trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
                if (choice?.textDisabled == true) trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                addListener(object : Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        if (player !== owner) return
                        audioEnhancer.attach(audioSessionId)
                        player?.let { configureEnhancement(it) }
                    }
                    override fun onVideoSizeChanged(size: VideoSize) { if (player === owner) { this@PlayerViewModel.videoSize = size; checkEnhancePath() } }
                    override fun onTracksChanged(value: Tracks) {
                        if (player !== owner) return
                        tracks = value
                        checkEnhancePath()
                        restoreTrackChoices(value)
                        ensureSupportedAudio(value)
                        val videoGroups = value.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                        if (!backgroundEnabled && videoGroups.isNotEmpty() && videoGroups.none { group -> (0 until group.length).any { group.isTrackSupported(it, true) } }) {
                            if(softwareDecoding && !decoderRetried) {
                                decoderRetried=true;softwareDecoding=false
                                rebuildEnhancementPlayer("Software decoder cannot handle this video. Trying hardware decoding.")
                                return
                            }
                            failed = true
                            failureMessage = "Video format is not supported on this device: ${videoGroups.first().getTrackFormat(0).let(::formatDetails)}"
                            diagnostics.capabilityFailure(videoGroups.first().getTrackFormat(0))
                            owner.pause()
                        }
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
                        if (player !== owner) return
                        // Audio-only backgrounding is temporary, not a saved video-track preference.
                        savedState["trackParameters"] = parameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,false).build().toBundle()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        if (player !== owner) return
                        if(casting) {owner.pause();return}
                        if(softwareDecoding && !decoderRetried && error.errorCode in 4000..4999) {
                            decoderRetried=true;softwareDecoding=false
                            rebuildEnhancementPlayer("Software decoder unavailable. Trying hardware decoding.")
                            return
                        }
                        val current = player
                        val usedEffects = effectsConfigured || audioEnhancer.effectsInUse
                        diagnostics.record(error, usedEffects)
                        val causes = generateSequence<Throwable>(error) { it.cause }.take(20).toList()
                        val failedFormat = (error as? androidx.media3.exoplayer.ExoPlaybackException)?.rendererFormat
                        if (shouldRetryWithoutEffects(usedEffects, effectsDisabled)) {
                            disableEffectsForVideo("Enhancement is unavailable for this video after a playback error. Playing without effects.")
                            rebuildEnhancementPlayer(videoEnhancer.reason)
                        } else if (failedFormat?.sampleMimeType?.startsWith("audio/") == true && !muteProblemAudio) {
                            failedAudioFormats.add(audioFormatKey(failedFormat))
                            val alternative = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.any { group ->
                                (0 until group.length).any { group.isTrackSupported(it, false) && audioFormatKey(group.getTrackFormat(it)) !in failedAudioFormats }
                            }
                            if (!alternative || failedAudioFormats.size >= 8) {
                                muteProblemAudio = true
                                audioWarning = "Audio format ${failedFormat.sampleMimeType} is not supported on this device"
                                owner.trackSelectionParameters = owner.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
                            } else ensureSupportedAudio(tracks)
                            rebuildEnhancementPlayer(if (muteProblemAudio) audioWarning else "Trying another supported audio track.")
                        } else if (hasExternalSrt && (causes.any { it.javaClass.simpleName.contains("Subtitle", true) } ||
                                (error as? androidx.media3.exoplayer.ExoPlaybackException)?.rendererFormat?.sampleMimeType?.let(MimeTypes::isText) == true)) {
                            savedState.remove<String>("subtitleUri"); savedState.remove<String>("subtitleName")
                            hasExternalSrt = false; preparedSubtitle = null; preparedSource = null
                            rememberChoice { it.copy(externalUri = null, externalName = null, textId = null, textDisabled = true) }
                            rebuildEnhancementPlayer("Subtitle failed; playing the video without external subtitles.")
                        } else if (causes.any { it is HttpDataSource.InvalidContentTypeException && StreamPageProbe.isHtml(it.contentType) }) {
                            failureMessage = StreamPageProbe.HTML_MESSAGE
                            failed = true; current?.pause()
                        } else {
                            val httpError = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                            failureMessage = if (httpError != null) com.vxplayer.network.streamErrorMessage(status = httpError.responseCode) else if (error.errorCode in 4000..4999) {
                                val format = (error as? androidx.media3.exoplayer.ExoPlaybackException)?.rendererFormat ?: sourceFormat
                                "The device could not decode this format after decoder fallback. ${format?.let(::formatDetails) ?: "Codec details are available in Copy report."}"
                            } else if (selectedUri.startsWith("http", true)) when (error.errorCode) {
                                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Could not connect. Check your connection and retry."
                                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The server rejected this link. It may have expired or need authentication."
                                else -> "Cannot play this stream. Check the URL, stream type, and media format."
                            } else "Could not play this video. The file may be unavailable or unsupported."
                            failed = true
                        }
                    }
                    override fun onIsPlayingChanged(playing: Boolean) {
                        if (player !== owner) return
                        if(casting)return
                        if (playing) PlaybackCoordinator.startingVideo(this@PlayerViewModel)
                        this@PlayerViewModel.isPlaying = playing
                        if (playing && !isPrivatePlayback) currentUri?.let { positions.recordPlayed(it, activeTitle) }
                        if (!playing) savePlaybackState()
                    }
                    override fun onVolumeChanged(volume: Float) { if (player === owner) muted = volume == 0f }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (player !== owner) return
                        if(casting)return
                        buffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_ENDED) {
                            savePlaybackState()
                            if (sleepAtEnd || backgroundEnabled) {
                                sleepAtEnd = false; sleepLabel = "Off"; player?.pause()
                                setBackground(false)
                                if (!foreground) stop()
                                return
                            }
                            if (playbackMode != PlaybackMode.Off || AppSettings.get(getApplication()).state.value.autoNext) advance = viewModelScope.launch {
                                yield()
                                if (player?.playbackState == Player.STATE_ENDED) moveInPlaylist(1, atEnd = true)
                            }
                        }
                    }
                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                        if (player !== owner) return
                        savePlaybackState()
                    }
                    override fun onPlaybackParametersChanged(parameters: PlaybackParameters) {
                        if (player !== owner) return
                        speed = parameters.speed
                        savedState["speed"] = parameters.speed
                    }
                })
                setMediaItem(buildMediaItem(Uri.parse(selectedUri), activeTitle))
                seekTo(position.coerceAtLeast(0L))
                setSeekParameters(if (preferSyncSeek(sourceFormat?.containerMimeType, activeTitle)) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
                setPlaybackSpeed(this@PlayerViewModel.speed)
                volume = if(this@PlayerViewModel.muted)0f else audibleVolume
                playWhenReady = !casting && pendingBackgroundReplacement == null && (savedState.get<Boolean>("playWhenReady") ?: true)
                configureEnhancement(this, force = true)
                prepare()
            }
            pendingBackgroundReplacement?.let { previous ->
                pendingBackgroundReplacement = null
                val replacement = requireNotNull(player)
                if (BackgroundPlaybackService.replaceForRecovery(previous, replacement)) {
                    replacement.playWhenReady = savedState.get<Boolean>("playWhenReady") ?: true
                } else {
                    backgroundEnabled = false; replacement.pause()
                    BackgroundPlaybackService.detach(getApplication(), previous)
                    notice = "Background recovery paused. Return to VXPlayer to resume."
                }
                previous.release()
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
                    delay(250)
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
            recoverySeek?.let { target ->
                recoverySeek = null
                seekWork = viewModelScope.launch {
                    repeat(24) {
                        if (player?.isCurrentMediaItemSeekable == true) { seekTo(target); return@launch }
                        delay(250)
                    }
                    failed = true; failureMessage = "Playback could not recover after seeking. Retry this video."
                }
            }
            folderLoad?.await()?.let { loaded ->
                playlist = if (loaded.any { it.uri == selectedUri }) loaded else listOf(PlaylistVideo(selectedUri, activeTitle))
                // A short clip can end before a large folder scan completes.
                if (player?.playbackState == Player.STATE_ENDED && advance?.isActive != true &&
                    (playbackMode != PlaybackMode.Off || AppSettings.get(getApplication()).state.value.autoNext)) {
                    advance = viewModelScope.launch {
                        yield()
                        if (activeUri == selectedUri && player?.playbackState == Player.STATE_ENDED) moveInPlaylist(1,atEnd=true)
                    }
                }
            }
        }
    }

    private fun checkEnhancePath() {
        val current = player ?: return
        val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        val format = current.videoFormat ?: videoGroups.firstOrNull()?.getTrackFormat(0) ?: return
        sourceFormat = format
        current.setSeekParameters(if (preferSyncSeek(format.containerMimeType, activeTitle)) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
        if (videoEffectsUnsafe(format)) {
            videoEnhancer.unavailable("Smart Enhance is unavailable for HDR, 10-bit or protected video.")
            audioEnhancer.smartProfile(null, false)
            if (effectsConfigured && !effectsDisabled) {
                disableEffectsForVideo(videoEnhancer.reason.orEmpty())
                rebuildEnhancementPlayer(videoEnhancer.reason)
            }
        } else if (needsEnhancementWarmup(videoEnhancer.enabled,effectsConfigured,warmingAttempted,
            videoEnhancer.available && !effectsDisabled,foreground,backgroundEnabled,
            videoGroups.any { group -> (0 until group.length).any { group.isTrackSupported(it, true) } })) {
            warmingAttempted = true
            rebuildEnhancementPlayer()
        }
        configureEnhancement(current)
    }

    private fun configureEnhancement(current: ExoPlayer, force: Boolean = false) {
        videoEnhancer.updateSuspended(pipActive || backgroundEnabled)
        val active = videoEnhancer.enabled && !enhancementUnavailable && sourceFormat != null && !videoEnhancer.comparing && !videoEnhancer.suspended
        audioEnhancer.smartProfile(videoEnhancer.lastMode, active)
        if (force) {
            effectsConfigured = false
            if (videoEnhancer.enabled && videoEnhancer.available && sourceFormat != null && !enhancementUnavailable) try {
                // Keep one neutral shader installed from preparation. Taps change uniforms only.
                current.setVideoEffects(listOf(videoEnhancer.effect))
                effectsConfigured = true
            } catch (_: RuntimeException) {
                videoEnhancer.unavailable("Video effects are unavailable on this device.")
                runCatching { current.setVideoEffects(emptyList()) }
            }
        }
        enhanceOn = active && ((videoEnhancer.available && effectsConfigured) || audioEnhancer.supportsSmart(videoEnhancer.lastMode))
    }

    private fun rebuildEnhancementPlayer(explanation: String? = null) {
        if (rebuilding?.isActive == true) return
        val current = player ?: return
        val uri = currentUri ?: return
        val wasBackground = backgroundEnabled
        rebuilding = viewModelScope.launch {
            yield()
            val position = current.currentPosition.coerceAtLeast(0L)
            val playing = current.playWhenReady
            rebuilding = null // stop must not cancel the recovery coroutine itself.
            stop(keepBackgroundPlayer = wasBackground)
            savedState["position"] = position
            savedState["playWhenReady"] = playing && (foreground || wasBackground)
            start(Uri.parse(uri), activeTitle)
            if (explanation != null) notice = explanation
        }
    }

    fun setPipActive(value: Boolean) { pipActive = value; player?.let { configureEnhancement(it) } }
    fun updateEnhance(value: EnhanceValues) { videoEnhancer.change(value); refreshEnhancement() }
    fun enableEnhance(enabled: Boolean) { videoEnhancer.changeEnabled(enabled); refreshEnhancement() }
    fun presetEnhance(name: String) { videoEnhancer.selectPreset(name); refreshEnhancement() }
    fun toggleSmartEnhance() {
        if (enhancementUnavailable) { notice = "Smart Enhance is unavailable for this video"; return }
        enableEnhance(!videoEnhancer.enabled)
        notice = if (!videoEnhancer.enabled) "Smart Enhance OFF"
            else if (!videoEnhancer.available && !audioEnhancer.supportsSmart(videoEnhancer.lastMode)) "Not supported on this device"
            else "Smart Enhance ON - ${videoEnhancer.lastMode}"
    }
    fun compareEnhance(original: Boolean) { videoEnhancer.compare(original); refreshEnhancement() }
    fun autoEnhanceSample(frame: FrameSummary?) { videoEnhancer.autoAdjust(frame) }
    private fun ensureSupportedAudio(value: Tracks) {
        val current = player ?: return
        if (muteProblemAudio) {
            if (!current.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)) current.trackSelectionParameters =
                current.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            return
        }
        val candidates = value.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group -> (0 until group.length).map { group to it } }
        if (candidates.isEmpty()) { audioWarning = null; return }
        val selected = candidates.indexOfFirst { (group, index) -> group.isTrackSelected(index) }
        val index = supportedAudioIndex(candidates.map { (group, index) -> group.isTrackSupported(index, false) && audioFormatKey(group.getTrackFormat(index)) !in failedAudioFormats }, selected)
        if (index < 0) {
            audioWarning = "Audio format ${candidates.map { it.first.getTrackFormat(it.second).sampleMimeType ?: "unknown" }.distinct().joinToString()} is not supported on this device"
            if (!current.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)) current.trackSelectionParameters =
                current.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
        } else {
            audioWarning = null
            if (index != selected || current.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)) {
                val (group, track) = candidates[index]
                current.trackSelectionParameters = current.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track)).build()
            }
        }
    }
    private fun refreshEnhancement() {
        if(videoEnhancer.enabled && !effectsConfigured) checkEnhancePath()
        player?.let { configureEnhancement(it) }
        repaint?.cancel()
        // Live uniforms update with the next rendered frame; never force decoder seeks to repaint.
    }

    private fun restoreTrackChoices(value: Tracks) {
        val selected = choice ?: return
        val current = player ?: return
        listOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT).forEach { type ->
            if (type == C.TRACK_TYPE_AUDIO && muteProblemAudio) return@forEach
            if ((type == C.TRACK_TYPE_AUDIO && audioRestored) || (type == C.TRACK_TYPE_TEXT && (textRestored || selected.textDisabled))) return@forEach
            val id = if (type == C.TRACK_TYPE_AUDIO) selected.audioId else selected.textId
            val language = if (type == C.TRACK_TYPE_AUDIO) selected.audioLanguage else selected.textLanguage
            val candidates = value.groups.filter { it.type == type }.flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it, false) && (type != C.TRACK_TYPE_AUDIO || audioFormatKey(group.getTrackFormat(it)) !in failedAudioFormats) }.map { group to it } }
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
    fun onForeground() {
        foreground = true
        player?.let { current ->
            current.trackSelectionParameters = current.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
            configureEnhancement(current)
        }
    }
    fun onHidden() {
        foreground = false
        if (!backgroundEnabled) stop() else {
            savePlaybackState()
            player?.let { current ->
                current.clearVideoSurface()
                current.trackSelectionParameters = current.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
                configureEnhancement(current)
            }
        }
    }
    // The service owns the active session until explicitly stopped; leaving a screen must not release it.
    fun close() { if (backgroundEnabled && player != null) onHidden() else stop() }

    fun setBackground(enabled: Boolean): Boolean {
        if(casting) {notice="Casting already runs in the background.";return false}
        if (enabled && isPrivatePlayback) { notice = "Private playback pauses when hidden."; return false }
        val current = player ?: return false
        if (enabled) {
            current.play()
            if (!com.vxplayer.music.VideoSessionHandoff.begin(getApplication(),current,{backgroundEnabled=false;stop()},
                sessionPlayer=SeekSessionPlayer(current,::seekBy,{seekTo(it)}))) return false
        } else BackgroundPlaybackService.detach(getApplication(), current)
        backgroundEnabled = enabled
        configureEnhancement(current)
        current.trackSelectionParameters = current.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, enabled && !foreground).build()
        return true
    }

    fun moveInPlaylist(offset: Int, atEnd: Boolean = false) {
        val step = playlistStep(playlist, activeUri, playbackMode, offset, playlistCursor, atEnd) ?: return
        val next = step.video
        playlistCursor = step.cursor
        savePlaylistCursor()
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
        if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            val type = preferences.all["streamType:$uri"] as? String ?: "Auto"
            when (type) {
                "HLS" -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                "DASH" -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            }
        }
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
        if (group.type == C.TRACK_TYPE_AUDIO) { muteProblemAudio = false; failedAudioFormats.remove(audioFormatKey(format)) }
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
        subtitleWork?.cancel()
        val video = currentUri ?: return
        val offset = subtitleDelayMs
        val encoding = subtitleEncoding
        subtitleWork = viewModelScope.launch {
            try {
                // Validate/read before remembering this file or changing the working tracks.
                val cached = withContext(Dispatchers.IO) {
                    ShiftedSubtitleCache.prepare(getApplication(), uri, offset, preparedSubtitle, encoding)
                }
                if (video != currentUri) return@launch
                attachSubtitle(uri, name, cached)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice = "Could not load external SRT. The video and existing subtitles are kept." }
        }
    }

    private fun attachSubtitle(uri: Uri, name: String, cached: Uri) {
        savedState["subtitleUri"] = uri.toString()
        savedState["subtitleName"] = name
        hasExternalSrt = true
        textRestored = true
        rememberChoice { it.copy(externalUri = uri.toString(), externalName = name, textDisabled = false, textId = "external-srt", textLanguage = null) }
        currentUri?.takeIf { it.startsWith("http", true) }?.let { video ->
            viewModelScope.launch {
                try { com.vxplayer.network.NetworkRepository(getApplication()).subtitle(video, uri.toString(), name) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* TrackPreference still remembers the attached subtitle. */ }
            }
        }
        val parameters = player?.trackSelectionParameters ?: savedState.get<Bundle>("trackParameters")?.let {
            TrackSelectionParameters.fromBundle(it)
        } ?: TrackSelectionParameters.Builder(getApplication<Application>()).build()
        val updated = parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        savedState["trackParameters"] = updated.toBundle()
        savedState["selectExternal"] = true
        player?.trackSelectionParameters = updated
        reloadExternalSubtitle(select = true, cachedUri = cached)
    }

    fun changeSubtitleDelay(delta: Int, reset: Boolean = false) {
        if (!canDelayExternalSubtitle) { notice = "Delay applies only to an external SRT file."; return }
        subtitleDelayMs = if (reset) 0 else (subtitleDelayMs + delta).coerceIn(-60_000, 60_000)
        currentUri?.let { preferences.edit().putInt(subtitleDelayKey(it), subtitleDelayMs).apply() }
        reloadExternalSubtitle(select = false)
    }

    private fun reloadExternalSubtitle(select: Boolean, cachedUri: Uri? = null) {
        subtitleWork?.cancel()
        val source = savedState.get<String>("subtitleUri") ?: return
        val video = currentUri ?: return
        val offset = subtitleDelayMs
        subtitleWork = viewModelScope.launch {
            delay(120)
            try {
                val cached = cachedUri ?: withContext(Dispatchers.IO) { ShiftedSubtitleCache.prepare(getApplication(), Uri.parse(source), offset, preparedSubtitle, subtitleEncoding) }
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
        if(casting && (castBridge?.ownedBy(this)!=true || playback?.playbackState in listOf(Player.STATE_IDLE,Player.STATE_BUFFERING)))return
        playback?.let {
            val position = if (it.playbackState == Player.STATE_ENDED) 0L else it.currentPosition.coerceAtLeast(0L)
            savedState["position"] = position
            savedState["playWhenReady"] = it.playWhenReady && it.playbackState != Player.STATE_ENDED
            val id = privateId
            if (id != null) privatePositions.edit().putLong(id, position).apply()
            else {
                currentUri?.let { uri -> positions.save(uri, position) }
                if (it.playbackState == Player.STATE_ENDED) currentUri?.let { uri -> positions.finished(uri) }
            }
        }
    }

    fun changeSpeed(value: Float) {
        if (value !in SPEEDS) return
        speed = value
        savedState["speed"] = value
        playback?.let {runCatching {it.setPlaybackSpeed(value)}.onFailure {notice="Speed is unavailable on this receiver."}}
    }

    fun seekBy(offsetMs: Long): Boolean {
        val current = playback ?: return false
        if (!current.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) || !current.isCurrentMediaItemSeekable) {
            seekMessage("This file has no usable seek index, or the stream does not allow seeking.");return false
        }
        seekTo(seekWithOffset(seekRequests.pending ?: current.currentPosition, offsetMs, current.duration))
        return true
    }

    fun seekTo(position: Long, exact: Boolean = false) {
        if(casting) {playback?.let {remote->runCatching {remote.seekTo(boundedSeek(position,remote.duration))}};return}
        val current = player ?: return
        if (!current.isCurrentMediaItemSeekable) {notice="This file has no usable seek index, or the stream does not allow seeking.";return}
        seekRequests.request(position,current.duration,SystemClock.elapsedRealtime())
        seekWork?.cancel(); seekWatchdog?.cancel(); seekSave?.cancel()
        seeking = true
        seekWork = seekScope.launch {
            delay(100)
            val target = seekRequests.take(SystemClock.elapsedRealtime()) ?: return@launch
            if (player !== current) return@launch
            current.setSeekParameters(if (exactSeekForJump(exact,current.currentPosition,target)) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)
            val before=current.currentPosition
            try {current.seekTo(target)} catch(_: RuntimeException){seeking=false;notice="Seeking failed. Try another position.";return@launch}
            buffering = true
            seekWatchdog = seekScope.launch {
                var started = SystemClock.elapsedRealtime()
                var retried = false
                var quickRetry=false
                var anchor = current.currentPosition
                while (player === current) {
                    delay(250)
                    val elapsed = SystemClock.elapsedRealtime() - started
                    val ready = current.playbackState == Player.STATE_READY
                    if(!quickRetry && elapsed>=500 && kotlin.math.abs(target-before)>=1000 && kotlin.math.abs(current.currentPosition-before)<300) {
                        quickRetry=true
                        current.setSeekParameters(if(exact)SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
                        runCatching {current.seekTo(boundedSeek(target,current.duration))}
                    }
                    if(quickRetry && elapsed>=1500 && kotlin.math.abs(current.currentPosition-before)<300) {
                        seeking=false;buffering=current.playbackState==Player.STATE_BUFFERING
                        notice="This file could not seek to that position. Its index may be missing or damaged.";break
                    }
                    if (current.playbackState == Player.STATE_ENDED || (ready && (!current.playWhenReady || current.currentPosition > anchor + 250))) {
                        seeking = false; buffering = false
                        seekSave = viewModelScope.launch { delay(600); savePlaybackState() }
                        break
                    }
                    if (elapsed >= 5000 && current.playbackState == Player.STATE_BUFFERING && effectsConfigured && !effectsDisabled) {
                        recoverySeek = target
                        disableEffectsForVideo("Enhancement stalled after seeking; playing without effects.")
                        rebuildEnhancementPlayer(videoEnhancer.reason)
                        break
                    }
                    if (elapsed >= 8000) {
                        if (!retried) {
                            retried = true; current.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                            current.seekTo(boundedSeek(target,current.duration)); if (current.playWhenReady) current.prepare()
                            anchor = current.currentPosition; started = SystemClock.elapsedRealtime()
                        } else {
                            seeking = false; buffering = false; failed = true; current.pause()
                            failureMessage = "Playback stalled after seeking. Retry or try another video."
                            break
                        }
                    }
                }
            }
        }
    }

    fun stop(keepBackgroundPlayer: Boolean = false) {
        if (!keepBackgroundPlayer) PlaybackCoordinator.unregisterVideo(this)
        seekWork?.cancel(); seekWatchdog?.cancel(); seekSave?.cancel(); seekRequests.cancel(); seeking = false; seekDragging = false
        subtitleWork?.cancel(); subtitleWork = null
        rebuilding?.cancel(); rebuilding = null
        effectsConfigured = false
        warmingAttempted = false
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
        if (keepBackgroundPlayer) pendingBackgroundReplacement = previous
        else {
            BackgroundPlaybackService.detach(getApplication(), previous)
            backgroundEnabled = false
            previous?.release()
            pendingBackgroundReplacement?.let { pending ->
                pendingBackgroundReplacement = null
                BackgroundPlaybackService.detach(getApplication(), pending)
                pending.release(); backgroundEnabled = false
            }
        }
        isPlaying = casting && castBridge?.player?.isPlaying==true
        buffering = casting && castBridge?.player?.playbackState==Player.STATE_BUFFERING
    }

    override fun onCleared() {close();castBridge?.abandon(this);castBridge=null }

    companion object {
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    }
}
