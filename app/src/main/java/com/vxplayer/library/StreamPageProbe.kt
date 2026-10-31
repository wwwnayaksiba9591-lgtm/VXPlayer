package com.vxplayer.library

import java.util.Locale
import kotlinx.coroutines.ensureActive

/** Pure response classifier. It never loads or executes a webpage. */
object StreamPageProbe {
    const val MOBILE_UA="Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    data class Result(val url: String,val media: Boolean)
    suspend fun inspect(input: String): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var target=StreamUrls.normalize(input)
        try {
            repeat(6) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val connection=java.net.URL(target).openConnection() as java.net.HttpURLConnection
                try {
                    connection.instanceFollowRedirects=false;connection.connectTimeout=8000;connection.readTimeout=8000
                    connection.setRequestProperty("User-Agent",MOBILE_UA);connection.setRequestProperty("Range","bytes=0-511")
                    val status=connection.responseCode
                    if(status in 300..399) {
                        val location=connection.getHeaderField("Location") ?: return@withContext Result(target,false)
                        target=StreamUrls.validate(java.net.URI(target).resolve(location).toString())
                    } else {
                        if(status !in 200..299)return@withContext Result(target,false)
                        val prefix=connection.inputStream.use {stream -> val bytes=ByteArray(512);val count=stream.read(bytes);if(count>0)String(bytes,0,count,Charsets.UTF_8) else ""}
                        val mime=connection.contentType.orEmpty().lowercase()
                        return@withContext Result(target,!isHtml(mime,prefix) && (mime.startsWith("video/") || mime.startsWith("audio/") || mime.contains("mpegurl") || mime.contains("dash+xml") || StreamUrls.directMedia(target)))
                    }
                } finally {connection.disconnect()}
            }
        } catch(cancelled: kotlinx.coroutines.CancellationException){throw cancelled}
        catch(_: Exception) {}
        Result(target,false)
    }
    const val HTML_MESSAGE = "This link is a web page, not a direct video link"
    fun isHtml(contentType: String?, prefix: String = ""): Boolean {
        val mime = contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        if (mime == "text/html" || mime == "application/xhtml+xml") return true
        if (mime.isNotEmpty() && mime !in listOf("application/octet-stream", "text/plain")) return false
        val text = prefix.trimStart('\uFEFF', ' ', '\r', '\n', '\t').lowercase(Locale.ROOT)
            .replace(Regex("^(<!--.*?-->\\s*)+", RegexOption.DOT_MATCHES_ALL), "")
        return text.startsWith("<!doctype html") || text.startsWith("<html") || text.startsWith("<head") || text.startsWith("<body")
    }
}

fun userMediaSessionAllowed(root: String?, registered: Boolean): Boolean = registered && root != null &&
    runCatching { StreamUrls.validate(root) }.isSuccess
