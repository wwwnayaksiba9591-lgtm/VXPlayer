package com.vxplayer.music

/** Include short clips, voice notes and system sounds; category/folder flags never hide audio. */
object AudioLibraryPolicy {
    val extensions = setOf("mp3","m4a","aac","flac","wav","ogg","opus","amr","3ga","aiff","aif","wma","ape","alac")
    fun isAudio(mime: String?, name: String): Boolean = mime.orEmpty().startsWith("audio/",true) ||
        name.substringAfterLast('.',"").lowercase() in extensions
    fun map(uri: String, title: String?, name: String?, artist: String?, album: String?, duration: Long,
        albumId: Long = 0, track: Int = 0, year: Int = 0, added: Long = 0, volume: String, relativePath: String = ""): Song {
        fun known(value: String?, fallback: String) = value?.trim()?.takeUnless { it.isEmpty() || it.equals("<unknown>",true) } ?: fallback
        val file = name.orEmpty()
        return Song(uri,known(title,file.ifBlank { "Audio" }),known(artist,"Unknown artist"),known(album,"Unknown album"),
            duration.coerceAtLeast(0),albumId,track,year,added,volume,relativePath,file)
    }
}

data class AudioScanResult(val songs: List<Song>,val unavailableVolumes: Set<String>)
