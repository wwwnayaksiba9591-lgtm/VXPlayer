package com.vxplayer.player

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.*
import com.vxplayer.settings.AppSettings

class VideoEnhancer(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("vxplayer", 0)
    private val gpuSupported = (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo.reqGlEsVersion >= 0x20000
    private val remember = AppSettings.get(app).state.value.rememberEnhance
    private var state by mutableStateOf(SmartEnhanceState(prefs.getBoolean("enhanceEnabled", false),
        smartMode(prefs.getString("smartLastMode", prefs.getString("enhancePreset", "Auto"))).takeUnless { it == "Original" } ?: "Auto").restored(remember))
    var values by mutableStateOf(if (remember && state.lastMode == "Custom") EnhanceValues(prefs.getFloat("enhanceBrightness", 0f), prefs.getFloat("enhanceContrast", 0f), prefs.getFloat("enhanceSaturation", 1f), prefs.getFloat("enhanceSharpness", 0f), prefs.getFloat("enhanceWarmth", 0f)).bounded() else ENHANCE_PRESETS[state.lastMode] ?: ENHANCE_PRESETS.getValue("Auto"))
        private set
    val enabled get() = state.enabled
    val lastMode get() = state.lastMode
    val preset get() = if (enabled) lastMode else "Original"
    var comparing by mutableStateOf(false)
        private set
    var reason by mutableStateOf<String?>(if (gpuSupported) null else "Video enhancement is unavailable on this GPU.")
        private set
    var suspended by mutableStateOf(false)
        private set
    val available get() = reason == null
    val parameters = LiveEnhanceParameters()
    val effect = LiveVideoEffect(parameters)
    private var lastUri: String? = null
    init { publish() }
    fun newVideo(uri: String) {
        if (lastUri != uri) {
            reason = if (gpuSupported) null else "Video enhancement is unavailable on this GPU."
            if (!AppSettings.get(app).state.value.rememberEnhance) { state = SmartEnhanceState(); values = ENHANCE_PRESETS.getValue("Auto") }
        }
        comparing = false; lastUri = uri; publish()
    }
    fun change(value: EnhanceValues) { values = value.bounded(); state = state.select("Custom"); publish(); persist() }
    fun changeEnabled(value: Boolean) { state = state.copy(enabled = value); publish(); persist() }
    fun selectPreset(name: String) {
        val selected = ENHANCE_PRESETS[name] ?: return
        if (name != "Original") values = selected
        state = state.select(name); publish(); persist()
    }
    fun compare(original: Boolean) { comparing = original; publish() }
    fun autoAdjust(frame: FrameSummary?) {
        if (enabled && lastMode == "Auto" && available && !suspended && !comparing) { values = smoothAutoEnhance(values, frame); publish() }
    }
    fun updateSuspended(value: Boolean) { suspended = value; publish() }
    fun unavailable(message: String) { reason = message; publish() }
    private fun publish() { parameters.values = if (enabled && available && !suspended && !comparing) values else EnhanceValues() }
    private fun persist() {
        if (!AppSettings.get(app).state.value.rememberEnhance) return
        prefs.edit().putBoolean("enhanceEnabled", enabled).putString("enhancePreset", preset).putString("smartLastMode", lastMode)
            .putFloat("enhanceBrightness", values.brightness).putFloat("enhanceContrast", values.contrast).putFloat("enhanceSaturation", values.saturation)
            .putFloat("enhanceSharpness", values.sharpness).putFloat("enhanceWarmth", values.warmth).apply()
    }
}
