package com.vxplayer.network

import com.vxplayer.library.StreamPageProbe

fun streamErrorMessage(status: Int? = null, html: Boolean = false, unsupported: Boolean = false): String = when {
    html -> StreamPageProbe.HTML_MESSAGE
    status == 403 -> "Access denied (403). This link may require permission or may have expired."
    status == 404 -> "Video not found (404). Check the link."
    status != null -> "The media server returned HTTP $status. Retry or check the link."
    unsupported -> "This media format is not supported on this device."
    else -> "Could not connect to the media server. Check your connection and retry."
}

fun restoreNetworkLink(current: List<NetworkLink>, saved: NetworkLink): List<NetworkLink> {
    val merged = listOf(saved) + current.filter { it.url != saved.url }
    val recent = recentNetworkLinks(merged).map { it.url }.toSet()
    return merged.mapNotNull { if (it.url in recent) it else if (it.favourite) it.copy(lastPlayedAt = 0) else null }
}
