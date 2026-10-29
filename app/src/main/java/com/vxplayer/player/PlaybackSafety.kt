package com.vxplayer.player

fun supportedAudioIndex(supported: List<Boolean>, selected: Int = -1): Int =
    if (selected in supported.indices && supported[selected]) selected else supported.indexOfFirst { it }

fun unsafeVideoEffects(mime: String?, codecs: String?, transfer: Int, bitDepth: Int, protected: Boolean): Boolean {
    val code = codecs.orEmpty().lowercase()
    return protected || transfer in listOf(6, 7) || bitDepth > 8 || mime == "video/dolby-vision" ||
        code.startsWith("dv") || code.matches(Regex("(?:hvc1|hev1)\\.2(?:\\..*)?")) ||
        code.matches(Regex("avc[13]\\.(?:6e|7a|f4)[0-9a-f]{4}")) ||
        (code.startsWith("vp09.") && code.split('.').getOrNull(3)?.toIntOrNull()?.let { it > 8 } == true) ||
        (code.startsWith("av01.") && code.split('.').getOrNull(3)?.toIntOrNull()?.let { it > 8 } == true)
}

fun shouldRetryWithoutEffects(effectsUsed: Boolean, alreadyDisabled: Boolean) = effectsUsed && !alreadyDisabled

fun boundedSeek(position: Long, duration: Long): Long = position.coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
fun seekWithOffset(position: Long, delta: Long, duration: Long): Long {
    val target = if (delta > 0 && position > Long.MAX_VALUE - delta) Long.MAX_VALUE
        else if (delta < 0 && position < Long.MIN_VALUE - delta) 0L else position + delta
    return boundedSeek(target, duration)
}
fun preferSyncSeek(mime: String?, name: String): Boolean = mime in setOf("video/x-matroska", "video/mp2t", "video/avi", "video/x-msvideo") ||
    name.substringBefore('?').substringAfterLast('.').lowercase() in setOf("mkv", "avi", "ts", "m2ts", "mpeg", "mpg")

fun displayDimensions(width: Int, height: Int, rotation: Int, pixelRatio: Float = 1f): Pair<Int, Int> {
    val ratio = pixelRatio.takeIf { it.isFinite() && it > 0 } ?: 1f
    val corrected = (width.coerceAtLeast(0).toDouble() * ratio).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
    return if (Math.floorMod(rotation, 180) == 90) height.coerceAtLeast(0) to corrected else corrected to height.coerceAtLeast(0)
}

fun sanitizeProblemText(text: String): String = text.take(12_000)
    .replace(Regex("(?i)(?:https?|content|file)://[^\\s\"'<>]+"), "[link redacted]")
    .replace(Regex("(?:/(?:storage|sdcard|data|mnt|home)/|[A-Za-z]:[\\\\/])[^\\r\\n]+"), "[path redacted]")
    .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[account redacted]")
    .replace(Regex("(?i)(?:token|signature|password|authorization|cookie|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+"), "[secret redacted]")
    .replace(Regex("[\\p{Cntrl}&&[^\\n\\t]]"), " ").take(12_000)

fun reportFileName(title: String, network: Boolean, privateVideo: Boolean): String = when {
    privateVideo -> "Private video"
    network -> "Network stream"
    title.contains("://") -> "Video"
    else -> sanitizeProblemText(title.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').substringBefore('#')).ifBlank { "Video" }
}
