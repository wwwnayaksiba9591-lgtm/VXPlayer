package com.vxplayer.player

import com.vxplayer.library.StreamPageProbe
import com.vxplayer.library.userMediaSessionAllowed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamPageProbeTest {
    @Test fun recognizesHtmlMimeTypesWithParametersAndCase() {
        assertTrue(StreamPageProbe.isHtml("Text/HTML; charset=UTF-8"))
        assertTrue(StreamPageProbe.isHtml("application/xhtml+xml"))
    }
    @Test fun networkCapabilityRequiresAnExplicitUserMediaSession() {
        assertFalse(userMediaSessionAllowed("http://example.com/a.mp4", false))
        assertTrue(userMediaSessionAllowed("http://example.com/a.mp4", true))
        assertTrue(userMediaSessionAllowed("https://example.com/a.m3u8", true))
        assertFalse(userMediaSessionAllowed("file:///private/a.mp4", true))
        assertFalse(userMediaSessionAllowed(null, true))
        assertFalse(userMediaSessionAllowed("javascript:alert(1)", true))
        assertTrue(StreamPageProbe.isHtml(null, "<!-- comment -->\n<html>"))
    }

    @Test fun recognizesUnlabelledHtmlWithoutMistakingPlaylistsForPages() {
        assertTrue(StreamPageProbe.isHtml(null, "\uFEFF \n<!DOCTYPE HTML><html>"))
        assertTrue(StreamPageProbe.isHtml("application/octet-stream", "<HTML><HEAD>"))
        assertTrue(StreamPageProbe.isHtml("text/plain", "<html>"))
        assertFalse(StreamPageProbe.isHtml(null, "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000"))
        assertFalse(StreamPageProbe.isHtml("application/dash+xml", "<?xml version=\"1.0\"?><MPD>"))
        assertFalse(StreamPageProbe.isHtml("video/mp4", "<html>"))
        assertFalse(StreamPageProbe.isHtml(null, ""))
    }
}
