package com.vxplayer.player

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import org.mozilla.universalchardet.UniversalDetector

object SubtitleText {
    val encodings = listOf("Auto", "UTF-8", "UTF-16LE", "UTF-16BE", "windows-1252", "windows-1250", "windows-1251", "x-ISCII91")
        .filter { it == "Auto" || Charset.isSupported(it) }
    fun decode(bytes: ByteArray, encoding: String = "Auto"): String {
        require(encoding in encodings) { "Unsupported subtitle encoding" }
        val detected = when {
            encoding != "Auto" -> encoding
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> "UTF-16LE"
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> "UTF-16BE"
            bytes.size >= 8 && bytes.take(1000).filterIndexed { index, _ -> index % 2 == 1 }.count { it == 0.toByte() } > minOf(bytes.size / 5, 100) -> "UTF-16LE"
            bytes.size >= 8 && bytes.take(1000).filterIndexed { index, _ -> index % 2 == 0 }.count { it == 0.toByte() } > minOf(bytes.size / 5, 100) -> "UTF-16BE"
            runCatching { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)) }.isSuccess -> "UTF-8"
            Charset.isSupported("x-ISCII91") && (0 until bytes.lastIndex).any { bytes[it] == 0xEF.toByte() && (bytes[it + 1].toInt() and 255) in 0x40..0x4B } -> "x-ISCII91"
            else -> {
                val western = bytes.toString(Charset.forName("windows-1252"))
                val central = bytes.toString(Charset.forName("windows-1250"))
                val centralLetters = central.filter { it.lowercaseChar() in "ąćęłńśźżčďěňřšťů" }.map { it.lowercaseChar() }.distinct().size
                val detector = UniversalDetector(null)
                detector.handleData(bytes, 0, minOf(bytes.size, 65536)); detector.dataEnd()
                if (centralLetters >= 3 && (western.any { it == '\uFFFD' } || western.count { it in "¹²³¤¿" } >= 2)) "windows-1250"
                else detector.detectedCharset?.takeIf { Charset.isSupported(it) } ?: "windows-1252"
            }
        }
        return bytes.toString(Charset.forName(detected)).removePrefix("\uFEFF")
    }
    fun cleanCue(text: String): String = text
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("<[^>\\r\\n]{0,512}>"), "")
        .replace(Regex("\\{\\\\[^}\\r\\n]{0,512}}"), "")
        .replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
        .trim()
}
