package com.vxplayer.settings

enum class ThemeMode { System, Light, Dark }

fun resolveDarkTheme(mode: String, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.Light.name -> false
    ThemeMode.Dark.name -> true
    else -> systemDark
}
