package com.vxplayer.library

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vxplayer.R
import com.vxplayer.player.PlaybackPositionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class Video(val uri: Uri, val title: String, val durationMs: Long, val sizeBytes: Long,
    val width: Int = 0, val height: Int = 0, val dateAdded: Long = 0L, val path: String = "",
    val folderKey: String = "", val positionMs: Long = 0L, val watched: Boolean = false)
data class VideoFolder(val key: String, val name: String, val videos: List<Video>)
data class LibraryState(val loading: Boolean = false, val folders: List<VideoFolder> = emptyList(), val failed: Boolean = false,
    val recentlyPlayed: List<Video> = emptyList())

class VideoLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(LibraryState())
    val state = mutableState.asStateFlow()
    private var scan: Job? = null

    fun refresh(hasAccess: Boolean) {
        scan?.cancel()
        mutableState.value = if (hasAccess) mutableState.value.copy(loading = true) else LibraryState()
        if (!hasAccess) return
        scan = viewModelScope.launch {
            try {
                val scanned = withContext(Dispatchers.IO) { queryVideos() }
                val snapshot = try { PlaybackPositionStore.get(getApplication()).snapshot() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { com.vxplayer.player.PlaybackSnapshot() }
                val folders = scanned.map { folder -> folder.copy(videos = folder.videos.map { video ->
                    video.copy(positionMs = snapshot.positions[video.uri.toString()] ?: 0L, watched = video.uri.toString() in snapshot.watched)
                }) }
                val recent = try { PlaybackPositionStore.get(getApplication()).recent() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                val accessible = folders.flatMap { it.videos }.associateBy { it.uri.toString() }
                mutableState.value = LibraryState(folders = folders, recentlyPlayed = recent.mapNotNull { accessible[it.uri] })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = LibraryState(failed = true)
            }
        }
    }

    private suspend fun queryVideos(): List<VideoFolder> = scanMediaLibrary(getApplication())
}
