package com.vxplayer.web

/** Use the installed engine's version instead of advertising an unrelated hard-coded Chrome. */
fun browserUserAgent(installed: String, desktop: Boolean): String {
    val mobile=installed.ifBlank {com.vxplayer.library.StreamPageProbe.MOBILE_UA}
        .replace("; wv","").replace("Version/4.0 ","")
    if(!desktop)return mobile
    val version=Regex("Chrome/[0-9.]+").find(mobile)?.value ?: "Chrome/131.0.0.0"
    return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $version Safari/537.36"
}
