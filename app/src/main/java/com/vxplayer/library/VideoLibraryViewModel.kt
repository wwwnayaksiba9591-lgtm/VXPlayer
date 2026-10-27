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

data class Video(val uri: Uri, val title: String, val durationMs: Long, val sizeBytes: Long)
data class VideoFolder(val key: String, val name: String, val videos: List<Video>)
data class LibraryState(val loading: Boolean = false, val folders: List<VideoFolder> = emptyList(), val failed: Boolean = false,
    val recentlyPlayed: List<Video> = emptyList())

class VideoLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(LibraryState())
    val state = mutableState.asStateFlow()
    private var scan: Job? = null

    fun refresh(hasAccess: Boolean) {
        scan?.cancel()
        mutableState.value = LibraryState(loading = hasAccess)
        if (!hasAccess) return
        scan = viewModelScope.launch {
            try {
                val folders = withContext(Dispatchers.IO) { queryVideos() }
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

    private suspend fun queryVideos(): List<VideoFolder> {
        val app = getApplication<Application>()
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_ID, MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )
        val videos = linkedMapOf<String, MutableList<Video>>()
        val names = mutableMapOf<String, String>()
        val cursor = app.contentResolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DISPLAY_NAME} ASC")
            ?: error("MediaStore unavailable")
        cursor.use {
            val id = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val title = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val duration = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val size = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val bucket = it.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val folder = it.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            while (it.moveToNext()) {
                coroutineContext.ensureActive()
                val key = it.getString(bucket) ?: "unknown"
                names[key] = it.getString(folder)?.takeIf(String::isNotBlank) ?: app.getString(R.string.unknown_folder)
                videos.getOrPut(key) { mutableListOf() }.add(Video(
                    ContentUris.withAppendedId(collection, it.getLong(id)),
                    it.getString(title)?.takeIf(String::isNotBlank) ?: app.getString(R.string.unknown_video),
                    it.getLong(duration).coerceAtLeast(0), it.getLong(size).coerceAtLeast(0),
                ))
            }
        }
        return videos.map { (key, items) -> VideoFolder(key, names.getValue(key), items) }
            .sortedBy { it.name.lowercase() }
    }
}
