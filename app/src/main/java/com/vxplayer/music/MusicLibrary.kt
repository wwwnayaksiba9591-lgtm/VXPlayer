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

fun scanMusic(context: Context, hideShort: Boolean): List<Song> {
    val volumes = if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(context) else setOf("external")
    return volumes.flatMap { volume ->
        val collection = MediaStore.Audio.Media.getContentUri(volume)
        val projection = mutableListOf("_id","title","artist","album","duration","album_id","track","year","date_added","_display_name")
        if (Build.VERSION.SDK_INT >= 29) projection += "relative_path"
        val result = mutableListOf<Song>()
        val selection = "is_music != 0 AND is_ringtone = 0 AND is_notification = 0 AND is_alarm = 0" + if (Build.VERSION.SDK_INT >= 31) " AND is_recording = 0" else ""
        context.contentResolver.query(collection,projection.toTypedArray(),selection,null,null)?.use { cursor ->
            fun text(key: String) = cursor.getColumnIndex(key).takeIf { it >= 0 }?.let { cursor.getString(it) }.orEmpty()
            fun number(key: String) = cursor.getColumnIndex(key).takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0
            while (cursor.moveToNext()) {
                val duration = number("duration")
                if (hideShort && duration < 30_000) continue
                val relative = text("relative_path")
                if (relative.split('/').any { it.equals("Recordings",true) || it.equals("Voice Recorder",true) }) continue
                result += Song(ContentUris.withAppendedId(collection,number("_id")).toString(),text("title").ifBlank { text("_display_name") },
                    text("artist").takeUnless { it.isBlank() || it == "<unknown>" } ?: "Unknown artist",text("album").ifBlank { "Unknown album" },
                    duration,number("album_id"),number("track").toInt(),number("year").toInt(),number("date_added"),volume,relative,text("_display_name"))
            }
        }
        result
    }.distinctBy { it.uri }
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
