package com.vxplayer.settings

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.StringReader
import java.io.StringWriter
import java.math.BigDecimal
import java.net.URI

/** Only playback preferences are portable; permissions, warning consent and URL history are excluded. */
data class SettingsBackup(val settings: Map<String, Any>, val positions: Map<String, Long>)

object BackupCodec {
    const val MAX_BYTES = 4 * 1024 * 1024
    private val booleans = setOf("autoNext", "dynamicColor", "allowBoost", "rememberBoost", "rememberEnhance", "descending", "grid", "nightAudio", "enhanceEnabled")
    private val floats = mapOf("defaultSpeed" to .5f..2f, "subtitleSize" to 12f..36f,
        "enhanceBrightness" to -0.25f..0.25f, "enhanceContrast" to -0.5f..0.5f,
        "enhanceSaturation" to 0f..2f, "enhanceSharpness" to -0.5f..1f, "enhanceWarmth" to -0.2f..0.2f)
    private val integers = mapOf("defaultAspect" to 0..3, "maxBoost" to 150..200, "bassStrength" to 0..1000,
        "subtitleColor" to Int.MIN_VALUE..Int.MAX_VALUE, "subtitleBackground" to Int.MIN_VALUE..Int.MAX_VALUE)
    private val choices = mapOf("theme" to setOf("System", "Light", "Dark"),
        "seekSensitivity" to setOf("Low", "Medium", "High"),
        "accent" to setOf("Orange", "Blue", "Teal", "Green", "Purple"),
        "playbackMode" to setOf("Off", "RepeatOne", "RepeatFolder", "Shuffle"),
        "defaultOrientation" to setOf("Auto", "System", "Portrait", "Landscape"),
        "sort" to setOf("Name", "Date", "Size", "Duration"),
        "enhancePreset" to setOf("Auto", "Original", "Vivid", "Cinema", "Sports/Action", "Anime/Cartoon", "Soft", "Sharp", "Night", "Custom"),
        "smartLastMode" to setOf("Auto", "Vivid", "Cinema", "Sports/Action", "Anime/Cartoon", "Night", "Custom"))
    private val delayKey = Regex("srtDelay:[0-9a-f]{64}")
    fun supports(key: String) = key in booleans || key in floats || key in integers || key in choices || delayKey.matches(key)

    private fun setting(key: String, raw: Any): Any {
        require(supports(key)) { "Unsupported setting: $key" }
        return when {
            key in booleans -> { require(raw is Boolean); raw }
            key in choices -> { require(raw is String && raw in choices.getValue(key)); raw }
            key in floats -> {
                val value = (raw as? Number)?.toFloat() ?: error("Invalid numeric setting")
                require(value.isFinite() && value in floats.getValue(key))
                if (key == "defaultSpeed") require(value in listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f))
                value
            }
            else -> {
                require(raw is Number)
                val value = BigDecimal(raw.toString()).intValueExact()
                require(value in (integers[key] ?: -60_000..60_000))
                if (key == "maxBoost") require(value == 150 || value == 200)
                value
            }
        }
    }
    private fun position(uri: String, value: Long) {
        require(uri.length in 1..4096 && uri.none { it.isISOControl() })
        require(URI(uri).scheme?.lowercase() in setOf("content", "file", "http", "https"))
        require(value in 1L..315_576_000_000L) { "Invalid resume position" }
    }
    fun encode(data: SettingsBackup): String {
        require(data.settings.size <= 2048 && data.positions.size <= 10_000)
        val output = StringWriter()
        JsonWriter(output).use { writer ->
            writer.setIndent("  ")
            writer.beginObject().name("format").value("VXPlayer").name("version").value(1)
            writer.name("settings").beginObject()
            data.settings.toSortedMap().forEach { (key, raw) ->
                val value = setting(key, raw)
                writer.name(key)
                when (value) {
                    is Boolean -> writer.value(value)
                    is Number -> writer.value(value)
                    is String -> writer.value(value)
                }
            }
            writer.endObject().name("positions").beginObject()
            data.positions.toSortedMap().forEach { (uri, value) -> position(uri, value); writer.name(uri).value(value) }
            writer.endObject().endObject()
        }
        return output.toString().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) }
    }
    fun decode(json: String): SettingsBackup {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup exceeds 4 MiB" }
        val settings = linkedMapOf<String, Any>()
        val positions = linkedMapOf<String, Long>()
        val fields = mutableSetOf<String>()
        JsonReader(StringReader(json)).use { reader ->
            reader.strictness = Strictness.STRICT
            reader.beginObject()
            while (reader.hasNext()) {
                val field = reader.nextName()
                require(fields.add(field)) { "Duplicate backup field" }
                when (field) {
                    "format" -> { require(reader.peek() == JsonToken.STRING && reader.nextString() == "VXPlayer") }
                    "version" -> { require(reader.peek() == JsonToken.NUMBER && BigDecimal(reader.nextString()).intValueExact() == 1) { "Unsupported backup version" } }
                    "settings" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            require(key !in settings && settings.size < 2048)
                            val raw: Any = when (reader.peek()) {
                                JsonToken.STRING -> reader.nextString()
                                JsonToken.BOOLEAN -> reader.nextBoolean()
                                JsonToken.NUMBER -> BigDecimal(reader.nextString())
                                else -> error("Invalid setting value")
                            }
                            settings[key] = setting(key, raw)
                        }
                        reader.endObject()
                    }
                    "positions" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val uri = reader.nextName()
                            require(uri !in positions && positions.size < 10_000 && reader.peek() == JsonToken.NUMBER)
                            val ms = BigDecimal(reader.nextString()).longValueExact()
                            position(uri, ms); positions[uri] = ms
                        }
                        reader.endObject()
                    }
                    else -> error("Unknown backup field")
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT && fields == setOf("format", "version", "settings", "positions"))
        }
        return SettingsBackup(settings, positions)
    }
}
