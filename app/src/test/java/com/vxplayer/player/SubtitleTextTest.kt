package com.vxplayer.player

import java.nio.charset.Charset
import org.junit.Assert.*
import org.junit.Test

class SubtitleTextTest {
    @Test fun decodesUnicodeAndIndicTextWithAndWithoutBom() {
        val text = "1\n00:00:01,000 --> 00:00:03,000\nनमस्ते भारत · தமிழ் · Hello"
        assertEquals(text, SubtitleText.decode(text.toByteArray(Charsets.UTF_8)))
        assertEquals(text, SubtitleText.decode(("\uFEFF" + text).toByteArray(Charsets.UTF_8)))
        for (charset in listOf(Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            assertEquals(text, SubtitleText.decode(("\uFEFF" + text).toByteArray(charset)))
        }
    }
    @Test fun legacyEncodingOverridesAreReliableWhenDetectionIsAmbiguous() {
        for ((encoding, text) in listOf("windows-1252" to "Café déjà vu – bientôt", "windows-1250" to "Zażółć gęślą jaźń", "windows-1251" to "Привет мир")) {
            assertEquals(text, SubtitleText.decode(text.toByteArray(Charset.forName(encoding)), encoding))
        }
        assertTrue(SubtitleText.decode("Café déjà vu – bientôt".toByteArray(Charset.forName("windows-1252"))).contains("Café"))
        assertEquals("Zażółć gęślą jaźń", SubtitleText.decode("Zażółć gęślą jaźń".toByteArray(Charset.forName("windows-1250"))))
    }
    @Test fun mixedMalformedAndEmptyCuesDoNotDiscardValidCaptions() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\n{\\an8}<b>Hello</b><br/>World\n\n2\nBAD --> TIMING\nOops\n\n3\n00:00:03,000 --> 00:00:04,000\n\n4\n00:00:04,000 --> 00:00:05,000\nCaption"
        val shifted = SrtTiming.shift(text, 100)
        assertTrue(shifted.contains("Hello\nWorld")); assertTrue(shifted.contains("Caption"))
        assertFalse(shifted.contains("<b>")); assertFalse(shifted.contains("\\an8")); assertFalse(shifted.contains("Oops"))
        assertEquals("", SrtTiming.shift("", 0))
    }
}
