package com.vxplayer.settings

import android.content.Context
import com.vxplayer.player.SeekSensitivity
import com.vxplayer.player.PlaybackMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackDefaults(val speed: Float = 1f, val autoNext: Boolean = true, val aspect: Int = 0,
    val orientation: String = "Auto", val theme: String = "System", val dynamicColor: Boolean = false,
    val allowBoost: Boolean = true, val maxBoost: Int = 200, val rememberBoost: Boolean = false, val rememberEnhance: Boolean = true,
    val seekSensitivity: SeekSensitivity = SeekSensitivity.Medium, val playbackMode: PlaybackMode = PlaybackMode.Off,
    val accent: String = "Orange")

class AppSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vxplayer", 0)
    private fun read() = SettingsParser.parse(runCatching { prefs.all }.getOrDefault(emptyMap<String,Any>()))
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    fun reload() { mutable.value = read() }
    fun update(change: (PlaybackDefaults) -> PlaybackDefaults) {
        val value = change(mutable.value)
        prefs.edit().putFloat("defaultSpeed", value.speed).putBoolean("autoNext", value.autoNext).putInt("defaultAspect", value.aspect)
            .putString("defaultOrientation", value.orientation).putString("theme", value.theme).putBoolean("dynamicColor", value.dynamicColor)
            .putBoolean("allowBoost", value.allowBoost).putInt("maxBoost", value.maxBoost).putBoolean("rememberBoost", value.rememberBoost)
            .putBoolean("rememberEnhance", value.rememberEnhance).putString("seekSensitivity", value.seekSensitivity.name)
            .putString("playbackMode", value.playbackMode.name).putString("accent", value.accent).apply()
        mutable.value = value
    }
    companion object {
        @Volatile private var instance: AppSettings? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: AppSettings(context).also { instance = it } }
    }
}
