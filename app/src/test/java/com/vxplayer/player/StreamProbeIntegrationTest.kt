package com.vxplayer.player

import com.vxplayer.library.StreamPageProbe
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class StreamProbeIntegrationTest {
    @Test fun followsRedirectsUsesGetAndChromeAgentAndRecognizesMedia()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","/video"))
            server.enqueue(MockResponse().setHeader("Content-Type","video/mp4").setBody("media fixture"))
            server.start()
            val result=StreamPageProbe.inspect(server.url("/redirect").toString())
            assertTrue(result.media);assertTrue(result.url.endsWith("/video"))
            val request=server.takeRequest(2,TimeUnit.SECONDS)!!
            assertEquals("GET",request.method);assertTrue(request.getHeader("User-Agent")!!.startsWith("Mozilla/5.0"))
        }
    }
    @Test fun htmlAndForbiddenResponsesFallBackToWebInsteadOfThrowing()=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setHeader("Content-Type","text/html").setBody("<!doctype html><html><video></video></html>"))
            server.enqueue(MockResponse().setResponseCode(403))
            server.start()
            assertFalse(StreamPageProbe.inspect(server.url("/page").toString()).media)
            assertFalse(StreamPageProbe.inspect(server.url("/forbidden").toString()).media)
        }
    }
}
