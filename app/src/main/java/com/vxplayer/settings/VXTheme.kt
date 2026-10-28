package com.vxplayer.settings

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

val VXLightColors = lightColorScheme(
    primary = Color(0xFF145DA0), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E8FF), onPrimaryContainer = Color(0xFF08315A),
    secondary = Color(0xFF4E6075), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E5F6), onSecondaryContainer = Color(0xFF172D43),
    tertiary = Color(0xFF3C6370), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC0E9F7), onTertiaryContainer = Color(0xFF123B47),
    background = Color(0xFFF8FAFD), onBackground = Color(0xFF17212D),
    surface = Color(0xFFF8FAFD), onSurface = Color(0xFF17212D),
    surfaceVariant = Color(0xFFE0E7EF), onSurfaceVariant = Color(0xFF414D5B),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F5FA),
    surfaceContainer = Color(0xFFEBF0F7), surfaceContainerHigh = Color(0xFFE4EBF3), surfaceContainerHighest = Color(0xFFDDE5EF),
    inverseSurface = Color(0xFF293340), inverseOnSurface = Color(0xFFF0F4FA), inversePrimary = Color(0xFFA6CFFF),
    outline = Color(0xFF6D7987), outlineVariant = Color(0xFFBFC9D5),
    error = Color(0xFFBA1A1A), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    surfaceTint = Color(0xFF145DA0), scrim = Color.Black,
)

val VXDarkColors = darkColorScheme(
    primary = Color(0xFFA6CFFF), onPrimary = Color(0xFF00325C),
    primaryContainer = Color(0xFF154978), onPrimaryContainer = Color(0xFFD5E8FF),
    secondary = Color(0xFFB9C9DE), onSecondary = Color(0xFF223448),
    secondaryContainer = Color(0xFF394B60), onSecondaryContainer = Color(0xFFD8E5F6),
    tertiary = Color(0xFFA5CDDA), onTertiary = Color(0xFF073540),
    tertiaryContainer = Color(0xFF254D59), onTertiaryContainer = Color(0xFFC0E9F7),
    background = Color(0xFF101720), onBackground = Color(0xFFE3EAF4),
    surface = Color(0xFF101720), onSurface = Color(0xFFE3EAF4),
    surfaceVariant = Color(0xFF293543), onSurfaceVariant = Color(0xFFC0CBD8),
    surfaceContainerLowest = Color(0xFF0B1119), surfaceContainerLow = Color(0xFF17212D),
    surfaceContainer = Color(0xFF1B2633), surfaceContainerHigh = Color(0xFF263240), surfaceContainerHighest = Color(0xFF313E4D),
    inverseSurface = Color(0xFFE3EAF4), inverseOnSurface = Color(0xFF293340), inversePrimary = Color(0xFF145DA0),
    outline = Color(0xFF8A97A8), outlineVariant = Color(0xFF404D5D),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFFA6CFFF), scrim = Color.Black,
)

@Composable
fun VXTheme(settings: PlaybackDefaults, playerMode: Boolean = false, content: @Composable () -> Unit) {
    val dark = playerMode || resolveDarkTheme(settings.theme, isSystemInDarkTheme())
    val activity = LocalActivity.current
    val context = LocalContext.current
    SideEffect {
        activity?.window?.let { window -> WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        } }
    }
    val colors = when {
        playerMode -> VXDarkColors
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> VXDarkColors
        else -> VXLightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
