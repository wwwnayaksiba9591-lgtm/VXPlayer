package com.vxplayer.player

// Persisted names stay stable: existing Low/Medium/High selections automatically use the new scale.
enum class SeekSensitivity(val seconds: Int) {
    Low(60), Medium(120), High(240);
    val label get() = "$name · $seconds s / full width"
}

fun seekPreview(start: Long, distanceDp: Float, widthDp: Float, duration: Long,
    sensitivity: SeekSensitivity = SeekSensitivity.Medium): Long {
    val fraction = if (distanceDp.isFinite() && widthDp.isFinite() && widthDp > 0f) (distanceDp / widthDp).coerceIn(-1f, 1f) else 0f
    return (start + (fraction * sensitivity.seconds * 1000).toLong())
        .coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
}

/** A diagonal movement stays undecided until one axis clearly dominates. */
fun swipeAxis(dxDp: Float, dyDp: Float): Int = when {
    kotlin.math.abs(dxDp) >= 16f && kotlin.math.abs(dxDp) > kotlin.math.abs(dyDp) * 1.5f -> 2
    kotlin.math.abs(dyDp) >= 16f && kotlin.math.abs(dyDp) > kotlin.math.abs(dxDp) * 1.5f -> 1
    else -> 0
}
