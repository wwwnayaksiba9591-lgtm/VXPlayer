package com.vxplayer.player

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import androidx.compose.runtime.*
import com.vxplayer.settings.AppSettings
import kotlin.math.roundToInt

private object SessionVolume {
    var percent = 100
    var uri: String? = null
    var expectedIndex: Int? = null
}

/** Owns only this player's session effects; never attaches to the global audio mix. */
class AudioEnhancer(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = app.getSharedPreferences("vxplayer", 0)
    private var session = 0
    private var loudness: LoudnessEnhancer? = null
    private var bass: BassBoost? = null
    private var dynamics: AudioEffect? = null
    private var desiredOnAttach = 100
    var percent by mutableIntStateOf(systemPercent())
        private set
    var boostAvailable by mutableStateOf(false)
        private set
    var bassAvailable by mutableStateOf(false)
        private set
    var nightAvailable by mutableStateOf(false)
        private set
    var bassStrength by mutableIntStateOf(prefs.getInt("bassStrength", 0).coerceIn(0, 1000))
        private set
    var night by mutableStateOf(prefs.getBoolean("nightAudio", false))
        private set
    var warning by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private var pendingWarning: Int? = null
    private fun index() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    private fun systemPercent(): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) (index() * 100f / max).roundToInt().coerceIn(0, 100) else 0
    }
    fun newVideo(uri: String) {
        message = null
        val defaults = AppSettings.get(app).state.value
        if (SessionVolume.percent > 100 && SessionVolume.expectedIndex != index()) SessionVolume.percent = systemPercent()
        SessionVolume.percent = volumeAfterVideoChange(SessionVolume.percent, SessionVolume.uri != uri, defaults.rememberBoost)
        SessionVolume.uri = uri
        desiredOnAttach = if (SessionVolume.percent > 100) SessionVolume.percent else systemPercent()
        percent = systemPercent()
    }
    fun attach(id: Int) {
        if (id == session && id > 0) return
        val desired = if (session > 0) percent else desiredOnAttach
        release()
        if (id <= 0) return
        session = id
        loudness = try { LoudnessEnhancer(id) } catch (_: RuntimeException) { null }
        boostAvailable = loudness != null && !audio.isVolumeFixed && audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) > 0
        bass = try { BassBoost(0, id).also { if (!it.strengthSupported) { it.release(); error("Unsupported bass control") } } } catch (_: RuntimeException) { null }
        bassAvailable = bass != null
        dynamics = if (Build.VERSION.SDK_INT >= 28) try { createNightCompression(id) } catch (_: RuntimeException) { null } else null
        nightAvailable = dynamics != null
        updateBass(bassStrength)
        updateNight(night)
        // A remembered boost must still respect the warning and current settings.
        request(if (desired > 100) desired else systemPercent())
    }
    fun request(requested: Int) {
        val defaults = AppSettings.get(app).state.value
        var target = boundedVolume(requested, defaults.allowBoost, defaults.maxBoost)
        if (!boostAvailable) target = target.coerceAtMost(100)
        if (needsBoostWarning(target, prefs.getBoolean("boostWarningAcknowledged", false))) {
            pendingWarning = target; warning = true
            return
        }
        apply(target)
    }
    fun acknowledgeWarning(accept: Boolean) {
        val target = pendingWarning; pendingWarning = null; warning = false
        if (accept && target != null) {
            prefs.edit().putBoolean("boostWarningAcknowledged", true).apply()
            request(target)
        }
    }
    private fun apply(target: Int) {
        if (audio.isVolumeFixed) { message = "Volume is controlled by this device."; return }
        val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        try {
            if (target <= 100) loudness?.enabled = false
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (target.coerceAtMost(100) * maximum / 100f).roundToInt(), 0)
            if (target > 100) {
                loudness?.setTargetGain(loudnessGainMb(target))
                loudness?.enabled = true
            }
            percent = if (target > 100) target else systemPercent()
            message = null
            SessionVolume.percent = percent; SessionVolume.expectedIndex = index()
        } catch (_: RuntimeException) {
            try { loudness?.enabled = false } catch (_: RuntimeException) { }
            boostAvailable = false; percent = systemPercent(); SessionVolume.percent = percent
            message = "Audio boost is unavailable on this audio output."
        }
    }
    fun syncSystemVolume() {
        val current = index()
        if (SessionVolume.expectedIndex != current) {
            try { loudness?.enabled = false } catch (_: RuntimeException) { }
            percent = systemPercent(); SessionVolume.percent = percent; SessionVolume.expectedIndex = current
        }
        val defaults = AppSettings.get(app).state.value
        val allowed = boundedVolume(percent, defaults.allowBoost, defaults.maxBoost)
        if (allowed < percent) request(allowed)
    }
    fun updateBass(value: Int) {
        bassStrength = value.coerceIn(0, 1000)
        prefs.edit().putInt("bassStrength", bassStrength).apply()
        try { bass?.setStrength(bassStrength.toShort()); bass?.enabled = bassStrength > 0 }
        catch (_: RuntimeException) { bassAvailable = false; message = "Bass boost is unavailable on this output." }
    }
    fun updateNight(enabled: Boolean) {
        night = enabled; prefs.edit().putBoolean("nightAudio", enabled).apply()
        try { dynamics?.enabled = enabled } catch (_: RuntimeException) { nightAvailable = false; message = "Night audio is unavailable on this output." }
    }
    fun release() {
        listOfNotNull(loudness, bass, dynamics).forEach { try { it.release() } catch (_: RuntimeException) { } }
        loudness = null; bass = null; dynamics = null; session = 0
        boostAvailable = false; bassAvailable = false; nightAvailable = false
        warning = false; pendingWarning = null
    }
}
