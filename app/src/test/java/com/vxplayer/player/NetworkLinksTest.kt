package com.vxplayer.player

import com.vxplayer.network.*
import org.junit.Assert.*
import org.junit.Test

class NetworkLinksTest {
    @Test fun recordingIsDeduplicatedNewestFirstAndCapped() {
        var links = emptyList<NetworkLink>()
        repeat(25) { index -> links = recordNetworkPlay(links, NetworkLink("https://example.com/$index.mp4"), 100L) }
        assertEquals(20, recentNetworkLinks(links).size)
        assertEquals("https://example.com/24.mp4", recentNetworkLinks(links).first().url)
        links = recordNetworkPlay(links, NetworkLink(" https://example.com/10.mp4 "), 100L)
        assertEquals(20, links.size)
        assertEquals(1, links.count { it.url.endsWith("/10.mp4") })
        assertEquals("https://example.com/10.mp4", recentNetworkLinks(links).first().url)
    }
    @Test fun favouritesAndNamesSurviveRecentEvictionAndReplay() {
        var links = listOf(NetworkLink("https://example.com/f.mp4", "Saved name", true, 1))
        repeat(25) { links = recordNetworkPlay(links, NetworkLink("https://example.com/$it.mp4"), 100L) }
        val favourite = links.first { it.favourite }
        assertEquals(0L, favourite.lastPlayedAt)
        assertEquals("Saved name", favourite.title)
        links = recordNetworkPlay(links, NetworkLink(favourite.url), 200L)
        assertTrue(recentNetworkLinks(links).first().favourite)
        assertEquals("Saved name", recentNetworkLinks(links).first().title)
    }
    @Test fun legacyMigrationPreservesOrderTypesAndSignedQueries() {
        val links = legacyNetworkLinks("[\"https://example.com/live?token=a%2Bb\",\"bad\",\"https://example.com/second.mp4\",\"https://example.com/live?token=a%2Bb\"]") { "HLS" }
        assertEquals(2, links.size)
        assertEquals("https://example.com/live?token=a%2Bb", links.first().url)
        assertEquals("HLS", links.first().streamType)
        assertTrue(links.first().lastPlayedAt > links.last().lastPlayedAt)
        assertTrue(legacyNetworkLinks("not json").isEmpty())
        assertEquals("Auto", legacyNetworkLinks("[\"https://example.com/a\"]") { error("Bad legacy preference") }.single().streamType)
        assertTrue(legacyNetworkLinks("[\"file:///private/movie\",\"javascript:alert(1)\"]").isEmpty())
        assertEquals(20, legacyNetworkLinks((1..30).joinToString(prefix = "[", postfix = "]") { "\"https://example.com/$it\"" }).size)
    }
}
