package com.vxplayer.player

import com.vxplayer.web.WebShield
import org.junit.Assert.*
import org.junit.Test

class WebShieldTest {
    @Test fun legitimateSubdomainsAndWwwRedirectsStayOnTheSameSite() {
        assertTrue(WebShield.sameSite("https://www.example.com/watch","https://example.com/watch"))
        assertTrue(WebShield.allowNavigation("https://watch.example.co.uk","https://cdn.example.co.uk/video",false,true))
        assertFalse(WebShield.sameSite("https://example.co.uk","https://different.co.uk"))
        assertFalse(WebShield.sameSite("https://example.com","https://example.com.evil.com"))
        assertFalse(WebShield.sameSite("https://127.0.0.1","https://127.0.0.2"))
    }
    @Test fun httpRedirectCanReachConfirmationButDoesNotBypassBlockedHosts() {
        assertTrue(WebShield.allowNavigation("https://example.com","https://other.com/watch",false,true,redirect=true))
        assertFalse(WebShield.allowNavigation("https://example.com","https://popads.net/",false,true,redirect=true))
        assertFalse(WebShield.allowNavigation("https://example.com","intent://bad",false,true,redirect=true))
    }
    @Test fun blocksHostsAndRealSubdomainsOnly() {
        assertTrue(WebShield.blocked("https://DOUBLECLICK.net./ad.js"))
        assertTrue(WebShield.blocked("https://cdn.popads.net/script.js"))
        assertFalse(WebShield.blocked("https://notdoubleclick.net/video.mp4"))
        assertFalse(WebShield.blocked("https://example.com/watch?next=doubleclick.net"))
        assertFalse(WebShield.blocked("https://example.com/videos/holiday.mp4"))
    }
    @Test fun blocksAdPathsWithoutBlockingMediaSubstrings() {
        assertTrue(WebShield.blocked("https://example.com/ads/banner.js"))
        assertTrue(WebShield.blocked("https://example.com/vast.xml"))
        assertFalse(WebShield.blocked("https://example.com/downloads/video.mp4"))
        assertFalse(WebShield.blocked("https://example.com/episode/shadows"))
    }
    @Test fun rejectsSchemesPasswordsAndMalformedUrls() {
        listOf("javascript:alert(1)", "intent://ad", "file:///data/a", "content://private/a", "https://user:pass@example.com", "bad").forEach {
            assertNull(WebShield.host(it)); assertTrue(WebShield.blocked(it))
            assertFalse(WebShield.allowNavigation("https://example.com", it, true, false))
        }
    }
    @Test fun navigationRequiresGestureAcrossHostsAndBlocksAdsEvenOnClick() {
        val current = "https://example.com/embed/1"
        assertTrue(WebShield.allowNavigation(current, "https://example.com/watch/1", false, true))
        assertFalse(WebShield.allowNavigation(current, "https://other.com/watch", false, true))
        assertTrue(WebShield.allowNavigation(current, "https://other.com/watch", true, true))
        assertFalse(WebShield.allowNavigation(current, "https://popads.net", true, true))
        assertTrue(WebShield.allowNavigation(current, "https://other.com/watch", false, false))
    }
}
