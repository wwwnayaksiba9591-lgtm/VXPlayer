package com.vxplayer.web

import com.vxplayer.library.StreamUrls
import java.net.URI
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Local filtering, not a full browser filter-list engine or a security boundary. */
object WebShield {
    @Volatile private var editableHosts: Set<String> = emptySet()
    fun load(context: android.content.Context) {
        editableHosts=runCatching {context.assets.open("web-blocklist.txt").bufferedReader().useLines {lines->lines.map {it.substringBefore('#').trim().lowercase(Locale.ROOT)}.filter {it.isNotBlank()}.toSet()}}.getOrDefault(emptySet())
    }
    private val hosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
        "google-analytics.com", "googletagmanager.com", "adservice.google.com", "analytics.google.com",
        "adnxs.com", "adsrvr.org", "amazon-adsystem.com", "adform.net", "adroll.com",
        "criteo.com", "criteo.net", "outbrain.com", "taboola.com", "mgid.com", "revcontent.com",
        "popads.net", "popcash.net", "propellerads.com", "adsterra.com", "adsterra.net",
        "exoclick.com", "exosrv.com", "juicyads.com", "trafficjunky.net", "trafficstars.com",
        "hilltopads.net", "clickadu.com", "clickaine.com", "monetag.com", "onclicka.com",
        "onclickalgo.com", "onclickperformance.com", "onclkds.com", "onclckvd.com", "tsyndicate.com",
        "ad-maven.com", "ad-maven.net", "a-ads.com", "bidvertiser.com", "servedbyadbutler.com",
        "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com", "smartadserver.com",
        "scorecardresearch.com", "quantserve.com", "hotjar.com", "hotjar.io", "clarity.ms",
        "connect.facebook.net", "ads.twitter.com", "ads.tiktok.com", "pushwelcome.com", "pushwoosh.com",
        "mc.yandex.ru", "mc.yandex.com", "imasdk.googleapis.com"
    )
    private val adPath = Regex("/(?:ads?|advertisements?|popunder|popads|vast|vpaid)(?:[/.?_-]|$)", RegexOption.IGNORE_CASE)
    fun host(url: String): String? = runCatching {
        URI(StreamUrls.validate(url)).host.lowercase(Locale.ROOT).trimEnd('.')
    }.getOrNull()
    fun sameHost(first: String, second: String): Boolean = host(first)?.let { it == host(second) } == true
    fun sameSite(first: String, second: String): Boolean {
        if(sameHost(first,second))return true
        if(host(first)==null || host(second)==null)return false
        val site=first.toHttpUrlOrNull()?.topPrivateDomain() ?: return false
        return site==second.toHttpUrlOrNull()?.topPrivateDomain()
    }
    fun blocked(url: String): Boolean {
        val host = host(url) ?: return true
        if ((hosts+editableHosts).any { host == it || host.endsWith(".$it") }) return true
        // Popup loader observed on these pages. Shield OFF remains available for compatibility checks.
        if (runCatching { URI(url).path }.getOrNull() == "/assets/jquery/ev-static.js" && WebPageRoutes.knownPage("https://$host/file/probe")) return true
        return adPath.containsMatchIn(runCatching { URI(url).rawPath.orEmpty() }.getOrDefault(""))
    }
    fun allowNavigation(current: String, target: String, gesture: Boolean, shield: Boolean, redirect: Boolean = false): Boolean =
        host(target) != null && (!shield || (!blocked(target) && (sameSite(current, target) || gesture || redirect)))
    // No native JS bridge; avoid generic iframe/video selectors that would hide the actual player.
    val cosmeticScript = """
        (() => {
          if (window.__vxShield) return; window.__vxShield = true;
          const style = document.createElement('style');
          style.textContent = '[data-ad-slot], [data-ad-client], ins.adsbygoogle, .ad-banner, .ad-container, .ad-overlay, #popunder, iframe[src*="doubleclick.net"], iframe[src*="googlesyndication.com"] { display:none !important; pointer-events:none !important; }';
          (document.head || document.documentElement).appendChild(style);
          window.open = () => null;
        })();
    """.trimIndent()
}
