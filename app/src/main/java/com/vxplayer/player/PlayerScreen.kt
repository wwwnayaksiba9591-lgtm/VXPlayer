package com.vxplayer.player

import android.net.Uri
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.vxplayer.R
import com.vxplayer.MainActivity
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(uri: Uri, title: String, onBack: () -> Unit, folder: String = "", model: PlayerViewModel = viewModel()) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalActivity.current
    val host = activity as? MainActivity
    val inPip = host?.pipMode == true
    val orientation = rememberOrientationController()
    val context = LocalContext.current
    val audio = remember(context) { context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val window = activity?.window
    var brightness by rememberSaveable(uri.toString()) { mutableFloatStateOf(-1f) }
    var swipeStart by remember { mutableFloatStateOf(0f) }
    var adjustingBrightness by remember { mutableStateOf(false) }
    var swiping by remember { mutableStateOf(false) }
    var indicator by remember { mutableStateOf<String?>(null) }
    var indicatorVersion by remember { mutableIntStateOf(0) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    DisposableEffect(host, model) {
        host?.canEnterPip = { model.player?.let { it.playWhenReady && it.playbackState != androidx.media3.common.Player.STATE_ENDED } == true && !model.failed && !model.choosingSubtitle }
        host?.releaseHiddenPlayer = { model.stop() }
        onDispose {
            host?.canEnterPip = { false }
            host?.releaseHiddenPlayer = {}
            host?.updatePip()
        }
    }
    LaunchedEffect(host, model.isPlaying, model.choosingSubtitle) { host?.updatePip() }

    // A per-window override needs no system-settings permission and must not leak into the library.
    DisposableEffect(window) {
        val original = window?.attributes?.screenBrightness
        onDispose {
            if (original != null) window.attributes = window.attributes.apply { screenBrightness = original }
        }
    }
    LaunchedEffect(window, brightness) {
        if (brightness >= 0f && window != null) {
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
    }
    LaunchedEffect(indicatorVersion, swiping) {
        if (!swiping) {
            delay(1000)
            indicator = null
        }
    }

    DisposableEffect(lifecycle, uri) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> model.start(uri, title, folder)
                Lifecycle.Event.ON_PAUSE -> model.savePlaybackState()
                Lifecycle.Event.ON_STOP -> if (host?.pipMode != true) model.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) model.start(uri, title, folder)
        onDispose {
            lifecycle.removeObserver(observer)
            model.stop()
        }
    }
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previousBehavior != null) controller?.systemBarsBehavior = previousBehavior
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                GesturePlayerView(context).apply {
                    useController = true
                    controllerAutoShow = false
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setEnableComposeSurfaceSyncWorkaround(true)
                }
            },
            update = {
                it.player = model.player
                it.useController = !inPip
                it.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                    window?.let { currentWindow ->
                        val controller = WindowCompat.getInsetsController(currentWindow, currentWindow.decorView)
                        if (visibility == android.view.View.VISIBLE && !inPip) controller.show(WindowInsetsCompat.Type.systemBars())
                        else controller.hide(WindowInsetsCompat.Type.systemBars())
                    }
                })
                it.keepScreenOn = model.isPlaying
                it.onSeek = { forward ->
                    if (model.seekBy(if (forward) 10_000L else -10_000L)) {
                        indicator = context.getString(if (forward) R.string.seek_forward else R.string.seek_backward)
                        indicatorVersion++
                    }
                }
                it.onSwipeStart = { left ->
                    adjustingBrightness = left
                    swiping = true
                    swipeStart = if (left) {
                        brightness.takeIf { value -> value >= 0f }
                            ?: window?.attributes?.screenBrightness?.takeIf { value -> value >= 0f }
                            ?: (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
                    } else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                }
                it.onSwipe = { fraction ->
                    if (adjustingBrightness) {
                        brightness = (swipeStart + fraction).coerceIn(0.01f, 1f)
                        indicator = context.getString(R.string.gesture_brightness, (brightness * 100).roundToInt())
                    } else {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        if (audio.isVolumeFixed || max == 0) {
                            indicator = context.getString(R.string.gesture_volume_unavailable)
                        } else {
                            val volume = (swipeStart + fraction * max).roundToInt().coerceIn(0, max)
                            try {
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                                val actual = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                indicator = context.getString(R.string.gesture_volume, (actual * 100f / max).roundToInt())
                            } catch (_: SecurityException) {
                                indicator = context.getString(R.string.gesture_volume_unavailable)
                            }
                        }
                    }
                    indicatorVersion++
                }
                it.onSwipeEnd = { swiping = false }
            },
            onRelease = {
                it.setControllerVisibilityListener(null as PlayerView.ControllerVisibilityListener?)
                it.player = null
                it.keepScreenOn = false
            },
        )
        if (!inPip) indicator?.let {
            Text(it, color = Color.White,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp))
        }
        if (!inPip) Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f)).safeDrawingPadding().padding(horizontal = 12.dp),
        ) {
        Row(Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back_to_library), color = Color.White) }
            Text(model.activeTitle.ifBlank { title }, color = Color.White, modifier = Modifier.weight(1f).padding(8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            TrackMenu(model)
            Box {
                val speedLabel = stringResource(R.string.playback_speed_value, model.speed.toString().removeSuffix(".0"))
                val speedDescription = stringResource(R.string.playback_speed_description, speedLabel)
                TextButton(onClick = { speedMenuOpen = true }, modifier = Modifier.semantics { contentDescription = speedDescription }) {
                    Text(speedLabel, color = Color.White)
                }
                DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                    PlayerViewModel.SPEEDS.forEach { speed ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playback_speed_value, speed.toString().removeSuffix(".0"))) },
                            modifier = Modifier.semantics { selected = model.speed == speed },
                            onClick = {
                                model.changeSpeed(speed)
                                speedMenuOpen = false
                            },
                        )
                    }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { model.moveInPlaylist(-1) }, enabled = model.player != null && adjacentVideo(model.playlist, model.activeUri, -1) != null) {
                Text(stringResource(R.string.previous_video))
            }
            TextButton(onClick = { model.moveInPlaylist(1) }, enabled = model.player != null && adjacentVideo(model.playlist, model.activeUri, 1) != null) {
                Text(stringResource(R.string.next_video))
            }
            OrientationControls(orientation)
            if (android.os.Build.VERSION.SDK_INT >= 26 && context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                TextButton(onClick = { host?.enterPlayerPip(manual = true) }, enabled = model.player != null) {
                    Text(stringResource(R.string.picture_in_picture), color = Color.White)
                }
            }
        }
        }
        if (model.failed) {
            Text(stringResource(R.string.playback_failed), color = Color.White,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.8f)).padding(24.dp))
        }
    }
}
