package com.vxplayer.player

import com.vxplayer.settings.BackupCodec
import com.vxplayer.settings.SettingsBackup
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private fun json(settings: String = "{}", positions: String = "{}", version: Int = 1) =
        """{"format":"VXPlayer","version":$version,"settings":$settings,"positions":$positions}"""
    @Test fun roundTripPreservesTypesNegativeColorsAndLongPositions() {
        val data = SettingsBackup(mapOf("theme" to "Light", "autoNext" to false, "defaultSpeed" to 1.25f,
            "subtitleBackground" to 0x99000000.toInt(), "maxBoost" to 150, "srtDelay:${"a".repeat(64)}" to -100),
            mapOf("content://media/external/video/media/1" to 5_000_000_000L, "https://example.com/video?token=a%2Bb" to 100L))
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
    }
    @Test fun noWarningConsentOrRecentUrlListIsPortable() {
        listOf("boostWarningAcknowledged", "recentStreamUrls", "streamType:https://example.com", "subtitleUri").forEach {
            assertFalse(BackupCodec.supports(it))
        }
    }
    @Test fun rejectsInvalidPreferencesBeforeRestore() {
        listOf("""{"theme":"Invisible"}""", """{"maxBoost":999}""", """{"maxBoost":"200"}""",
            """{"autoNext":"false"}""", """{"defaultAspect":1.5}""", """{"defaultSpeed":0.6}""",
            """{"enhanceSharpness":20}""", """{"boostWarningAcknowledged":true}""").forEach {
            assertThrows(Exception::class.java) { BackupCodec.decode(json(settings = it)) }
        }
    }
    @Test fun rejectsInvalidResumeValuesAndSchemes() {
        listOf("""{"content://v/1":-1}""", """{"content://v/1":1.5}""", """{"content://v/1":9223372036854775808}""",
            """{"javascript:alert(1)":10}""", """{"content://v/1":"100"}""").forEach {
            assertThrows(Exception::class.java) { BackupCodec.decode(json(positions = it)) }
        }
    }
    @Test fun rejectsUnsupportedVersionDuplicatesMissingFieldsAndTrailingData() {
        listOf(json(version = 2), json(settings = """{"theme":"Dark","theme":"Light"}"""),
            json(positions = """{"content://v/1":10,"content://v/1":20}"""), "{}", json() + "{}",
            """{"format":"VXPlayer","version":1,"version":1,"settings":{},"positions":{}}""").forEach {
            assertThrows(Exception::class.java) { BackupCodec.decode(it) }
        }
    }
    @Test fun rejectsOversizedAndMalformedJson() {
        listOf(" ".repeat(BackupCodec.MAX_BYTES + 1), "not json", json(settings = """{"theme":null}"""),
            """{/* comment */"format":"VXPlayer","version":1,"settings":{},"positions":{}}""").forEach {
            assertThrows(Exception::class.java) { BackupCodec.decode(it) }
        }
    }
}
