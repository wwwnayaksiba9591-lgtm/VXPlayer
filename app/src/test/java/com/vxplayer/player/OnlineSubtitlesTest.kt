package com.vxplayer.player

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class OnlineSubtitlesTest {
    @Test fun titleDoesNotExposePathsOrUrls() {
        assertEquals("Movie 2024",subtitleSearchTitle("Movie.2024.mkv"))
        assertEquals("",subtitleSearchTitle("https://example.com/private?token=secret"))
        assertEquals("",subtitleSearchTitle("/storage/emulated/0/private.mkv"))
        assertEquals("",subtitleSearchTitle("C:\\video.mkv"))
    }
    @Test fun searchesEnglishDeduplicatesAndDownloadsWithoutLeakingKey() {
        MockWebServer().use {server ->
            server.start()
            val origin=server.url("/").newBuilder().host("localhost").build()
            val data="""{"data":[{"attributes":{"language":"en","feature_details":{"title":"Movie"},"release":"Release","files":[{"file_id":42,"file_name":"movie.srt"},{"file_id":42}]}},{"attributes":{"language":"fr","files":[{"file_id":43}]}}]}"""
            server.enqueue(MockResponse().setBody(data))
            server.enqueue(MockResponse().setBody("""{"link":"${origin.resolve("english.srt")}"}"""))
            server.enqueue(MockResponse().setBody("1\n00:00:01,000 --> 00:00:02,000\nHello\n"))
            val provider=OnlineSubtitles(origin.resolve("api/v1/")!!)
            val results=provider.search("test-key","The Movie")
            assertEquals(1,results.size);assertEquals(42L,results.single().id)
            assertTrue(provider.download("test-key",42).toString(Charsets.UTF_8).contains("Hello"))
            val search=server.takeRequest()
            assertEquals("en",search.requestUrl!!.queryParameter("languages"))
            assertEquals("the movie",search.requestUrl!!.queryParameter("query"))
            assertEquals("test-key",search.getHeader("Api-Key"))
            assertEquals("POST",server.takeRequest().method)
            assertNull(server.takeRequest().getHeader("Api-Key"))
        }
    }
    @Test fun failuresAndMalformedSubtitlesAreRejected() {
        MockWebServer().use {server ->
            server.start()
            val origin=server.url("/").newBuilder().host("localhost").build()
            val provider=OnlineSubtitles(origin.resolve("api/v1/")!!)
            assertThrows(IllegalArgumentException::class.java){provider.search("","Movie")}
            assertThrows(IllegalArgumentException::class.java){provider.search("key","https://example.com/movie")}
            server.enqueue(MockResponse().setResponseCode(429))
            assertThrows(IllegalStateException::class.java){provider.search("key","Movie")}
            server.enqueue(MockResponse().setBody("""{"link":"${origin.resolve("bad.srt")}"}"""))
            server.enqueue(MockResponse().setBody("<html>Not a subtitle</html>"))
            assertThrows(IllegalArgumentException::class.java){provider.download("key",42)}
        }
    }
}
