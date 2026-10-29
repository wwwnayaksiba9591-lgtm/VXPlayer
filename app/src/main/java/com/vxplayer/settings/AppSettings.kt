package com.vxplayer.settings

import android.content.Context
import com.vxplayer.player.SeekSensitivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackDefaults(val speed: Float = 1f, val autoNext: Boolean = true, val aspect: Int = 0,
    val orientation: String = "Auto", val theme: String = "System", val dynamicColor: Boolean = false,
    val allowBoost: Boolean = true, val maxBoost: Int = 200, val rememberBoost: Boolean = false, val rememberEnhance: Boolean = true,
    val seekSensitivity: SeekSensitivity = SeekSensitivity.Medium)

class AppSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vxplayer", 0)
    private fun read() = PlaybackDefaults(prefs.getFloat("defaultSpeed", 1f), prefs.getBoolean("autoNext", true),
        prefs.getInt("defaultAspect", 0).coerceIn(0, 3), prefs.getString("defaultOrientation", "Auto") ?: "Auto", prefs.getString("theme", "System") ?: "System", prefs.getBoolean("dynamicColor", false),
        prefs.getBoolean("allowBoost", true), if (prefs.getInt("maxBoost", 200) == 150) 150 else 200, prefs.getBoolean("rememberBoost", false), prefs.getBoolean("rememberEnhance", true),
        runCatching { SeekSensitivity.valueOf(prefs.getString("seekSensitivity", "Medium")!!) }.getOrDefault(SeekSensitivity.Medium))
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    fun reload() { mutable.value = read() }
    fun update(change: (PlaybackDefaults) -> PlaybackDefaults) {
        val value = change(mutable.value)
        prefs.edit().putFloat("defaultSpeed", value.speed).putBoolean("autoNext", value.autoNext).putInt("defaultAspect", value.aspect)
            .putString("defaultOrientation", value.orientation).putString("theme", value.theme).putBoolean("dynamicColor", value.dynamicColor)
            .putBoolean("allowBoost", value.allowBoost).putInt("maxBoost", value.maxBoost).putBoolean("rememberBoost", value.rememberBoost)
            .putBoolean("rememberEnhance", value.rememberEnhance).putString("seekSensitivity", value.seekSensitivity.name).apply()
        mutable.value = value
    }
    companion object {
        @Volatile private var instance: AppSettings? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: AppSettings(context).also { instance = it } }
    }
}
