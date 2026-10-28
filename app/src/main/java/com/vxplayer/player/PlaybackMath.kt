package com.vxplayer.player

fun seekPreview(start: Long, fraction: Float, duration: Long): Long {
    val span = if (duration > 0L) duration else 120_000L
    return (start + (fraction * span).toLong()).coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
}
