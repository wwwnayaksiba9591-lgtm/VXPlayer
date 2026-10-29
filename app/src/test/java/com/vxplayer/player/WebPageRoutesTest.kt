package com.vxplayer.player

import com.vxplayer.web.WebPageRoutes
import com.vxplayer.web.WebShield
import com.vxplayer.network.NetworkLink
import com.vxplayer.network.recordNetworkPlay
import org.junit.Assert.*
import org.junit.Test

class WebPageRoutesTest {
    private val pages = listOf("https://morencius.com/file/bbw8t89l8cls",
        "https://morencius.com/embed/bbw8t89l8cls", "https://m4uplay.quest/file/tm187qcf23hh")
    @Test fun suppliedPagesUseWebInAutoButRespectForcedNativeTypes() {
        pages.forEach {
            assertTrue(WebPageRoutes.knownPage(it)); assertTrue(WebPageRoutes.useWeb(it, "Auto"))
            assertFalse(WebPageRoutes.useWeb(it, "HLS")); assertFalse(WebPageRoutes.useWeb(it, "DASH"))
            assertFalse(WebShield.blocked(it))
        }
        assertTrue(WebPageRoutes.isEmbed(pages[1])); assertFalse(WebPageRoutes.isEmbed(pages[0]))
    }
    @Test fun doesNotMisrouteMediaOrLookalikeDomains() {
        listOf("https://example.com/file/video", "https://morencius.com.evil.test/file/bbw8t89l8cls",
            "https://morencius.com/video.mp4", "https://morencius.com/master.m3u8",
            "https://cdn.example.com/stream?token=abc", "file:///file/bbw8t89l8cls").forEach {
            assertFalse(WebPageRoutes.useWeb(it, "Auto"))
        }
        assertTrue(WebPageRoutes.useWeb("https://example.com/watch", "Web"))
        assertTrue(WebPageRoutes.knownPage(pages[0] + "?token=abc"))
    }
    @Test fun keepsPlayerAssetsAndMediaWhileBlockingObservedAds() {
        listOf("/js/jquery.min.js", "/player/jw8/jwplayer.js?v=6", "/js/localstorage-slim.js", "/dl?op=get_slides").forEach {
            assertFalse(WebShield.blocked("https://morencius.com$it"))
        }
        assertFalse(WebShield.blocked("https://cdn.example.com/hls/stream/master.m3u8?token=abc"))
        assertTrue(WebShield.blocked("https://morencius.com/ad?type=123"))
        assertTrue(WebShield.blocked("https://m4uplay.quest/assets/jquery/ev-static.js?type=mainstream"))
        assertTrue(WebShield.blocked("https://mc.yandex.ru/metrika/tag.js"))
        assertTrue(WebShield.blocked("https://imasdk.googleapis.com/js/sdkloader/ima3.js"))
    }
    @Test fun webRecentsRetainFullUrlAndFavouritesWithoutDuplication() {
        val url = pages[0] + "?token=keep-this&quality=high"
        val original = NetworkLink(url, name = "My stream", favourite = true)
        val updated = recordNetworkPlay(listOf(original), NetworkLink(url, streamType = "Web"), 10)
        assertEquals(1, updated.size); assertEquals(url, updated.single().url)
        assertEquals("Web", updated.single().streamType); assertTrue(updated.single().favourite)
        assertEquals("My stream", updated.single().name)
    }
}
