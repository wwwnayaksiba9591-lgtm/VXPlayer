package com.vxplayer.library

import java.net.URI

object StreamUrls {
    fun normalize(input: String): String {
        val text=input.trim().take(8192)
        val match=Regex("https?://[^\\s<>\"']+",RegexOption.IGNORE_CASE).find(text)?.value
            ?: Regex("(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(?::[0-9]+)?(?:/[^\\s<>\"']*)?").find(text)?.value ?: text
        require(!text.contains(Regex("(?:ftp|file|javascript|intent)://",RegexOption.IGNORE_CASE))) {"Only HTTP and HTTPS links are supported."}
        return validate((if(match.contains("://"))match else "https://$match").trimEnd(')',']','}',',',';','"','\''))
    }
    fun directMedia(url: String)=runCatching {URI(url).path.orEmpty().substringAfterLast('.').lowercase() in setOf("mp4","mkv","webm","m3u8","mpd","mov","avi","ts","3gp")}.getOrDefault(false)
    fun validate(input: String): String {
        val value = input.trim()
        require(value.length in 1..4096 && value.none { it.isISOControl() }) { "Enter a valid HTTP or HTTPS URL." }
        val uri = try { URI(value) } catch (_: Exception) { throw IllegalArgumentException("Enter a valid HTTP or HTTPS URL.") }
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) { "Only HTTP and HTTPS links are supported." }
        require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null && (uri.port == -1 || uri.port in 1..65535)) { "Use a valid server address without embedded passwords." }
        return value
    }
    fun recent(previous: List<String>, opened: String) = (listOf(opened) + previous.filter { it != opened }).take(20)
    fun title(url: String): String {
        val uri = URI(url)
        return uri.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: uri.host
    }
}
