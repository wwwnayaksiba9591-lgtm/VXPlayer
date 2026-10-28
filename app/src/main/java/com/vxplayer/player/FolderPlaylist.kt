package com.vxplayer.player

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

data class PlaylistVideo(val uri: String, val title: String)

fun adjacentVideo(videos: List<PlaylistVideo>, current: String?, offset: Int): PlaylistVideo? {
    val index = videos.indexOfFirst { it.uri == current }
    return if (index < 0) null else videos.getOrNull(index + offset)
}

internal fun loadFolderPlaylist(context: Context, folder: String): List<PlaylistVideo> {
    if (folder.isBlank()) return emptyList()
    val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val result = mutableListOf<PlaylistVideo>()
    val preferences = context.getSharedPreferences("vxplayer", 0)
    val column = when (preferences.getString("sort", "Name")) {
        "Date" -> MediaStore.Video.Media.DATE_ADDED
        "Size" -> MediaStore.Video.Media.SIZE
        "Duration" -> MediaStore.Video.Media.DURATION
        else -> "${MediaStore.Video.Media.DISPLAY_NAME} COLLATE NOCASE"
    }
    val direction = if (preferences.getBoolean("descending", false)) "DESC" else "ASC"
    val order = "$column $direction, ${MediaStore.Video.Media.DISPLAY_NAME} COLLATE NOCASE $direction"
    context.contentResolver.query(collection,
        arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME),
        "${MediaStore.Video.Media.BUCKET_ID} = ?", arrayOf(folder), order)?.use {
        while (it.moveToNext()) result.add(PlaylistVideo(ContentUris.withAppendedId(collection, it.getLong(0)).toString(), it.getString(1).orEmpty()))
    }
    return result
}
