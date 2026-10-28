package com.vxplayer.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

@Composable
fun VXTheme(settings: PlaybackDefaults, content: @Composable () -> Unit) {
    val dark = settings.theme != "System" || isSystemInDarkTheme()
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window -> WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
        } }
    }
    val colors = if (dark) darkColorScheme(primary = Color(0xFF79BAFF), secondary = Color(0xFF8AB4E0),
        background = Color(0xFF101720), surface = Color(0xFF17222F), surfaceVariant = Color(0xFF233243),
        onBackground = Color(0xFFE9EFF7), onSurface = Color(0xFFE9EFF7), onSurfaceVariant = Color(0xFFB5C3D5))
        else lightColorScheme(primary = Color(0xFF1765AA), surface = Color(0xFFF4F7FB), background = Color(0xFFFAFCFF))
    MaterialTheme(colorScheme = colors, content = content)
}
