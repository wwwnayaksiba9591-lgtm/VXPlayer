package com.vxplayer.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.vxplayer.settings.VXDarkColors
import com.vxplayer.settings.VXLightColors
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ThemeContrastTest {
    private fun luminance(color: Color): Double {
        val rgb = color.toArgb()
        fun channel(shift: Int): Double {
            val value = ((rgb shr shift) and 255) / 255.0
            return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }
    @Test fun textRolesMeetNormalTextContrastInBothPalettes() {
        listOf(VXLightColors, VXDarkColors).forEach { scheme ->
            listOf(scheme.onSurface to scheme.surface, scheme.onSurfaceVariant to scheme.surfaceVariant,
                scheme.onPrimary to scheme.primary, scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSurface to scheme.surfaceContainer, scheme.onErrorContainer to scheme.errorContainer).forEach { (text, background) ->
                val a = luminance(text); val b = luminance(background)
                assertTrue("Text contrast must be at least 4.5:1", (maxOf(a, b) + .05) / (minOf(a, b) + .05) >= 4.5)
            }
        }
    }
}
