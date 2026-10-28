package com.vxplayer.player

import com.vxplayer.library.StreamUrls
import org.junit.Assert.*
import org.junit.Test

class StreamUrlsTest {
    @Test fun acceptsStreamsAndKeepsSignedQueries() {
        val url = "https://example.com/video.m3u8?token=a%2Bb&x=1"
        assertEquals(url, StreamUrls.validate(" $url "))
        assertEquals("video.m3u8", StreamUrls.title(url))
        assertEquals("http://localhost:8080/v.mpd", StreamUrls.validate("http://localhost:8080/v.mpd"))
    }
    @Test fun rejectsBadSchemesHostsCredentialsAndControlCharacters() {
        listOf("file:///x", "content://videos/1", "https:///nohost", "https://a:b@example.com/v", "https://example.com:99999/v", "https://example.com/v\nX").forEach {
            assertThrows(IllegalArgumentException::class.java) { StreamUrls.validate(it) }
        }
    }
    @Test fun recentIsBoundedDeduplicatedAndMostRecentFirst() {
        val urls = (1..20).map { "https://example.com/$it" }
        val recent = StreamUrls.recent(urls, urls[10])
        assertEquals(20, recent.size)
        assertEquals(urls[10], recent.first())
        assertEquals(1, recent.count { it == urls[10] })
        assertEquals(20, StreamUrls.recent(urls, "https://example.com/new").size)
    }
}
