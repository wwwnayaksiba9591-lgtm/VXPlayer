package com.vxplayer.music

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import androidx.palette.graphics.Palette
import android.os.Bundle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

suspend fun scanMusic(context: Context): AudioScanResult {
    val volumes = linkedSetOf("internal","external") // external is MediaStore.VOLUME_EXTERNAL, the merged shared-storage view.
    if (Build.VERSION.SDK_INT >= 29) volumes += runCatching { MediaStore.getExternalVolumeNames(context) }.getOrDefault(emptySet())
    val result = linkedMapOf<String,Song>()
    val failed = linkedSetOf<String>()
    for (volume in volumes) {
        for (files in listOf(false,true)) {
            currentCoroutineContext().ensureActive()
            val collection = if (files) MediaStore.Files.getContentUri(volume) else MediaStore.Audio.Media.getContentUri(volume)
            val projection = mutableListOf("_id","_display_name","mime_type","date_added")
            if (!files) projection += listOf("title","artist","album","duration","album_id","track","year")
            if (Build.VERSION.SDK_INT >= 29) projection += listOf("relative_path","volume_name")
            var lastId = -1L
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val audioSelection = if (files) " AND (media_type = 2 OR mime_type LIKE 'audio/%' OR " +
                        AudioLibraryPolicy.extensions.joinToString(" OR ") { "LOWER(_display_name) LIKE '%.$it'" } + ")" else ""
                    val selection = "_id > ?$audioSelection"
                    val args = arrayOf(lastId.toString())
                    val cursor = try { if (Build.VERSION.SDK_INT >= 26) {
                        context.contentResolver.query(collection,projection.toTypedArray(),Bundle().apply {
                            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,selection)
                            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,args)
                            putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,arrayOf("_id"))
                            putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER,"_id ASC")
                            putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
                            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT,500)
                        },null)
                    } else context.contentResolver.query(collection,projection.toTypedArray(),selection,args,"_id ASC")
                    } catch (_: IllegalArgumentException) {
                        // Older/OEM providers may not expose optional volume columns or structured arguments.
                        context.contentResolver.query(collection,projection.filterNot { it in setOf("relative_path","volume_name") }.toTypedArray(),selection,args,"_id ASC")
                    }
                    if(cursor==null){if(!files)failed+=volume;break}
                    var rows = 0
                    val previousId = lastId
                    cursor?.use {
                        fun text(key: String) = it.getColumnIndex(key).takeIf { index -> index >= 0 }?.let { index -> it.getString(index) }.orEmpty()
                        fun number(key: String) = it.getColumnIndex(key).takeIf { index -> index >= 0 }?.let { index -> it.getLong(index) } ?: 0
                        while (it.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val id = number("_id"); lastId = maxOf(lastId,id); rows++
                            val name = text("_display_name")
                            if (files && !AudioLibraryPolicy.isAudio(text("mime_type"),name)) continue
                            val actualVolume = text("volume_name").ifBlank { volume }
                            val key = "${if (volume == "internal") "internal" else "external"}:$id"
                            if (key !in result) result[key] = AudioLibraryPolicy.map(
                                ContentUris.withAppendedId(if (files) MediaStore.Files.getContentUri(actualVolume) else MediaStore.Audio.Media.getContentUri(actualVolume),id).toString(),
                                text("title"),name,text("artist"),text("album"),number("duration"),number("album_id"),number("track").toInt(),number("year").toInt(),number("date_added"),actualVolume,text("relative_path"))
                        }
                    }
                    if (rows < 500 || lastId <= previousId) break
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { if (!files) failed += volume }
        }
    }
    return AudioScanResult(result.values.toList(),failed)
}

data class MusicArtwork(val bitmap: Bitmap?, val color: Int)
object MusicArt {
    private val cache = object : LruCache<String,MusicArtwork>(16*1024*1024) { override fun sizeOf(key: String, value: MusicArtwork) = value.bitmap?.byteCount ?: 256 }
    fun load(context: Context, song: Song): MusicArtwork {
        val key = "${song.volume}:${song.albumId}:${if(song.albumId <= 0) song.uri else ""}"
        cache.get(key)?.let { return it }
        var bitmap: Bitmap? = null
        try {
            val art = Uri.parse("content://media/${song.volume}/audio/albumart/${song.albumId}")
            bitmap = context.contentResolver.openInputStream(art)?.use { input ->
                val data = input.readBytesBounded(8*1024*1024)
                decodeArt(data)
            }
        } catch (_: Exception) { }
        if (bitmap == null) try {
            val retriever = MediaMetadataRetriever()
            try { retriever.setDataSource(context,Uri.parse(song.uri)); bitmap = retriever.embeddedPicture?.takeIf { it.size <= 8*1024*1024 }?.let(::decodeArt) }
            finally { retriever.release() }
        } catch (_: Exception) { } catch (_: OutOfMemoryError) { }
        val fallback = 0xFF000000.toInt() or (song.album.hashCode() and 0x007F7F7F) or 0x00303030
        val color = bitmap?.let { runCatching { Palette.from(it).maximumColorCount(12).generate().getDominantColor(fallback) }.getOrNull() } ?: fallback
        return MusicArtwork(bitmap,color).also { cache.put(key,it) }
    }
    private fun decodeArt(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth,bounds.outHeight)/256).coerceAtLeast(1) }
        return BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
    }
}
private fun java.io.InputStream.readBytesBounded(max: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val count = read(buffer); if (count < 0) break; require(output.size()+count <= max); output.write(buffer,0,count) }
    return output.toByteArray()
}
fun companionLyrics(context: Context, song: Song): List<LyricLine> = runCatching {
    if (Build.VERSION.SDK_INT < 29 || song.relativePath.isBlank()) return@runCatching emptyList()
    val files = MediaStore.Files.getContentUri(song.volume)
    val name = song.fileName.substringBeforeLast('.') + ".lrc"
    context.contentResolver.query(files,arrayOf("_id"),"_display_name = ? AND relative_path = ?",arrayOf(name,song.relativePath),null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use emptyList()
        val uri = ContentUris.withAppendedId(files,cursor.getLong(0))
        context.contentResolver.openInputStream(uri)?.use { parseLrc(it.readBytesBounded(262144).toString(Charsets.UTF_8).trimStart('\uFEFF')) }.orEmpty()
    }.orEmpty()
}.getOrDefault(emptyList())
