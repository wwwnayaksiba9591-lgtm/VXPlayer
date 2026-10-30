package com.vxplayer.music

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.session.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.vxplayer.MainActivity
import com.vxplayer.player.AudioEnhancer
import com.vxplayer.player.PlaybackCoordinator
import com.vxplayer.player.PlaybackDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

private data class MusicSnapshot(val songs: List<Song>,val index: Int,val position: Long,val shuffle: Boolean,val repeat: Int)
private object MusicPersistence {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private sealed interface Command {
        data class Save(val dao: MusicDao,val state: MusicSnapshot): Command
        data class Load(val dao: MusicDao,val result: CompletableDeferred<MusicSessionState?>): Command
    }
    private val commands = Channel<Command>(Channel.UNLIMITED)
    init { scope.launch { for (command in commands) when (command) {
        is Command.Save -> runCatching { val state=command.state; command.dao.saveSession(MusicSessionState(songsJson=Gson().toJson(state.songs),currentIndex=state.index,position=state.position,shuffle=state.shuffle,repeatMode=state.repeat)) }
        is Command.Load -> command.result.complete(runCatching { command.dao.session() }.getOrNull())
    } } }
    fun save(dao: MusicDao, state: MusicSnapshot) { commands.trySend(Command.Save(dao,state)) }
    suspend fun load(dao: MusicDao): MusicSessionState? { val result=CompletableDeferred<MusicSessionState?>();commands.send(Command.Load(dao,result));return result.await() }
}

fun Song.mediaItem(): MediaItem = MediaItem.Builder().setMediaId(uri).setUri(uri).setMediaMetadata(MediaMetadata.Builder()
    .setTitle(title).setArtist(artist).setAlbumTitle(album).setTrackNumber(track).setRecordingYear(year)
    .setExtras(android.os.Bundle().apply { putLong("vx.duration",duration); putLong("vx.album",albumId); putLong("vx.added",added); putString("vx.volume",volume); putString("vx.relative",relativePath); putString("vx.name",fileName) })
    .setArtworkUri(Uri.parse("content://media/$volume/audio/albumart/$albumId")).build()).build()
fun MediaItem.song() = Song(mediaId,mediaMetadata.title?.toString().orEmpty(),mediaMetadata.artist?.toString() ?: "Unknown artist",
    mediaMetadata.albumTitle?.toString() ?: "Unknown album",duration=mediaMetadata.extras?.getLong("vx.duration") ?: 0,albumId=mediaMetadata.extras?.getLong("vx.album") ?: 0,
    track=mediaMetadata.trackNumber ?: 0,year=mediaMetadata.recordingYear ?: 0,added=mediaMetadata.extras?.getLong("vx.added") ?: 0,
    volume=mediaMetadata.extras?.getString("vx.volume") ?: "external",relativePath=mediaMetadata.extras?.getString("vx.relative").orEmpty(),fileName=mediaMetadata.extras?.getString("vx.name").orEmpty())

