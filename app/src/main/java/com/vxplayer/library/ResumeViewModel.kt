package com.vxplayer.library

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vxplayer.player.PlaybackPositionStore
import com.vxplayer.privatefiles.PrivateRepository
import com.vxplayer.ui.ResumeVideo
import com.vxplayer.ui.selectResumeVideo
import kotlinx.coroutines.*

class ResumeViewModel(application: Application) : AndroidViewModel(application) {
    var candidate by mutableStateOf<ResumeVideo?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    private var refreshJob: Job? = null
    private val store = PlaybackPositionStore.get(application)
    private fun readable(uri: Uri): Boolean = try {
        getApplication<Application>().contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } == true
    } catch (_: Exception) { false }
    fun refresh(videos: List<Video>) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val history = store.recent()
                val snapshot = store.snapshot()
                val indexed = videos.associateBy { it.uri.toString() }
                candidate = withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val prefs = app.getSharedPreferences("vxplayer", 0)
                    selectResumeVideo(history.map { played ->
                        val uri = Uri.parse(played.uri)
                        val video = indexed[played.uri]
                        val network = runCatching { StreamUrls.validate(played.uri) }.isSuccess
                        val privateVideo = PrivateRepository.privateId(app, uri) != null
                        ResumeVideo(played.uri, video?.title ?: played.title, video?.folderKey.orEmpty(),
                            snapshot.positions[played.uri] ?: 0, video?.durationMs ?: 0, played.playedAt,
                            played.uri in snapshot.watched, !privateVideo && if (network) prefs.contains("streamType:${played.uri}") else readable(uri), privateVideo)
                    })
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { candidate = null }
        }
    }
    fun play(fromStart: Boolean, onPlay: (Video, String) -> Unit, missing: () -> Unit) {
        val selected = candidate ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val uri = Uri.parse(selected.uri)
                val network = runCatching { StreamUrls.validate(selected.uri) }.isSuccess
                if (!network && !withContext(Dispatchers.IO) { readable(uri) }) {
                    candidate = null; missing(); return@launch
                }
                if (fromStart || (selected.durationMs > 0 && selected.positionMs >= selected.durationMs)) {
                    store.save(selected.uri, 0); store.load(selected.uri) // Serialize reset before a new player reads it.
                }
                onPlay(Video(uri, selected.title, selected.durationMs, 0), selected.folder)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { candidate = null; missing() }
            finally { busy = false }
        }
    }
}
