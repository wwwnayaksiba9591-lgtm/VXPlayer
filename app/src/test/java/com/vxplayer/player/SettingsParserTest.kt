package com.vxplayer.player

import com.vxplayer.settings.*
import com.vxplayer.diagnostics.sanitizeCrashText
import org.junit.Assert.*
import org.junit.Test

class SettingsParserTest {
    @Test fun missingAndCorruptValuesUseDefaults() {
        assertEquals(PlaybackDefaults(), SettingsParser.parse(emptyMap<String,Any>()))
        val bad = mapOf("defaultSpeed" to Float.NaN,"defaultAspect" to "garbage","autoNext" to 23,
            "seekSensitivity" to "old-seek","playbackMode" to 10,"theme" to "removed","accent" to listOf("bad"),"maxBoost" to Long.MAX_VALUE)
        assertEquals(PlaybackDefaults(),SettingsParser.parse(bad))
    }
    @Test fun validLegacyValuesAndPresetNamesAreSafe() {
        val value = SettingsParser.parse(mapOf("defaultSpeed" to "1.25","seekSensitivity" to "high","theme" to "dark","defaultAspect" to 3L))
        assertEquals(1.25f,value.speed); assertEquals(SeekSensitivity.High,value.seekSensitivity)
        assertEquals("Dark",value.theme); assertEquals(3,value.aspect)
        assertEquals("Cinema",smartMode("Soft")); assertEquals("Sports/Action",smartMode("Sharp")); assertEquals("Auto",smartMode("garbage"))
    }
    @Test fun reportsKeepStackFramesButRedactSensitiveMessages() {
        val report = sanitizeCrashText("token=secret https://example.com/watch?key=abc\n/storage/emulated/0/private.mp4\nat com.vxplayer.settings.SettingsScreen(SettingsScreen.kt:23)")
        assertFalse(report.contains("token=secret")); assertFalse(report.contains("example.com")); assertFalse(report.contains("private.mp4"))
        assertTrue(report.contains("SettingsScreen.kt:23"))
    }
}
