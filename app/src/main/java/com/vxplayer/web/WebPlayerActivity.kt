package com.vxplayer.web

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vxplayer.library.StreamUrls
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/** Explicit web session, separate from native playback and its private/media access. */
class WebPlayerActivity : ComponentActivity() {
    private var web: WebView? = null
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var shieldButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var error: LinearLayout
    private lateinit var errorText: TextView
    private var custom: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    @Volatile private var shield = true
    @Volatile private var page = ""
    private val count = AtomicInteger()
    private var navigationDialog: AlertDialog? = null
    private var lastGesture=0L
    private var triedHttp=false
    private var desktop=false
    private var mobileAgent=""
    private var sslHandler: SslErrorHandler?=null
    private var sslDialog: AlertDialog?=null
    private lateinit var nativeButton: Button
    @Volatile private var detectedMedia: String?=null
    private val preferences by lazy {getSharedPreferences("vxplayer",0)}
    private fun externalBrowser() {runCatching {startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(page)).addCategory(Intent.CATEGORY_BROWSABLE),"Open in browser").putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,arrayOf(android.content.ComponentName(this,com.vxplayer.MainActivity::class.java))))}.onFailure {Toast.makeText(this,"No browser is available.",Toast.LENGTH_SHORT).show()}}

    companion object {
        fun open(context: Context, url: String) {
            context.startActivity(Intent(context, WebPlayerActivity::class.java).putExtra("url", StreamUrls.normalize(url)))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = runCatching { StreamUrls.normalize(intent.getStringExtra("url").orEmpty()) }.getOrElse { finish(); return }
        shield = preferences.all["webShield"] as? Boolean ?: true
        mobileAgent=browserUserAgent(runCatching {WebSettings.getDefaultUserAgent(this)}.getOrDefault(com.vxplayer.library.StreamPageProbe.MOBILE_UA),false)
        Thread {WebShield.load(applicationContext)}.start()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        root = FrameLayout(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(24, 24, 24)) }
        root.addView(content, FrameLayout.LayoutParams(-1, -1)); setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        toolbar.addView(button("Back") { back() })
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; maxLines = 2; setPadding(8, 8, 8, 8) }
        toolbar.addView(status, LinearLayout.LayoutParams(0, -2, 1f)); content.addView(toolbar)
        val actions = LinearLayout(this)
        shieldButton = button("") { shield = !shield;preferences.edit().putBoolean("webShield",shield).apply();updateStatus(); reload() }
        actions.addView(shieldButton, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button("Reload") { reload() }, LinearLayout.LayoutParams(0, -2, 1f)); content.addView(actions)
        val tools=LinearLayout(this)
        tools.addView(button("Browser"){externalBrowser()},LinearLayout.LayoutParams(0,-2,1f))
        val desktopButton=button("Desktop OFF") {}
        desktopButton.setOnClickListener {desktop=!desktop;desktopButton.text=if(desktop)"Desktop ON" else "Desktop OFF";web?.settings?.userAgentString=browserUserAgent(mobileAgent,desktop);reload()}
        tools.addView(desktopButton,LinearLayout.LayoutParams(0,-2,1f));content.addView(tools)
        tools.addView(button("Page play") {
            web?.evaluateJavascript(WebPageRoutes.playPageVideoScript) {result ->
                val message=when(result) {
                    "\"missing\"" -> "Tap Play inside the embedded player first. This frame may be controlled by another website."
                    "\"error\"" -> "The website video has a playback error. Retry, switch Shield off for this site, or open in browser."
                    else -> "Playback requested. If it does not start, tap the site's Play button or use Browser."
                }
                Toast.makeText(this,message,Toast.LENGTH_LONG).show()
            }
        },LinearLayout.LayoutParams(0,-2,1f))
        nativeButton=button("Play in VXPlayer") {
            detectedMedia?.let { media ->
                startActivity(Intent(this,com.vxplayer.MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse(media))
                    .putExtra("nativeMedia",true).putExtra("mediaReferer",page).putExtra("mediaUserAgent",web?.settings?.userAgentString))
                web?.evaluateJavascript("document.querySelectorAll('video,audio').forEach(v=>v.pause());",null)
            }
        }.apply {visibility=View.GONE}
        content.addView(nativeButton)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        content.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        error = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        errorText = TextView(this).apply { setTextColor(Color.WHITE) }
        error.addView(errorText); error.addView(button("Retry") { reload() });error.addView(button("Open in browser"){externalBrowser()}); content.addView(error)
        try {
            val browser = WebView(this); web = browser
            browser.setBackgroundColor(Color.BLACK)
            browser.settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                useWideViewPort = true; loadWithOverviewMode = true
                allowFileAccess = false; allowContentAccess = false
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
                setSupportMultipleWindows(true); javaScriptCanOpenWindowsAutomatically = false
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString=mobileAgent
                if (Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
            }
            CookieManager.getInstance().apply {setAcceptCookie(true);setAcceptThirdPartyCookies(browser, true)}
            browser.setOnTouchListener {_,event->if(event.actionMasked==android.view.MotionEvent.ACTION_UP)lastGesture=android.os.SystemClock.elapsedRealtime();false}
            browser.setDownloadListener { _, _, _, _, _ -> Toast.makeText(this, "Downloads are blocked in the web player.", Toast.LENGTH_SHORT).show() }
            browser.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    if (WebShield.host(url) == null || (shield && WebShield.blocked(url))) {
                        recordBlock()
                        return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                    }
                    if(StreamUrls.directMedia(url) && detectedMedia!=url) {
                        detectedMedia=url
                        runOnUiThread {if(!isDestroyed)nativeButton.visibility=View.VISIBLE}
                    }
                    return null
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val target = request.url.toString()
                    if(WebShield.host(target)==null) {
                        if(!request.isForMainFrame){recordBlock();return true}
                        if(request.hasGesture() || android.os.SystemClock.elapsedRealtime()-lastGesture<1500) {
                            AlertDialog.Builder(this@WebPlayerActivity).setTitle("Open another app?").setMessage("This link asks to leave VXPlayer.")
                                .setPositiveButton("Open"){_,_->runCatching {val intent=if(target.startsWith("intent:"))Intent.parseUri(target,Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW,request.url);intent.component=null;intent.selector=null;intent.addCategory(Intent.CATEGORY_BROWSABLE);startActivity(intent)}.onFailure {Toast.makeText(this@WebPlayerActivity,"No app can open this link.",Toast.LENGTH_SHORT).show()}}
                                .setNegativeButton("Cancel",null).show()
                        } else recordBlock()
                        return true
                    }
                    if (!request.isForMainFrame) return WebShield.host(target) == null || (shield && WebShield.blocked(target))
                    val gesture=request.hasGesture() || android.os.SystemClock.elapsedRealtime()-lastGesture<1500
                    if (!WebShield.allowNavigation(page, target, gesture, shield, request.isRedirect)) { recordBlock(); return true }
                    if (!WebShield.sameSite(page, target)) {
                        navigationDialog?.dismiss()
                        navigationDialog = AlertDialog.Builder(this@WebPlayerActivity).setTitle(if(request.isRedirect)"Follow website redirect?" else "Open another website?")
                            .setMessage(WebShield.host(target)).setPositiveButton("Open") { _, _ -> page = target; view.loadUrl(target) }
                            .setNegativeButton("Stay here", null).show()
                        return true
                    }
                    return false
                }
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {page=url;detectedMedia=null;nativeButton.visibility=View.GONE;error.visibility=View.GONE;updateStatus()}
                override fun onPageFinished(view: WebView, url: String) {
                    if (shield) view.evaluateJavascript(WebShield.cosmeticScript, null)
                    if (WebPageRoutes.isEmbed(url)) view.evaluateJavascript(WebPageRoutes.playerLayoutScript, null)
                    updateStatus()
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                    if (request.isForMainFrame) {
                        if(!triedHttp && page.startsWith("https://") && failure.errorCode in listOf(ERROR_CONNECT,ERROR_TIMEOUT)) {
                            triedHttp=true;page="http://"+page.removePrefix("https://");view.loadUrl(page)
                        } else showError("Page error ${failure.errorCode}: ${failure.description}. Check your connection or open in a browser.")
                    }
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) showError("Website returned HTTP ${response.statusCode}.")
                }
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, failure: SslError) {
                    sslHandler?.cancel();sslDialog?.dismiss();sslHandler=handler
                    showError("The website's secure connection could not be verified.")
                    sslDialog=AlertDialog.Builder(this@WebPlayerActivity).setTitle("Unverified secure connection")
                        .setMessage("The certificate could not be verified. Proceed only if you trust this website.")
                        .setPositiveButton("Proceed anyway"){_,_->sslHandler=null;handler.proceed();error.visibility=View.GONE}
                        .setNegativeButton("Cancel"){_,_->handler.cancel();sslHandler=null}.setOnCancelListener {handler.cancel();sslHandler=null}.show()
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    hideFullscreen(); (view.parent as? ViewGroup)?.removeView(view); view.destroy(); web = null
                    showError("The web player stopped. Go back and reopen the link."); return true
                }
            }
            browser.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, value: Int) { progress.progress = value; progress.visibility = if (value == 100) View.GONE else View.VISIBLE }
                override fun onCreateWindow(view: WebView, dialog: Boolean, gesture: Boolean, result: Message): Boolean { recordBlock(); return false }
                override fun onPermissionRequest(request: PermissionRequest) { request.deny();if(request.resources.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))runOnUiThread {detectedMedia=null;nativeButton.visibility=View.GONE;Toast.makeText(this@WebPlayerActivity,"DRM-protected media cannot be extracted for native playback.",Toast.LENGTH_LONG).show()} }
                override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }
                override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<android.net.Uri>>, params: FileChooserParams): Boolean { callback.onReceiveValue(null); return true }
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (custom != null) { callback.onCustomViewHidden(); return }
                    custom = view; customCallback = callback; content.visibility = View.GONE
                    root.addView(view, FrameLayout.LayoutParams(-1, -1))
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    WindowCompat.getInsetsController(window, root).apply {
                        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
                override fun onHideCustomView() { hideFullscreen() }
            }
            content.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
            val restored = savedInstanceState?.getBundle("web")?.let { browser.restoreState(it) }
            if (restored == null) browser.loadUrl(page) else page = browser.url ?: page
        } catch (_: Exception) { showError("Android System WebView is unavailable. Update it and reopen this link.") }
        updateStatus()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { back() } })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun button(label: String, click: () -> Unit) = Button(this).apply { text = label; minHeight = dp(48); setOnClickListener { click() } }
    private fun updateStatus() { status.text = WebShield.host(page).orEmpty(); shieldButton.text = if (shield) "Shield ON · ${count.get()}" else "Shield OFF" }
    private fun recordBlock() { val total = count.incrementAndGet(); if (total <= 10 || total % 10 == 0) runOnUiThread { if (!isDestroyed) updateStatus() } }
    private fun showError(message: String) { errorText.text = message; error.visibility = View.VISIBLE; progress.visibility = View.GONE }
    private fun reload() { count.set(0); error.visibility = View.GONE; updateStatus(); if(web==null) {finish();open(this,page)} else web?.loadUrl(page) }
    private fun back() { if (custom != null) hideFullscreen() else if (web?.canGoBack() == true) web?.goBack() else finish() }
    private fun hideFullscreen() {
        custom?.let { root.removeView(it) }; custom = null
        val callback = customCallback; customCallback = null; callback?.onCustomViewHidden()
        if (::content.isInitialized) content.visibility = View.VISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (::root.isInitialized) WindowCompat.getInsetsController(window, root).show(WindowInsetsCompat.Type.systemBars())
    }
    override fun onResume() { super.onResume(); web?.onResume() }
    override fun onPause() {
        web?.evaluateJavascript("document.querySelectorAll('video,audio').forEach(v => v.pause());", null)
        web?.onPause(); super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("shield", shield); outState.putBundle("web", Bundle().also { web?.saveState(it) }); super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        sslHandler?.cancel();sslHandler=null;sslDialog?.dismiss()
        navigationDialog?.dismiss(); hideFullscreen()
        web?.let { it.stopLoading(); (it.parent as? ViewGroup)?.removeView(it); it.destroy() }; web = null
        super.onDestroy()
    }
}
