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
    var values by mutableStateOf(if (remember) EnhanceValues(prefs.getFloat("enhanceBrightness", 0f), prefs.getFloat("enhanceContrast", 0f), prefs.getFloat("enhanceSaturation", 1f), prefs.getFloat("enhanceSharpness", 0f), prefs.getFloat("enhanceWarmth", 0f)).bounded() else EnhanceValues())
        private set
    var enabled by mutableStateOf(remember && prefs.getBoolean("enhanceEnabled", false))
        private set
    var preset by mutableStateOf(if (remember) prefs.getString("enhancePreset", "Original").orEmpty() else "Original")
        private set
    var reason by mutableStateOf<String?>(if (gpuSupported) null else "Video enhancement is unavailable on this GPU.")
        private set
    var suspended by mutableStateOf(false)
        private set
    val available get() = reason == null
    val parameters = LiveEnhanceParameters()
    val effect = LiveVideoEffect(parameters)
    private var lastUri: String? = null
    init { parameters.values = values }
    fun newVideo(uri: String) {
        if (lastUri != uri) {
            reason = if (gpuSupported) null else "Video enhancement is unavailable on this GPU."
            if (!AppSettings.get(app).state.value.rememberEnhance) { values = EnhanceValues(); enabled = false; preset = "Original" }
        }
        lastUri = uri; publish()
    }
    fun change(value: EnhanceValues) { values = value.bounded(); preset = "Custom"; publish(); persist() }
    fun changeEnabled(value: Boolean) { enabled = value; publish(); persist() }
    fun selectPreset(name: String) {
        val selected = ENHANCE_PRESETS[name] ?: return
        values = selected; preset = name; enabled = name != "Original"; publish(); persist()
    }
    fun updateSuspended(value: Boolean) { suspended = value; publish() }
    fun unavailable(message: String) { reason = message; publish() }
    private fun publish() { parameters.values = if (enabled && available && !suspended) values else EnhanceValues() }
    private fun persist() {
        if (!AppSettings.get(app).state.value.rememberEnhance) return
        prefs.edit().putBoolean("enhanceEnabled", enabled).putString("enhancePreset", preset)
            .putFloat("enhanceBrightness", values.brightness).putFloat("enhanceContrast", values.contrast).putFloat("enhanceSaturation", values.saturation)
            .putFloat("enhanceSharpness", values.sharpness).putFloat("enhanceWarmth", values.warmth).apply()
    }
}
