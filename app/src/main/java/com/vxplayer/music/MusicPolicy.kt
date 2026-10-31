package com.vxplayer.music

import kotlin.random.Random

@androidx.compose.runtime.Immutable
data class Song(val uri: String, val title: String, val artist: String = "Unknown artist", val album: String = "Unknown album",
    val duration: Long = 0, val albumId: Long = 0, val track: Int = 0, val year: Int = 0, val added: Long = 0,
    val volume: String = "external", val relativePath: String = "", val fileName: String = "")
enum class MusicSort { Title, Artist, Album, Duration, RecentlyAdded }
fun sortSongs(songs: List<Song>, sort: MusicSort): List<Song> = when (sort) {
    MusicSort.Title -> songs.sortedWith(compareBy<Song> { it.title.lowercase() }.thenBy { it.uri })
    MusicSort.Artist -> songs.sortedWith(compareBy<Song> { it.artist.lowercase() }.thenBy { it.title.lowercase() }.thenBy { it.uri })
    MusicSort.Album -> songs.sortedWith(compareBy<Song> { it.album.lowercase() }.thenBy { it.track }.thenBy { it.title.lowercase() })
    MusicSort.Duration -> songs.sortedBy { it.duration }
    MusicSort.RecentlyAdded -> songs.sortedByDescending { it.added }
}
fun <T> reorderMusic(items: List<T>, from: Int, to: Int): List<T> = if (from !in items.indices || to !in items.indices) items else items.toMutableList().apply { add(to,removeAt(from)) }
fun <T> addPlaylistSong(items: List<T>, song: T): List<T> = if (song in items) items else items + song
fun <T> insertMusic(items: List<T>, current: Int, song: T, next: Boolean): List<T> = items.toMutableList().apply { add(if (next) (current + 1).coerceIn(0,size) else size,song) }
fun <T> removeMusic(items: List<T>, index: Int): List<T> = items.filterIndexed { i,_ -> i != index }
fun <T> shuffledMusic(items: List<T>, first: T?, random: Random = Random.Default): List<T> =
    items.distinct().filter { it != first }.shuffled(random).let { if (first != null && first in items) listOf(first) + it else it }
fun nextMusicRepeat(mode: Int) = when (mode) { 0 -> 2; 2 -> 1; else -> 0 } // Media3 off/all/one

/** Bounded traversal bypasses repeat-one so a broken track cannot trap playback. */
fun nextMusicAfterError(ids: List<String>, current: Int, failed: Set<String>, order: List<Int> = ids.indices.toList(), wrap: Boolean = false): Int? {
    val validOrder=order.filter { it in ids.indices }.distinct()
    val offset=validOrder.indexOf(current)
    val candidates=if(offset<0)validOrder else validOrder.drop(offset+1) + if(wrap)validOrder.take(offset) else emptyList()
    return candidates.firstOrNull { ids[it] !in failed }
}

fun Song.folderKey() = "$volume:${relativePath.trimEnd('/')}"
fun Song.folderLabel() = relativePath.trimEnd('/').substringAfterLast('/').ifBlank { if(volume=="internal")"Internal audio" else "Shared audio" }
fun <T> musicQueueWindow(items: List<T>, index: Int, limit: Int = 5000): Pair<List<T>,Int> {
    if (items.isEmpty()) return emptyList<T>() to 0
    val selected = index.coerceIn(items.indices)
    val count = limit.coerceAtLeast(1)
    val start = (selected-count/2).coerceIn(0,(items.size-count).coerceAtLeast(0))
    return items.drop(start).take(count) to (selected-start)
}
data class LyricLine(val timeMs: Long, val text: String)
fun parseLrc(input: String): List<LyricLine> {
    var offset = 0L
    Regex("\\[offset:([+-]?[0-9]+)]",RegexOption.IGNORE_CASE).find(input)?.groupValues?.get(1)?.toLongOrNull()?.let { offset = it.coerceIn(-600_000,600_000) }
    val stamp = Regex("\\[([0-9]{1,3}):([0-9]{2})(?:[.:]([0-9]{1,3}))?]")
    return input.take(262_144).lineSequence().flatMap { line ->
        val text = line.replace(stamp,"").replace(Regex("<[^>]*>"),"").trim()
        stamp.findAll(line).mapNotNull { match ->
            val min = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val sec = match.groupValues[2].toLongOrNull()?.takeIf { it < 60 } ?: return@mapNotNull null
            val fraction = match.groupValues[3].padEnd(3,'0').toLongOrNull() ?: 0
            if (text.isEmpty()) null else LyricLine((min*60_000+sec*1000+fraction+offset).coerceAtLeast(0),text)
        }
    }.sortedBy { it.timeMs }.take(10_000).toList()
}
fun activeLyric(lines: List<LyricLine>, position: Long) = lines.indexOfLast { it.timeMs <= position }
