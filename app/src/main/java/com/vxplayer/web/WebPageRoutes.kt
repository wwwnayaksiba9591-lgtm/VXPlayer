package com.vxplayer.web

import java.net.URI

/** Only inspected watch-page routes are auto-routed. Signed extensionless media stays native. */
object WebPageRoutes {
    private val pageHosts = setOf("morencius.com", "www.morencius.com", "m4uplay.quest", "www.m4uplay.quest")
    private val pagePath = Regex("^/(?:file|embed)/[A-Za-z0-9_-]+/?$")
    fun knownPage(url: String): Boolean = WebShield.host(url) in pageHosts &&
        runCatching { pagePath.matches(URI(url).path.orEmpty()) }.getOrDefault(false)
    fun useWeb(url: String, type: String): Boolean = type == "Web" || (type == "Auto" && knownPage(url))
    fun needsProbe(url: String, type: String): Boolean =
        type == "Auto" && !useWeb(url,type) && !com.vxplayer.library.StreamUrls.directMedia(url)

    /** Preserve the supplied URL, cookies, query tokens and origin; never guess a cross-host embed. */
    fun isEmbed(url: String): Boolean = knownPage(url) && runCatching { URI(url).path.startsWith("/embed/") }.getOrDefault(false)
    /** User-invoked playback of existing page media; no scraping, native bridge or DRM bypass. */
    val playPageVideoScript = """
        (() => {
          const videos = [];
          function collect(doc) {
            videos.push(...doc.querySelectorAll('video'));
            for (const frame of doc.querySelectorAll('iframe')) {
              try { if (frame.contentDocument) collect(frame.contentDocument); } catch (_) {}
            }
          }
          collect(document);
          const visible = videos.filter(v => v.getBoundingClientRect().width > 0 && v.getBoundingClientRect().height > 0);
          visible.sort((a,b) => b.clientWidth*b.clientHeight - a.clientWidth*a.clientHeight);
          const video = visible[0];
          if (!video) return 'missing';
          if (video.error) return 'error';
          video.play().catch(() => {});
          return 'requested';
        })();
    """.trimIndent()
    val playerLayoutScript = """
        (() => {
          if (document.getElementById('vx-player-layout')) return;
          const s = document.createElement('style'); s.id = 'vx-player-layout';
          s.textContent = 'html,body { margin:0 !important; min-height:100%; background:#000 !important; } #vplayer { width:100% !important; height:100vh !important; }';
          (document.head || document.documentElement).appendChild(s);
        })();
    """.trimIndent()
}
