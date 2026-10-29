package com.vxplayer.player

import kotlin.random.Random

enum class PlaybackMode(val label: String) {
    Off("Off"), RepeatOne("Repeat one"), RepeatFolder("Repeat folder"), Shuffle("Shuffle");
    fun next() = entries[(ordinal + 1) % entries.size]
}

data class PlaylistCursor(val history: List<String> = emptyList(), val index: Int = 0, val remaining: List<String> = emptyList())
data class PlaylistStep(val video: PlaylistVideo, val cursor: PlaylistCursor)

/** Manual Next skips Repeat one; only end-of-video repeats the same item. */
fun playlistStep(videos: List<PlaylistVideo>, current: String?, mode: PlaybackMode, offset: Int,
    cursor: PlaylistCursor = PlaylistCursor(), atEnd: Boolean = false, random: Random = Random.Default): PlaylistStep? {
    val position = videos.indexOfFirst { it.uri == current }
    if (position < 0 || offset !in listOf(-1, 1)) return null
    if (atEnd && mode == PlaybackMode.RepeatOne) return PlaylistStep(videos[position], cursor)
    if (mode != PlaybackMode.Shuffle) {
        val target = if (mode == PlaybackMode.RepeatFolder) Math.floorMod(position + offset, videos.size) else position + offset
        return videos.getOrNull(target)?.let { PlaylistStep(it, cursor) }
    }
    val ids = videos.map { it.uri }
    var state = cursor.takeIf { it.history.getOrNull(it.index) == current && it.history.all { id -> id in ids } }
        ?: PlaylistCursor(listOf(requireNotNull(current)), 0, ids.filter { it != current }.shuffled(random))
    if (offset < 0) {
        if (state.index == 0) return null
        state = state.copy(index = state.index - 1)
    } else if (state.index < state.history.lastIndex) {
        state = state.copy(index = state.index + 1)
    } else {
        var remaining = state.remaining.filter { it in ids }
        if (remaining.isEmpty()) {
            remaining = ids.shuffled(random)
            // Avoid an immediate repeat between cycles when another video exists.
            if (remaining.size > 1 && remaining.first() == current) remaining = remaining.drop(1) + remaining.first()
        }
        val history = (state.history + remaining.first()).takeLast(1000)
        state = PlaylistCursor(history, history.lastIndex, remaining.drop(1))
    }
    return PlaylistStep(videos.first { it.uri == state.history[state.index] }, state)
}
