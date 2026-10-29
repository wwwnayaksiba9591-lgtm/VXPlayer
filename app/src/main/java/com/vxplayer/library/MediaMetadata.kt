package com.vxplayer.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.vxplayer.player.displayDimensions
import android.content.ContentUris
import android.provider.MediaStore

/** Resolve only a matching old synthetic URI, so colliding IDs on another volume cannot steal resume data. */
fun legacyResumeAlias(context: Context, uri: Uri): String? {
    if (android.os.Build.VERSION.SDK_INT < 29 || uri.authority != "media") return null
    val parts = uri.pathSegments
    if (parts.size != 4 || parts[1] != "video" || parts[2] != "media" || parts[0] in listOf("external", "external_primary")) return null
    val id = parts[3].toLongOrNull() ?: return null
    val legacy = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
    val actualVolume = runCatching { context.contentResolver.query(legacy, arrayOf(MediaStore.MediaColumns.VOLUME_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } }.getOrNull()
    return legacy.toString().takeIf { actualVolume == parts[0] }
}

/** All calls belong on Dispatchers.IO. No network URLs are probed here. */
fun inspectLocalVideo(context: Context, uri: Uri, title: String = "Video", folder: String = ""): Video {
    require(uri.scheme in listOf("content", "file"))
    var name = title
    var size = 0L
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) { name = it.getString(0)?.takeIf(String::isNotBlank) ?: title; size = it.getLong(1).coerceAtLeast(0) }
        }
    }
    if (size <= 0) runCatching { size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize.coerceAtLeast(0) } ?: 0 }
    var duration = 0L; var width = 0; var height = 0
    val retriever = runCatching { MediaMetadataRetriever() }.getOrNull()
        ?: return Video(uri, name, duration, size, width, height, folderKey = folder)
    try {
        retriever.setDataSource(context, uri)
        duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0) ?: 0
        val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val dimensions = displayDimensions(rawWidth, rawHeight, rotation)
        width = dimensions.first; height = dimensions.second
    } catch (_: Exception) { /* Playback may still succeed through Media3. */ }
    finally { runCatching { retriever.release() } }
    return Video(uri, name, duration, size, width, height, folderKey = folder)
}

fun fallbackVideoThumbnail(context: Context, uri: Uri): android.graphics.Bitmap? {
    val retriever = runCatching { MediaMetadataRetriever() }.getOrNull() ?: return null
    return try {
        retriever.setDataSource(context, uri)
        if (android.os.Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 144)
        else {
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toLongOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toLongOrNull() ?: 0
            if (width <= 0 || height <= 0 || width * height > 1920L * 1080) null
            else retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { bitmap ->
                val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 256, 144, true)
                if (scaled !== bitmap) bitmap.recycle()
                scaled
            }
        }
    } catch (_: Exception) { null }
    catch (_: OutOfMemoryError) { null }
    finally { runCatching { retriever.release() } }
}
