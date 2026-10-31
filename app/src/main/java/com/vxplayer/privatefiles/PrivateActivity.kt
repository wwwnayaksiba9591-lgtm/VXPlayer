package com.vxplayer.privatefiles

import android.os.Bundle
import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
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
    var authenticating by mutableStateOf(false)
        private set
    private val credentialResult=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result ->
        authenticating=false
        if(result.resultCode==Activity.RESULT_OK){authenticationMessage=null;PrivateAccess.unlock()}
        else authenticationMessage="Device unlock was cancelled. Your private videos remain locked."
    }
    private lateinit var prompt: BiometricPrompt
    var authenticationMessage by mutableStateOf<String?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {authenticating=false;authenticationMessage = null; PrivateAccess.unlock() }
            override fun onAuthenticationError(code: Int, message: CharSequence) {authenticating=false;authenticationMessage = "$message. You can also use your device PIN, pattern or password." }
        })
        setContent {
            val settings by AppSettings.get(this).state.collectAsStateWithLifecycle()
            VXTheme(settings) { PrivateScreen(this) }
        }
    }

    fun authenticate() {
        if(authenticating)return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (runCatching {BiometricManager.from(this).canAuthenticate(authenticators)}.getOrDefault(-1) != BiometricManager.BIOMETRIC_SUCCESS) {
            authenticateDeviceCredential();return
        }
        authenticating=true
        runCatching {prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock VXPlayer Private")
            .setSubtitle("Use your biometric or device screen lock")
            .setAllowedAuthenticators(authenticators).build())}.onFailure {authenticating=false;authenticationMessage="Biometric unlock is unavailable. Use your device PIN instead."}
    }
    fun authenticateDeviceCredential() {
        if(authenticating)return
        val keyguard=getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
        if(keyguard?.isDeviceSecure!=true){authenticationMessage="Set a device PIN, pattern or password in Android security settings first.";return}
        @Suppress("DEPRECATION") val request=keyguard.createConfirmDeviceCredentialIntent("Unlock VXPlayer Private","Use your device PIN, pattern or password")
        if(request==null){authenticationMessage="Device credentials are unavailable. Check Android security settings.";return}
        authenticating=true
        runCatching {credentialResult.launch(request)}.onFailure {authenticating=false;authenticationMessage="Could not open device unlock. Try again after returning to the app."}
    }

    override fun onStart() {super.onStart();com.vxplayer.diagnostics.LocalCrashReports.route("private");PrivateAccess.foreground()}
    override fun onStop() {
        if (!isChangingConfigurations) PrivateAccess.background()
        super.onStop()
    }
}
