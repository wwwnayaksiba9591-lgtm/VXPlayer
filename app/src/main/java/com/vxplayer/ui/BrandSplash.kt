package com.vxplayer.ui

import android.app.Activity
import android.content.res.Configuration
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.splashscreen.SplashScreen
import com.vxplayer.R
import com.vxplayer.settings.AppSettings
import com.vxplayer.settings.resolveDarkTheme

fun Activity.brandSplashExit(splash: SplashScreen, freshLaunch: Boolean) {
    // There is deliberately no keep-on-screen condition or startup delay.
    splash.setOnExitAnimationListener { provider ->
        if (!freshLaunch || isFinishing) { provider.remove(); return@setOnExitAnimationListener }
        try {
            val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val dark = resolveDarkTheme(AppSettings.get(this).state.value.theme, systemDark)
            provider.view.setBackgroundColor(if (dark) 0xFF101720.toInt() else 0xFFF8FAFD.toInt())
            val density = resources.displayMetrics.density
            val credit = TextView(this).apply {
                text = getString(R.string.made_in_india_splash)
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (dark) 0xFFE3EAF4.toInt() else 0xFF17212D.toInt())
                setPadding((24 * density).toInt(), 0, (24 * density).toInt(), 0)
            }
            (provider.view as? android.view.ViewGroup)?.addView(credit, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = (resources.displayMetrics.heightPixels * .18f).toInt()
            })
            provider.view.animate().alpha(0f).setDuration(1000).withEndAction { provider.remove() }.start()
        } catch (_: RuntimeException) { provider.remove() }
    }
}
