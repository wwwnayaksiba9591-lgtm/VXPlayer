package com.vxplayer.player

import com.vxplayer.web.browserUserAgent
import org.junit.Assert.*
import org.junit.Test

class BrowserUserAgentTest {
    @Test fun usesInstalledChromeVersionForMobileAndDesktop() {
        val installed="Mozilla/5.0 (Linux; Android 15; TestPhone; wv) AppleWebKit/537.36 Version/4.0 Chrome/140.0.7339.123 Mobile Safari/537.36"
        val mobile=browserUserAgent(installed,false)
        assertTrue(mobile.contains("Chrome/140.0.7339.123"))
        assertTrue(mobile.contains("Android 15"));assertFalse(mobile.contains("; wv"));assertFalse(mobile.contains("Version/4.0"))
        val desktop=browserUserAgent(installed,true)
        assertTrue(desktop.contains("Chrome/140.0.7339.123"));assertFalse(desktop.contains("Android"))
        assertTrue(browserUserAgent("",false).startsWith("Mozilla/5.0"))
    }
}
