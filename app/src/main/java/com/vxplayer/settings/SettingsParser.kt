package com.vxplayer.settings

import com.vxplayer.player.PlaybackMode
import com.vxplayer.player.SeekSensitivity

object SettingsParser {
    fun text(raw: Any?, default: String) = (raw as? String) ?: default
    fun boolean(raw: Any?, default: Boolean) = when (raw) { is Boolean -> raw; "true" -> true; "false" -> false; else -> default }
    fun number(raw: Any?, default: Float): Float = ((raw as? Number)?.toFloat() ?: (raw as? String)?.toFloatOrNull())?.takeIf { it.isFinite() } ?: default
    fun integer(raw: Any?, default: Int): Int = number(raw, default.toFloat()).let { if (it % 1f == 0f && it >= Int.MIN_VALUE && it < Int.MAX_VALUE.toFloat()) it.toInt() else default }
    fun choice(raw: Any?, choices: List<String>, default: String) = choices.firstOrNull { it.equals(raw as? String, true) } ?: default
    inline fun <reified T : Enum<T>> enum(raw: Any?, default: T): T = enumValues<T>().firstOrNull { it.name.equals(raw as? String, true) } ?: default
    fun parse(values: Map<String, *>): PlaybackDefaults = PlaybackDefaults(
        speed = number(values["defaultSpeed"], 1f).takeIf { it in listOf(.5f,.75f,1f,1.25f,1.5f,2f) } ?: 1f,
        autoNext = boolean(values["autoNext"], true), aspect = integer(values["defaultAspect"], 0).takeIf { it in 0..3 } ?: 0,
        orientation = choice(values["defaultOrientation"], listOf("Auto","System","Portrait","Landscape","Reverse Portrait","Reverse Landscape"), "Auto"),
        theme = choice(values["theme"], listOf("System","Light","Dark"), "System"),
        dynamicColor = boolean(values["dynamicColor"], false), allowBoost = boolean(values["allowBoost"], true),
        maxBoost = integer(values["maxBoost"],200).takeIf { it in listOf(150,200) } ?: 200,
        rememberBoost = boolean(values["rememberBoost"],false), rememberEnhance = boolean(values["rememberEnhance"],true),
        seekSensitivity = enum(values["seekSensitivity"],SeekSensitivity.Medium), playbackMode = enum(values["playbackMode"],PlaybackMode.Off),
        accent = choice(values["accent"], listOf("Orange","Blue","Teal","Green","Purple"), "Orange"))
}
