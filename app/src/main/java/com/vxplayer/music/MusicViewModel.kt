package com.vxplayer.music

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import com.vxplayer.player.PlaybackDatabase
import com.vxplayer.settings.SettingsParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val dao = PlaybackDatabase.get(app).music()
    private val prefs = app.getSharedPreferences("vxplayer",0)
    val permission get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun allowed() = ContextCompat.checkSelfPermission(app,permission) == PackageManager.PERMISSION_GRANTED
    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var hideShort by mutableStateOf(SettingsParser.boolean(prefs.all["hideShortMusic"],true)); private set
    var sort by mutableStateOf(SettingsParser.enum(prefs.all["musicSort"],MusicSort.Title)); private set
    val stats = dao.stats().catch { message = "Could not read music history." }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val playlists = dao.playlists().catch { message = "Could not read playlists." }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val playlistItems = dao.items().catch { message = "Could not read playlist items." }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    var controller by mutableStateOf<MediaController?>(null); private set
    var current by mutableStateOf<Song?>(null); private set
    var queue by mutableStateOf<List<Song>>(emptyList()); private set
    var playing by mutableStateOf(false); private set
    var shuffle by mutableStateOf(false); private set
    var repeat by mutableIntStateOf(0); private set
    var nowPlaying by mutableStateOf(false)
    var showQueue by mutableStateOf(false)
    var showEqualizer by mutableStateOf(false)
    var showSleep by mutableStateOf(false)
    private var scan: Job? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || events.contains(Player.EVENT_MEDIA_METADATA_CHANGED))
        }
        override fun onPlayerError(error: PlaybackException) { message = "Could not play this track. The file may be missing or unsupported. Retry or choose another track." }
    }
    private val connection = MediaController.Builder(app,SessionToken(app,ComponentName(app,MusicPlaybackService::class.java))).buildAsync()
    init {
        connection.addListener({
            runCatching { connection.get() }.onSuccess { controller = it; it.addListener(listener); sync() }
                .onFailure { message = "Music controls could not connect. Reopen the app to retry." }
        },ContextCompat.getMainExecutor(app))
    }
    private fun sync(refreshQueue: Boolean = true) {
        val engine = controller ?: return
        if (refreshQueue) {
            val indexed = songs.associateBy { it.uri }
            queue = (0 until engine.mediaItemCount).map { engine.getMediaItemAt(it).let { item -> indexed[item.mediaId] ?: item.song() } }
        }
        current = queue.getOrNull(engine.currentMediaItemIndex)
        playing = engine.isPlaying; shuffle = engine.shuffleModeEnabled; repeat = engine.repeatMode
    }
    fun refresh() {
        scan?.cancel()
        hideShort = SettingsParser.boolean(prefs.all["hideShortMusic"],true)
        if (!allowed()) { songs = emptyList(); return }
        scan = viewModelScope.launch {
            loading = true; message = null
            try { songs = withContext(Dispatchers.IO) { scanMusic(app,hideShort) }; sync() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Could not read music. Check audio permission, then refresh." }
            finally { loading = false }
        }
    }
    fun changeHideShort(value: Boolean) { hideShort = value; prefs.edit().putBoolean("hideShortMusic",value).apply(); refresh() }
    fun changeSort(value: MusicSort) { sort = value; prefs.edit().putString("musicSort",value.name).apply() }
    fun play(song: Song, list: List<Song> = songs, shuffled: Boolean = false) {
        val player = controller ?: run { message = "Music controls are connecting. Try again."; return }
        val selected = if (shuffled) shuffledMusic(list,song) else list.distinctBy { it.uri }
        if (selected.isEmpty()) return
        player.setMediaItems(selected.take(5000).map { it.mediaItem() },selected.indexOfFirst { it.uri == song.uri }.coerceAtLeast(0),0)
        player.shuffleModeEnabled = shuffled; player.prepare(); player.play(); nowPlaying = true; sync()
    }
    fun toggle() { controller?.let { if (it.playWhenReady) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun shuffleToggle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun repeatToggle() { controller?.let { it.repeatMode = nextMusicRepeat(it.repeatMode) } }
    fun add(song: Song, next: Boolean) {
        val player = controller ?: return
        val updated = insertMusic(queue,player.currentMediaItemIndex,song,next)
        val index = if (next) (player.currentMediaItemIndex+1).coerceIn(0,player.mediaItemCount) else player.mediaItemCount
        if (updated.size > queue.size) player.addMediaItem(index,song.mediaItem())
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        sync()
    }
    fun remove(index: Int) { controller?.let { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }; sync() }
    fun move(from: Int, to: Int) { controller?.let { if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from,to) }; sync() }
    fun seekQueue(index: Int) { controller?.let { if (index in 0 until it.mediaItemCount) { it.seekTo(index,0); it.play() } } }
    fun clearQueue() { controller?.clearMediaItems(); sync() }
    fun favourite(song: Song) = work { dao.favourite(song.uri) }
    fun playlist(name: String, id: String = UUID.randomUUID().toString()) = work { require(name.isNotBlank()); dao.savePlaylist(MusicPlaylist(id,name.trim().take(120))) }
    fun deletePlaylist(id: String) = work { dao.deletePlaylist(id) }
    fun updatePlaylist(id: String, uris: List<String>) = work { dao.replaceItems(id,uris) }
    private fun work(action: suspend () -> Unit) = viewModelScope.launch {
        try { withContext(Dispatchers.IO) { action() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "Could not save the music change. Try again." }
    }
    override fun onCleared() { controller?.removeListener(listener); MediaController.releaseFuture(connection) }
}
