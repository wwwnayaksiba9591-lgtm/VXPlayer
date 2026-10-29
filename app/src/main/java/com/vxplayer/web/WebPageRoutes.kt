package com.vxplayer.web

import java.net.URI

/** Only inspected watch-page routes are auto-routed. Signed extensionless media stays native. */
object WebPageRoutes {
    private val pageHosts = setOf("morencius.com", "www.morencius.com", "m4uplay.quest", "www.m4uplay.quest")
    private val pagePath = Regex("^/(?:file|embed)/[A-Za-z0-9_-]+/?$")
    fun knownPage(url: String): Boolean = WebShield.host(url) in pageHosts &&
        runCatching { pagePath.matches(URI(url).path.orEmpty()) }.getOrDefault(false)
    fun useWeb(url: String, type: String): Boolean = type == "Web" || (type == "Auto" && knownPage(url))

    /** Preserve the supplied URL, cookies, query tokens and origin; never guess a cross-host embed. */
    fun isEmbed(url: String): Boolean = knownPage(url) && runCatching { URI(url).path.startsWith("/embed/") }.getOrDefault(false)
    val playerLayoutScript = """
        (() => {
          if (document.getElementById('vx-player-layout')) return;
          const s = document.createElement('style'); s.id = 'vx-player-layout';
          s.textContent = 'html,body { margin:0 !important; min-height:100%; background:#000 !important; } #vplayer { width:100% !important; height:100vh !important; }';
          (document.head || document.documentElement).appendChild(s);
        })();
    """.trimIndent()
}
