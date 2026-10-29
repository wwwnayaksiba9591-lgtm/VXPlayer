package com.vxplayer.privatefiles

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vxplayer.settings.AppSettings
import com.vxplayer.settings.VXTheme

/** No authentication flag is persisted: process death always locks Private. */
object PrivateAccess {
    private val policy = PrivateLockPolicy()
    private val handler = Handler(Looper.getMainLooper())
    var unlocked by mutableStateOf(false)
        private set
    private val lockLater = Runnable { lock() }
    fun unlock() { handler.removeCallbacks(lockLater); policy.unlock(); unlocked = true }
    fun lock() { handler.removeCallbacks(lockLater); policy.lock(); unlocked = false }
    fun background() { policy.background(SystemClock.elapsedRealtime()); handler.postDelayed(lockLater, 30_000) }
    fun foreground() {
        handler.removeCallbacks(lockLater)
        policy.foreground(SystemClock.elapsedRealtime()); unlocked = policy.unlocked
    }
}

class PrivateActivity : FragmentActivity() {
    private lateinit var prompt: BiometricPrompt
    var authenticationMessage by mutableStateOf<String?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { authenticationMessage = null; PrivateAccess.unlock() }
            override fun onAuthenticationError(code: Int, message: CharSequence) { authenticationMessage = message.toString() }
        })
        setContent {
            val settings by AppSettings.get(this).state.collectAsStateWithLifecycle()
            VXTheme(settings) { PrivateScreen(this) }
        }
    }

    fun authenticate() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            authenticationMessage = "Set up a device PIN, pattern, password, or biometric in Android Settings to unlock Private."
            return
        }
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock VXPlayer Private")
            .setSubtitle("Use your biometric or device screen lock")
            .setAllowedAuthenticators(authenticators).build())
    }

    override fun onStart() { PrivateAccess.foreground(); super.onStart() }
    override fun onStop() {
        if (!isChangingConfigurations) PrivateAccess.background()
        super.onStop()
    }
}
