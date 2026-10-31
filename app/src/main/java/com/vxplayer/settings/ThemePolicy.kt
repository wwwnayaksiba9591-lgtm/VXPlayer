package com.vxplayer.settings

enum class ThemeMode { System, Light, Dark, AMOLED }

fun resolveDarkTheme(mode: String, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.Light.name -> false
    ThemeMode.Dark.name, ThemeMode.AMOLED.name -> true
    else -> systemDark
}
