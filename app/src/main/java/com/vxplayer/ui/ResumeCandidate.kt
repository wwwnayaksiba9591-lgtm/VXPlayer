package com.vxplayer.ui

/** Prefer an unfinished checkpoint, retaining most-recent ordering; otherwise replay the latest. */
fun <T> resumeCandidate(recent: List<T>, position: (T) -> Long): T? = recent.firstOrNull { position(it) > 0L } ?: recent.firstOrNull()
