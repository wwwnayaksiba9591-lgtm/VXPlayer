package com.vxplayer.library

import java.util.Locale

/** Pure response classifier. It never loads or executes a webpage. */
object StreamPageProbe {
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
