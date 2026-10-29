package com.vxplayer.player

import java.util.Locale

object SrtTiming {
    private val timing = Regex("""^\s*(\d{1,4}:[0-5]\d:[0-5]\d[,.]\d{3})\s*-->\s*(\d{1,4}:[0-5]\d:[0-5]\d[,.]\d{3})(.*)$""")
    private fun millis(value: String): Long {
        val fields = value.replace('.', ',').split(':', ',')
        return fields[0].toLong() * 3_600_000 + fields[1].toLong() * 60_000 + fields[2].toLong() * 1000 + fields[3].toLong()
    }
    private fun timestamp(ms: Long): String = String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
    /** Positive delay makes captions appear later. Original subtitle content is never modified. */
    fun shift(source: String, delayMs: Int): String {
        require(delayMs in -60_000..60_000) { "Subtitle delay is limited to ±60 seconds" }
        val text = source.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return ""
        val result = mutableListOf<String>()
        var parsed = 0
        for (block in text.split(Regex("\n[ \t]*\n+"))) {
            val lines = block.lines()
            val index = lines.indexOfFirst { timing.matches(it) }
            if (index !in 0..1) continue
            val match = requireNotNull(timing.matchEntire(lines[index]))
            val start = millis(match.groupValues[1]); val end = millis(match.groupValues[2])
            if (end <= start) continue
            parsed++
            val cue = SubtitleText.cleanCue(lines.drop(index + 1).joinToString("\n"))
            if (cue.isBlank()) continue
            if (end + delayMs <= 0L) continue
            val shifted = "${timestamp((start + delayMs).coerceAtLeast(0))} --> ${timestamp(end + delayMs)}${match.groupValues[3]}"
            result.add("${result.size + 1}\n$shifted\n$cue")
        }
        require(parsed > 0) { "No SRT cues found" }
        return result.joinToString("\n\n", postfix = if (result.isEmpty()) "" else "\n")
    }
}
