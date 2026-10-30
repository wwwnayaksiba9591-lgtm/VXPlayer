package com.vxplayer.player
import com.vxplayer.network.*
import com.vxplayer.library.StreamUrls
import org.junit.Assert.*
import org.junit.Test
class StreamErrorsTest {
    @Test fun errorsAreReadable() {
        assertTrue(streamErrorMessage(403).contains("expired")); assertTrue(streamErrorMessage(404).contains("not found"))
        assertTrue(streamErrorMessage(500).contains("500")); assertTrue(streamErrorMessage(html=true).contains("web page"))
        assertTrue(streamErrorMessage(unsupported=true).contains("not supported")); assertTrue(streamErrorMessage().contains("connection"))
    }
    @Test fun undoPreservesOrderAndCapAndRejectsUnsafeUrls() {
        val links = (1..20).map { NetworkLink("https://example.com/$it.mp4",lastPlayedAt = it.toLong()) }
        val restored = restoreNetworkLink(links.dropLast(1),links.last())
        assertEquals(20,recentNetworkLinks(restored).size); assertEquals(links.last(),recentNetworkLinks(restored).first())
        assertEquals(20,restoreNetworkLink(links,NetworkLink("https://example.com/old",lastPlayedAt=1)).size)
        listOf("javascript:alert(1)","file:///private/a","http://user:pass@example.com").forEach { assertTrue(runCatching { StreamUrls.validate(it) }.isFailure) }
    }
}
