package com.vxplayer.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackDefaults(val speed: Float = 1f, val autoNext: Boolean = true, val aspect: Int = 0,
    val orientation: String = "Auto", val theme: String = "Dark")

class AppSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vxplayer", 0)
    private val mutable = MutableStateFlow(PlaybackDefaults(prefs.getFloat("defaultSpeed", 1f), prefs.getBoolean("autoNext", true),
        prefs.getInt("defaultAspect", 0).coerceIn(0, 3), prefs.getString("defaultOrientation", "Auto") ?: "Auto", prefs.getString("theme", "Dark") ?: "Dark"))
    val state = mutable.asStateFlow()
    fun update(change: (PlaybackDefaults) -> PlaybackDefaults) {
        val value = change(mutable.value)
        prefs.edit().putFloat("defaultSpeed", value.speed).putBoolean("autoNext", value.autoNext).putInt("defaultAspect", value.aspect)
            .putString("defaultOrientation", value.orientation).putString("theme", value.theme).apply()
        mutable.value = value
    }
    companion object {
        @Volatile private var instance: AppSettings? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: AppSettings(context).also { instance = it } }
    }
}
