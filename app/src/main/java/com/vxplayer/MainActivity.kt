package com.vxplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.util.Rational
import android.provider.MediaStore
import android.provider.Settings
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.navigation.navArgument
import com.vxplayer.library.LibraryScreen
import com.vxplayer.library.Video
import com.vxplayer.library.VideoLibraryViewModel
import com.vxplayer.player.PlayerScreen
import com.vxplayer.settings.AppSettings
import com.vxplayer.settings.SettingsScreen
import com.vxplayer.settings.VXTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.vxplayer.ui.brandSplashExit

class MainActivity : ComponentActivity() {
    var pipMode by mutableStateOf(false)
        private set
    var canEnterPip: () -> Boolean = { false }
    var releaseHiddenPlayer: () -> Unit = {}

    fun updatePip() {
        if (Build.VERSION.SDK_INT >= 31 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            setPictureInPictureParams(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
                .setAutoEnterEnabled(canEnterPip()).build())
        }
    }

    fun enterPlayerPip(manual: Boolean = false): Boolean {
        if (Build.VERSION.SDK_INT < 26 || (!manual && !canEnterPip()) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        return try { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
        catch (_: IllegalStateException) { false }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31) enterPlayerPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // Visible PiP is STARTED. STOPPED means even the PiP surface is no longer visible.
        releaseHiddenPlayer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        brandSplashExit(splash, freshLaunch = savedInstanceState == null)
        enableEdgeToEdge()
        setContent {
            val settings by AppSettings.get(applicationContext).state.collectAsStateWithLifecycle()
            val navigation = rememberNavController()
            val destination by navigation.currentBackStackEntryAsState()
            VXTheme(settings, playerMode = destination?.destination?.route?.startsWith("player") == true) {
                NavHost(navigation, startDestination = "library", enterTransition = { fadeIn(tween(180)) },
                    exitTransition = { fadeOut(tween(140)) }, popEnterTransition = { fadeIn(tween(180)) }, popExitTransition = { fadeOut(tween(140)) }) {
                    composable("library") {
                        LibraryScreen(onSettings = { navigation.navigate("settings") }, onVideoClick = { video, folder ->
                            navigation.navigate("player?uri=${Uri.encode(video.uri.toString())}&title=${Uri.encode(video.title)}&folder=${Uri.encode(folder)}") {
                                launchSingleTop = true
                            }
                        })
                    }
                    composable("settings") { SettingsScreen(onBack = { navigation.popBackStack() }) }
                    composable(
                        "player?uri={uri}&title={title}&folder={folder}",
                        arguments = listOf(
                            navArgument("uri") { type = NavType.StringType },
                            navArgument("title") { type = NavType.StringType },
                            navArgument("folder") { type = NavType.StringType; defaultValue = "" },
                        ),
                    ) { entry ->
                        PlayerScreen(
                            uri = Uri.parse(requireNotNull(entry.arguments?.getString("uri"))),
                            title = entry.arguments?.getString("title").orEmpty(),
                            folder = entry.arguments?.getString("folder").orEmpty(),
                            onBack = { navigation.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}


