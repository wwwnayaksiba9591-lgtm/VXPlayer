package com.vxplayer.ui

data class ResumeVideo(val uri: String, val title: String, val folder: String = "", val positionMs: Long = 0,
    val durationMs: Long = 0, val playedAt: Long = 0, val watched: Boolean = false,
    val accessible: Boolean = true, val privateVideo: Boolean = false)

/** An unfinished checkpoint wins; if every accessible video finished, replay the latest. */
fun selectResumeVideo(recent: List<ResumeVideo>): ResumeVideo? {
    val eligible = recent.filter { it.accessible && !it.privateVideo }.sortedByDescending { it.playedAt }
    return eligible.firstOrNull { it.positionMs > 0 && (it.durationMs <= 0 || it.positionMs < it.durationMs) }
        ?: eligible.firstOrNull()
}
