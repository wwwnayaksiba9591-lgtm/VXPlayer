package com.vxplayer.player

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vxplayer.library.StreamUrls

/** Website playback stays in WebView so the host can run its own video setup. */
class WebVideoActivity : ComponentActivity() {
    private lateinit var browser: WebView
    private lateinit var videoLayer: FrameLayout
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = runCatching { StreamUrls.validate(intent.dataString.orEmpty()) }.getOrNull()
        if (url == null) { finish(); return }
        val host = android.net.Uri.parse(url).host
        val root = FrameLayout(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val status = TextView(this).apply { setTextColor(Color.WHITE); text = "Loading video webpage…"; setPadding(16, 8, 16, 8) }
        val toolbar = LinearLayout(this)
        toolbar.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
        toolbar.addView(Button(this).apply { text = "Reload"; setOnClickListener { browser.reload() } })
        column.addView(toolbar)
        column.addView(status)
        browser = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val target = request.url
                    val permitted = target.scheme in listOf("http", "https") &&
                        (!request.isForMainFrame || target.host.equals(host, true))
                    if (!permitted && request.isForMainFrame) status.text = "Navigation outside this video host was blocked."
                    return !permitted
                }
                override fun onPageFinished(view: WebView, url: String) {
                    status.text = "Tap the website’s play button. Use its fullscreen button to expand the video."
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) status.text = "Could not load this page. Check your connection and try Reload."
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) status.text = "The video host returned an error (${response.statusCode}). Try Reload."
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullscreenCallback != null) { callback.onCustomViewHidden(); return }
                    fullscreenCallback = callback
                    videoLayer.addView(view, FrameLayout.LayoutParams(-1, -1))
                    videoLayer.visibility = View.VISIBLE
                    WindowInsetsControllerCompat(window, root).hide(WindowInsetsCompat.Type.systemBars())
                }
                override fun onHideCustomView() { leaveFullscreen() }
            }
        }
        column.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(column, FrameLayout.LayoutParams(-1, -1))
        videoLayer = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); visibility = View.GONE }
        root.addView(videoLayer, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        root.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreenCallback != null) leaveFullscreen()
                else if (browser.canGoBack()) browser.goBack()
                else finish()
            }
        })
        if (savedInstanceState == null || browser.restoreState(savedInstanceState) == null) browser.loadUrl(url)
    }

    private fun leaveFullscreen() {
        val callback = fullscreenCallback ?: return
        fullscreenCallback = null
        videoLayer.removeAllViews()
        videoLayer.visibility = View.GONE
        WindowInsetsControllerCompat(window, videoLayer).show(WindowInsetsCompat.Type.systemBars())
        callback.onCustomViewHidden()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        if (::browser.isInitialized) {
            browser.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(v){v.pause()})", null)
            browser.onPause()
        }
        super.onPause()
    }
    override fun onResume() { super.onResume(); if (::browser.isInitialized) browser.onResume() }
    override fun onDestroy() {
        if (::browser.isInitialized) {
            leaveFullscreen()
            (browser.parent as? android.view.ViewGroup)?.removeView(browser)
            browser.stopLoading()
            browser.destroy()
        }
        super.onDestroy()
    }
}
