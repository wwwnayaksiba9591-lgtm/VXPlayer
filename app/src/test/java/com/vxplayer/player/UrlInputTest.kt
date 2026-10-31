package com.vxplayer.player
import com.vxplayer.library.StreamUrls
import org.junit.Assert.*
import org.junit.Test
class UrlInputTest {
    @Test fun normalizesDomainsAndSharedText() {
        assertEquals("https://example.com",StreamUrls.normalize(" example.com "))
        assertEquals("http://example.com/video.mp4",StreamUrls.normalize("Watch this: http://example.com/video.mp4)"))
        assertTrue(StreamUrls.directMedia("https://example.com/master.m3u8?token=1"))
        assertFalse(StreamUrls.directMedia("https://example.com/watch"))
    }
    @Test fun rejectsLocalAndScriptSchemes() {listOf("file:///tmp/a","javascript://evil","ftp://example.com").forEach {assertTrue(runCatching {StreamUrls.normalize(it)}.isFailure)}}
}
