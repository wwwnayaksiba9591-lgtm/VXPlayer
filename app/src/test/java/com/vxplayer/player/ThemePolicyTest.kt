package com.vxplayer.player

import com.vxplayer.settings.resolveDarkTheme
import org.junit.Assert.*
import org.junit.Test

class ThemePolicyTest {
    @Test fun explicitChoicesIgnoreSystemAppearance() {
        assertFalse(resolveDarkTheme("Light", true))
        assertFalse(resolveDarkTheme("Light", false))
        assertTrue(resolveDarkTheme("Dark", false))
        assertTrue(resolveDarkTheme("Dark", true))
    }
    @Test fun systemAndUnknownChoicesFollowSystemAppearance() {
        assertTrue(resolveDarkTheme("System", true))
        assertFalse(resolveDarkTheme("System", false))
        assertTrue(resolveDarkTheme("obsolete", true))
        assertFalse(resolveDarkTheme("obsolete", false))
    }
}