@androidx.annotation.OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {
    private lateinit var engine: ExoPlayer
    private var session: MediaSession? = null
    private lateinit var dao: MusicDao
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var saveWork: Job? = null
    private var restored = false
    private var counted: String? = null
    private var sleepDeadline = 0L
    private var sleepAtEnd = false
    var sleepLabel = "Off"
        private set
    lateinit var effects: AudioEnhancer
        private set
    private val pauseForVideo: () -> Unit = { if (::engine.isInitialized) engine.pause() }
    companion object { var instance: MusicPlaybackService? = null; private set }

    override fun onCreate() {
        super.onCreate(); instance = this
        dao = PlaybackDatabase.get(this).music()
        effects = AudioEnhancer(this)
        engine = ExoPlayer.Builder(this,DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setUsePlatformDiagnostics(false).setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
            .setHandleAudioBecomingNoisy(true).build()
        PlaybackCoordinator.pauseMusic = pauseForVideo
        engine.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(id: Int) { effects.attach(id) }
            override fun onPlayWhenReadyChanged(play: Boolean, reason: Int) { if (play) PlaybackCoordinator.startingMusic() }
            override fun onIsPlayingChanged(playing: Boolean) { if (playing) countPlay(); persistSoon() }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                counted = null
                if (sleepAtEnd && reason in listOf(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)) {
                    engine.pause(); setSleep(0)
                } else if (engine.playWhenReady) countPlay()
                persistSoon()
            }
            override fun onEvents(player: Player, events: Player.Events) { persistSoon() }
            override fun onPlayerError(error: PlaybackException) { engine.pause(); persistSoon() }
        })
        val launch = PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java).putExtra("openMusic",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this,engine).setSessionActivity(launch).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                if (controller.packageName == packageName || controller.isTrusted) super.onConnect(session,controller) else MediaSession.ConnectionResult.reject()
            override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, items: List<MediaItem>): ListenableFuture<List<MediaItem>> =
                Futures.immediateFuture(items.mapNotNull { item ->
                    val uri = item.localConfiguration?.uri ?: runCatching { Uri.parse(item.mediaId) }.getOrNull()
                    if (uri?.scheme == "content" && uri.authority == "media") item.buildUpon().setUri(uri).build() else null
                })
        }).build().also { addSession(it) }
        scope.launch {
            val saved = MusicPersistence.load(dao)
            if (saved != null && engine.mediaItemCount == 0) {
                val songs = withContext(Dispatchers.IO) { runCatching { Gson().fromJson(saved.songsJson,Array<Song>::class.java).toList().take(5000) }.getOrDefault(emptyList())
                    .filter { runCatching { Uri.parse(it.uri).let { u -> u.scheme == "content" && u.authority == "media" } }.getOrDefault(false) } }
                if (engine.mediaItemCount > 0) { restored = true; return@launch }
                if (songs.isNotEmpty()) {
                    engine.setMediaItems(songs.map { it.mediaItem() },saved.currentIndex.coerceIn(songs.indices),saved.position.coerceAtLeast(0))
                    engine.shuffleModeEnabled = saved.shuffle
                    engine.repeatMode = saved.repeatMode.takeIf { it in 0..2 } ?: Player.REPEAT_MODE_OFF
                    engine.playWhenReady = false // Never auto-play a restored queue.
                }
            }
            restored = true
        }
        scope.launch {
            while (isActive) {
                delay(1000)
                if (sleepDeadline > 0 && SystemClock.elapsedRealtime() >= sleepDeadline) { engine.pause(); setSleep(0) }
                if (engine.isPlaying && SystemClock.elapsedRealtime()%5000 < 1100) persistSoon()
            }
        }
    }
    private fun countPlay() {
        val item = engine.currentMediaItem ?: return
        val key = "${engine.currentMediaItemIndex}:${item.mediaId}"
        if (counted == key) return
        counted = key; scope.launch(Dispatchers.IO) { runCatching { dao.played(item.mediaId) } }
    }
    fun setSleep(minutes: Int, end: Boolean = false) {
        sleepAtEnd = end; sleepDeadline = if (minutes > 0) SystemClock.elapsedRealtime()+minutes*60_000L else 0
        sleepLabel = if (end) "End of track" else if (minutes > 0) "$minutes min" else "Off"
    }
    private fun snapshot(): MusicSnapshot = MusicSnapshot(songs=(0 until engine.mediaItemCount).map { engine.getMediaItemAt(it).song() },
        index=engine.currentMediaItemIndex.coerceAtLeast(0),position=engine.currentPosition.coerceAtLeast(0),shuffle=engine.shuffleModeEnabled,repeat=engine.repeatMode)
    private fun persistSoon() {
        if (!restored) return
        saveWork?.cancel(); saveWork = scope.launch { delay(500); MusicPersistence.save(dao,snapshot()) }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onTaskRemoved(rootIntent: Intent?) { if (!engine.playWhenReady || engine.mediaItemCount == 0 || engine.playbackState == Player.STATE_ENDED) stopSelf() }
    override fun onDestroy() {
        if (::engine.isInitialized && restored) MusicPersistence.save(dao,snapshot())
        if (PlaybackCoordinator.pauseMusic === pauseForVideo) PlaybackCoordinator.pauseMusic = null
        scope.cancel(); session?.let { removeSession(it); it.release() }; session = null
        if (::effects.isInitialized) effects.release()
        if (::engine.isInitialized) engine.release()
        if (instance === this) instance = null
        super.onDestroy()
    }
}
