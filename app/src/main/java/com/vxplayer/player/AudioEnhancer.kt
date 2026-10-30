package com.vxplayer.player

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.media.audiofx.Equalizer
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
    private var virtualizer: Virtualizer? = null
    private var equalizer: Equalizer? = null
    private var smart: SmartAudioProfile? = null
    private var blocked = false
    val effectsInUse get() = listOfNotNull(loudness, bass, dynamics, virtualizer, equalizer).any { runCatching { it.enabled }.getOrDefault(false) }
    fun blockEffects(value: Boolean) { blocked = value; if (value) release() }
    var smartAvailable by mutableStateOf(false)
        private set
    private var desiredOnAttach = systemPercent()
    var percent by mutableIntStateOf(systemPercent())
        private set
    var boostAvailable by mutableStateOf(false)
        private set
    var bassAvailable by mutableStateOf(false)
        private set
    var nightAvailable by mutableStateOf(false)
        private set
    var bassStrength by mutableIntStateOf(com.vxplayer.settings.SettingsParser.integer(prefs.all["bassStrength"], 0).coerceIn(0, 1000))
        private set
    var night by mutableStateOf(com.vxplayer.settings.SettingsParser.boolean(prefs.all["nightAudio"], false))
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
        if (blocked) return
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
        virtualizer = try { Virtualizer(0, id).also { if (!it.strengthSupported) { it.release(); error("Unavailable") } } } catch (_: RuntimeException) { null }
        equalizer = try { Equalizer(0, id) } catch (_: RuntimeException) { null }
        smartAvailable = bass != null || dynamics != null || virtualizer != null || equalizer != null
        updateBass(bassStrength)
        updateNight(night)
        applySmartEffects()
        // A remembered boost must still respect the warning and current settings.
        request(if (desired > 100) desired else systemPercent())
    }
    fun request(requested: Int) {
        val defaults = AppSettings.get(app).state.value
        var target = boundedVolume(requested, defaults.allowBoost, defaults.maxBoost)
        if (!boostAvailable) target = target.coerceAtMost(100)
        if (needsBoostWarning(target, com.vxplayer.settings.SettingsParser.boolean(prefs.all["boostWarningAcknowledged"], false))) {
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
            if (target <= 100) disableGain()
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (target.coerceAtMost(100) * maximum / 100f).roundToInt(), 0)
            if (target > 100) {
                val effect = requireNotNull(loudness)
                effect.setTargetGain(loudnessGainMb(target))
                check(effect.setEnabled(true) == AudioEffect.SUCCESS && effect.enabled)
            }
            percent = if (target > 100) target else systemPercent()
            message = null
            SessionVolume.percent = percent; SessionVolume.expectedIndex = index()
        } catch (_: RuntimeException) {
            try { loudness?.release() } catch (_: RuntimeException) { }
            loudness = null
            boostAvailable = false; percent = systemPercent(); SessionVolume.percent = percent
            message = "Audio boost is unavailable on this audio output."
        }
    }
    fun syncSystemVolume() {
        val current = index()
        if (SessionVolume.expectedIndex != current) {
            disableGain()
            percent = systemPercent(); SessionVolume.percent = percent; SessionVolume.expectedIndex = current
        }
        val defaults = AppSettings.get(app).state.value
        val allowed = boundedVolume(percent, defaults.allowBoost, defaults.maxBoost)
        if (allowed < percent) request(allowed)
    }
    fun updateBass(value: Int) {
        bassStrength = value.coerceIn(0, 1000)
        prefs.edit().putInt("bassStrength", bassStrength).apply()
        try {
            bass?.let { effect ->
                val effective = maxOf(bassStrength, smart?.bass ?: 0)
                effect.setStrength(effective.toShort())
                check(effect.setEnabled(effective > 0) == AudioEffect.SUCCESS)
            }
        } catch (_: RuntimeException) {
            try { bass?.release() } catch (_: RuntimeException) { }
            bass = null; bassAvailable = false; message = "Bass boost is unavailable on this output."
        }
    }
    fun updateNight(enabled: Boolean) {
        night = enabled; prefs.edit().putBoolean("nightAudio", enabled).apply()
        try { dynamics?.let { check(it.setEnabled(enabled || smart?.night == true) == AudioEffect.SUCCESS) } }
        catch (_: RuntimeException) {
            try { dynamics?.release() } catch (_: RuntimeException) { }
            dynamics = null; nightAvailable = false; message = "Night audio is unavailable on this output."
        }
    }
    fun smartProfile(mode: String?, active: Boolean) {
        smart = if (active && mode != null) smartAudioProfile(mode) else null
        applySmartEffects()
    }
    fun supportsSmart(mode: String): Boolean {
        val profile = smartAudioProfile(mode)
        return (bass != null && profile.bass > 0) || (virtualizer != null && profile.spatial > 0) ||
            equalizer != null || (dynamics != null && profile.night)
    }
    private fun applySmartEffects() {
        // Smart profiles layer over the user's manual audio settings without saving over them.
        try { bass?.let { effect ->
            val value = maxOf(bassStrength, smart?.bass ?: 0)
            effect.setStrength(value.toShort()); check(effect.setEnabled(value > 0) == AudioEffect.SUCCESS)
        } } catch (_: RuntimeException) { runCatching { bass?.release() }; bass = null; bassAvailable = false }
        try { dynamics?.let { check(it.setEnabled(night || smart?.night == true) == AudioEffect.SUCCESS) } }
        catch (_: RuntimeException) { runCatching { dynamics?.release() }; dynamics = null; nightAvailable = false }
        try { virtualizer?.let { effect ->
            effect.setStrength((smart?.spatial ?: 0).toShort()); check(effect.setEnabled(smart != null) == AudioEffect.SUCCESS)
        } } catch (_: RuntimeException) { runCatching { virtualizer?.release() }; virtualizer = null }
        try { equalizer?.let { effect ->
            val range = effect.bandLevelRange
            for (band in 0 until effect.numberOfBands) {
                val frequency = effect.getCenterFreq(band.toShort()) / 1000
                val mb = smart?.let { if (frequency < 250) it.lowMb else if (frequency > 4000) it.highMb else it.midMb } ?: 0
                effect.setBandLevel(band.toShort(), mb.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
            check(effect.setEnabled(smart != null) == AudioEffect.SUCCESS)
        } } catch (_: RuntimeException) { runCatching { equalizer?.release() }; equalizer = null }
        smartAvailable = bass != null || dynamics != null || virtualizer != null || equalizer != null
    }
    private fun disableGain() {
        val effect = loudness ?: return
        try { check(effect.setEnabled(false) == AudioEffect.SUCCESS && !effect.enabled) }
        catch (_: RuntimeException) {
            try { effect.release() } catch (_: RuntimeException) { }
            loudness = null; boostAvailable = false
        }
    }
    fun release() {
        listOfNotNull(loudness, bass, dynamics, virtualizer, equalizer).forEach { try { it.release() } catch (_: RuntimeException) { } }
        loudness = null; bass = null; dynamics = null; session = 0
        virtualizer = null; equalizer = null; smartAvailable = false
        boostAvailable = false; bassAvailable = false; nightAvailable = false
        warning = false; pendingWarning = null
    }
}
