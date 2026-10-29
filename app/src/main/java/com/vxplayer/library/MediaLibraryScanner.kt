package com.vxplayer.library

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.vxplayer.player.displayDimensions
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

val VIDEO_EXTENSIONS = listOf("mp4", "mkv", "webm", "ts", "m2ts", "mts", "flv", "3gp", "3g2", "avi", "wmv", "mov", "m4v", "mpeg", "mpg", "vob", "ogv", "asf")

internal suspend fun scanMediaLibrary(context: Context): List<VideoFolder> {
    val volumes = if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(context).sorted() else listOf("external")
    val groups = linkedMapOf<String, MutableList<Video>>()
    val names = mutableMapOf<String, String>()
    var readableVolumes = 0
    for (volume in volumes) {
        currentCoroutineContext().ensureActive()
        val collection = MediaStore.Files.getContentUri(volume)
        val projection = arrayOf("_id", "_display_name", "duration", "_size", "bucket_id", "bucket_display_name", "width", "height", "date_added", "_data", "media_type", "orientation")
        val selection = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR " +
            VIDEO_EXTENSIONS.joinToString(" OR ") { "LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE ?" } + ")"
        val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(), "video/%") + VIDEO_EXTENSIONS.map { "%.$it" }
        val cursor = try {
            context.contentResolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DISPLAY_NAME} ASC")
        } catch (_: IllegalArgumentException) {
            // Some older providers do not expose orientation for Files queries.
            runCatching { context.contentResolver.query(collection, projection.dropLast(1).toTypedArray(), selection, args, "${MediaStore.MediaColumns.DISPLAY_NAME} ASC") }.getOrNull()
        } catch (_: SecurityException) { null }
        if (cursor == null) continue
        readableVolumes++
        cursor.use { data ->
            fun text(column: String) = data.getColumnIndex(column).takeIf { it >= 0 }?.let { data.getString(it) }.orEmpty()
            fun number(column: String) = data.getColumnIndex(column).takeIf { it >= 0 }?.let { data.getLong(it) } ?: 0
            while (data.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val videoCollection = if (number("media_type") == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toLong()) {
                    if (volume == "external_primary" || Build.VERSION.SDK_INT < 29) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Video.Media.getContentUri(volume)
                } else collection
                val uri = ContentUris.withAppendedId(videoCollection, number("_id"))
                val key = "$volume:${text("bucket_id").ifBlank { "unknown" }}"
                names[key] = text("bucket_display_name").ifBlank { "Other videos" }
                val title = text("_display_name").ifBlank { "Untitled video" }
                val rawWidth = number("width").toInt().coerceAtLeast(0)
                val rawHeight = number("height").toInt().coerceAtLeast(0)
                val size = number("_size").coerceAtLeast(0)
                val duration = number("duration").coerceAtLeast(0)
                val dimensions = displayDimensions(rawWidth, rawHeight, number("orientation").toInt())
                val fallback = if (duration == 0L || rawWidth == 0 || rawHeight == 0 || size == 0L)
                    inspectLocalVideo(context, uri, title, key) else null
                groups.getOrPut(key) { mutableListOf() }.add(Video(uri, title,
                    if (duration > 0) duration else fallback?.durationMs ?: 0,
                    if (size > 0) size else fallback?.sizeBytes ?: 0,
                    if (rawWidth > 0 && rawHeight > 0) dimensions.first else fallback?.width ?: 0,
                    if (rawWidth > 0 && rawHeight > 0) dimensions.second else fallback?.height ?: 0,
                    number("date_added"), text("_data"), key))
            }
        }
    }
    check(readableVolumes > 0 || volumes.isEmpty()) { "No external media volume could be read" }
    return groups.map { (key, videos) -> VideoFolder(key, names.getValue(key), videos.distinctBy { it.uri }) }.sortedBy { it.name.lowercase() }
}
