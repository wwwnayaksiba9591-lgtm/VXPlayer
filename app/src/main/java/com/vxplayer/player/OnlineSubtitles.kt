package com.vxplayer.player

import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class OnlineSubtitle(val id: Long, val title: String, val release: String)

fun subtitleSearchTitle(title: String): String {
    if(title.contains("://") || title.contains('/') || title.contains('\\')) return ""
    return title.replace(Regex("(?i)\\.(mkv|mp4|webm|avi|mov|ts|m2ts|3gp)$"),"")
        .replace('_',' ').replace('.',' ').trim().take(200)
}

/** Explicit, user-initiated title search only. No video, path, URL or hash is uploaded. */
class OnlineSubtitles(private val base: HttpUrl = "https://api.opensubtitles.com/api/v1/".toHttpUrl()) {
    private val http = OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS)
        .readTimeout(15,TimeUnit.SECONDS).callTimeout(25,TimeUnit.SECONDS)
        .followRedirects(false).build()

    fun search(key: String, query: String): List<OnlineSubtitle> {
        require(query.trim().length in 2..200) { "Enter a movie or episode title (2–200 characters)." }
        require(!query.contains("://") && !query.contains('/') && !query.contains('\\')) {"Enter a title, not a URL or file path."}
        val url = base.newBuilder().addPathSegment("subtitles")
            .addQueryParameter("languages","en").addEncodedQueryParameter("query",java.net.URLEncoder.encode(query.trim().lowercase(java.util.Locale.ROOT),"UTF-8")).build()
        val root = JsonParser.parseString(api(key,Request.Builder().url(url))).asJsonObject
        return root.getAsJsonArray("data")?.toList().orEmpty().flatMap { item ->
            runCatching {
                val attributes=item.asJsonObject.getAsJsonObject("attributes")
                if(attributes.get("language")?.asString != "en") return@runCatching emptyList()
                val title=attributes.getAsJsonObject("feature_details")?.get("title")?.takeUnless {it.isJsonNull}?.asString.orEmpty()
                val release=attributes.get("release")?.takeUnless {it.isJsonNull}?.asString.orEmpty()
                attributes.getAsJsonArray("files")?.toList().orEmpty().mapNotNull { file ->
                    val value=file.asJsonObject
                    val id=value.get("file_id")?.asLong ?: return@mapNotNull null
                    if(id<=0) null else OnlineSubtitle(id,title.ifBlank {release.ifBlank {"English subtitle"}},
                        value.get("file_name")?.takeUnless {it.isJsonNull}?.asString ?: release)
                }
            }.getOrDefault(emptyList())
        }.distinctBy {it.id}.take(40)
    }

    fun download(key: String, id: Long): ByteArray {
        require(id>0)
        val request=Request.Builder().url(base.newBuilder().addPathSegment("download").build())
            .post("{\"file_id\":$id,\"sub_format\":\"srt\"}".toRequestBody("application/json".toMediaType()))
        val link=JsonParser.parseString(api(key,request)).asJsonObject.get("link")?.asString
            ?: error("The provider did not return a subtitle download.")
        val url=link.toHttpUrl()
        require(url.scheme=="https" || (base.host=="localhost" && url.host=="localhost")) {"An insecure download was refused."}
        // Do not forward the API key to the subtitle download server.
        http.newCall(Request.Builder().url(url).header("User-Agent","VXPlayer v1.1.0").build()).execute().use {response ->
            check(response.isSuccessful) {"Subtitle download failed (${response.code}). Try searching again."}
            val body=response.body ?: error("Empty subtitle download.")
            require(body.contentLength()<=MAX_BYTES) {"Subtitle exceeds 4 MiB."}
            val bytes=body.byteStream().use {it.readBytesLimited()}
            require(bytes.isNotEmpty()) {"Empty subtitle download."}
            // Validate before the caller installs or remembers this file.
            SrtTiming.shift(SubtitleText.decode(bytes),0)
            return bytes
        }
    }

    private fun api(key: String, builder: Request.Builder): String {
        require(key.isNotBlank() && !key.contains('\n') && !key.contains('\r')) {"Add your OpenSubtitles API key first."}
        http.newCall(builder.header("Api-Key",key.trim()).header("User-Agent","VXPlayer v1.1.0")
            .header("Accept","application/json").build()).execute().use {response ->
            check(response.isSuccessful) { when(response.code) {
                401,403 -> "OpenSubtitles refused access. Check your API key and provider account."
                406,429 -> "OpenSubtitles download or request limit reached. Try again later."
                else -> "OpenSubtitles request failed (${response.code}). Try again."
            } }
            return response.body?.byteStream()?.use {it.readBytesLimited().toString(Charsets.UTF_8)} ?: error("Empty provider response.")
        }
    }

    companion object { const val MAX_BYTES=4*1024*1024 }
}

private fun java.io.InputStream.readBytesLimited(): ByteArray {
    val output=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(8192)
    while(true) {
        val count=read(buffer);if(count<0)break
        require(output.size()+count<=OnlineSubtitles.MAX_BYTES) {"Subtitle response exceeds 4 MiB."}
        output.write(buffer,0,count)
    }
    return output.toByteArray()
}
