package com.vxplayer.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vxplayer.settings.AppSettings
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.vxplayer.MainActivity
import com.vxplayer.library.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Keep menu/picker composition alive while hiding its placement and touch targets.
private fun Modifier.overlayVisible(visible: Boolean) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (visible) placeable.place(0, 0) }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(uri: Uri, title: String, onBack: () -> Unit, folder: String = "", model: PlayerViewModel = viewModel()) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalActivity.current
    val host = activity as? MainActivity
    val inPip = host?.pipMode == true
    val context = LocalContext.current
    val defaults by AppSettings.get(context).state.collectAsStateWithLifecycle()
    val window = activity?.window
    val orientation = rememberOrientationController()
    val audio = remember(context) { context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var locked by rememberSaveable { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var unlockVisible by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var aspect by rememberSaveable { mutableIntStateOf(defaults.aspect) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var brightness by rememberSaveable { mutableFloatStateOf(-1f) }
    var indicator by remember { mutableStateOf<String?>(null) }
    var indicatorTick by remember { mutableIntStateOf(0) }
    var swiping by remember { mutableStateOf(false) }
    var swipeStart by remember { mutableFloatStateOf(0f) }
    var leftSwipe by remember { mutableStateOf(false) }
    var seekStart by remember { mutableLongStateOf(0L) }
    var seekTarget by remember { mutableStateOf<Long?>(null) }
    var heldSpeed by remember { mutableStateOf<Float?>(null) }
    var pulseZone by remember { mutableIntStateOf(0) }
    var pulseTick by remember { mutableIntStateOf(0) }
    val pulse = remember { Animatable(0f) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var slider by remember { mutableStateOf<Float?>(null) }
    var remaining by rememberSaveable { mutableStateOf(false) }
    fun feedback(text: String) { indicator = text; indicatorTick++ }
    fun touch() { interaction++ }
    LaunchedEffect(model.notice) { model.notice?.let { feedback(it) } }
    BackHandler(locked && !inPip) { unlockVisible = true; touch() }
    LaunchedEffect(model.player) {
        while (true) {
            position = model.player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            duration = model.player?.duration?.coerceAtLeast(0L) ?: 0L
            delay(250)
        }
    }
    LaunchedEffect(controls, interaction, model.isPlaying, menu, model.choosingSubtitle, swiping, slider) {
        if (controls && model.isPlaying && !menu && !model.choosingSubtitle && !swiping && slider == null) { delay(3000); controls = false }
    }
    LaunchedEffect(indicatorTick, swiping, heldSpeed) { if (!swiping && heldSpeed == null) { delay(1000); indicator = null } }
    LaunchedEffect(pulseTick) {
        if (pulseZone != 0) { pulse.snapTo(0f); pulse.animateTo(1f, tween(650)); pulseZone = 0 }
    }
    LaunchedEffect(unlockVisible, interaction) { if (unlockVisible) { delay(3000); unlockVisible = false } }
    LaunchedEffect(model.activeUri) { zoom = 1f; controls = true; touch() }
    LaunchedEffect(model.videoSize, orientation.locked, inPip, defaults.orientation) {
        val size = model.videoSize
        if (!orientation.locked && !inPip) orientation.requested = when (defaults.orientation) {
            "System" -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            "Portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "Landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> if (size.width > 0 && size.height > 0) {
                if (size.width * size.pixelWidthHeightRatio >= size.height) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(host, model) {
        host?.canEnterPip = { model.player?.let { it.playWhenReady && it.playbackState != androidx.media3.common.Player.STATE_ENDED } == true && !model.failed && !model.choosingSubtitle && !model.backgroundEnabled }
        host?.releaseHiddenPlayer = { model.onHidden() }
        onDispose { host?.canEnterPip = { false }; host?.releaseHiddenPlayer = {}; host?.updatePip() }
    }
    LaunchedEffect(host, model.isPlaying, model.choosingSubtitle, model.backgroundEnabled) { host?.updatePip() }
    DisposableEffect(lifecycle, uri) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { model.foreground = true; model.start(uri, title, folder) }
                Lifecycle.Event.ON_PAUSE -> model.savePlaybackState()
                Lifecycle.Event.ON_STOP -> if (host?.pipMode != true) model.onHidden()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) model.start(uri, title, folder)
        onDispose { lifecycle.removeObserver(observer); model.close() }
    }
    DisposableEffect(window) {
        val originalBrightness = window?.attributes?.screenBrightness
        val cutout = if (Build.VERSION.SDK_INT >= 28) window?.attributes?.layoutInDisplayCutoutMode else null
        val contrast = if (Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced else null
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val behavior = controller?.systemBarsBehavior
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        if (Build.VERSION.SDK_INT >= 28 && window != null) window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        if (Build.VERSION.SDK_INT >= 29 && window != null) window.isNavigationBarContrastEnforced = false
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (window != null) {
                window.attributes = window.attributes.apply {
                    if (originalBrightness != null) screenBrightness = originalBrightness
                    if (Build.VERSION.SDK_INT >= 28 && cutout != null) layoutInDisplayCutoutMode = cutout
                }
                if (Build.VERSION.SDK_INT >= 29 && contrast != null) window.isNavigationBarContrastEnforced = contrast
            }
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (behavior != null) controller?.systemBarsBehavior = behavior
        }
    }
    LaunchedEffect(window, brightness) { if (window != null && brightness >= 0) window.attributes = window.attributes.apply { screenBrightness = brightness } }
    val visible = controls && !locked && !inPip
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx -> GesturePlayerView(ctx).apply {
            useController = false
            setEnableComposeSurfaceSyncWorkaround(true)
            layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
        } }, update = { view ->
            view.player = model.player
            view.keepScreenOn = model.isPlaying
            view.subtitleView?.apply {
                setApplyEmbeddedStyles(false); setApplyEmbeddedFontSizes(false)
                setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, model.subtitleSize)
                setStyle(androidx.media3.ui.CaptionStyleCompat(model.subtitleColor, model.subtitleBackground, android.graphics.Color.TRANSPARENT,
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null))
            }
            view.locked = locked || inPip
            view.resizeMode = when (aspect) { 1, 3 -> AspectRatioFrameLayout.RESIZE_MODE_FILL; 2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT }
            val frame = view.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
            val size = model.videoSize
            val width = if (aspect == 3 && size.width > 0 && !inPip) size.width else -1
            val height = if (aspect == 3 && size.height > 0 && !inPip) size.height else -1
            if (frame.layoutParams.width != width || frame.layoutParams.height != height) frame.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.CENTER)
            frame.scaleX = if (inPip) 1f else zoom; frame.scaleY = if (inPip) 1f else zoom
            view.onTap = { if (locked) { unlockVisible = true; touch() } else { controls = !controls; touch() } }
            view.onDoubleTapZone = { zone ->
                if (zone == 0) { model.player?.let { if (it.isPlaying) it.pause() else it.play() }; feedback("Play / pause") }
                else if (model.seekBy(zone * 10000L)) { feedback(if (zone < 0) "−10 s" else "+10 s"); pulseZone = zone; pulseTick++ }
                touch()
            }
            view.onVerticalStart = { left ->
                swiping = true; leftSwipe = left
                swipeStart = if (left) brightness.takeIf { it >= 0 } ?: (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
                    else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            }
            view.onVertical = { fraction ->
                if (leftSwipe) { brightness = (swipeStart + fraction).coerceIn(.01f, 1f); feedback("Brightness ${(brightness * 100).roundToInt()}%") }
                else {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    try {
                        if (max > 0 && !audio.isVolumeFixed) {
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (swipeStart + fraction * max).roundToInt().coerceIn(0, max), 0)
                            feedback("Volume ${(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt()}%")
                        } else feedback("Volume controlled by device")
                    } catch (_: SecurityException) { feedback("Volume controlled by device") }
                }
            }
            view.onHorizontalStart = { swiping = true; seekStart = model.player?.currentPosition ?: 0L }
            view.onHorizontal = { fraction ->
                val target = seekPreview(seekStart, fraction, duration); seekTarget = target
                val delta = (target - seekStart) / 1000
                feedback("${formatDuration(target)} (${if (delta >= 0) "+" else ""}$delta s)")
            }
            view.onGestureEnd = { cancelled -> if (!cancelled) seekTarget?.let { model.seekTo(it) }; seekTarget = null; swiping = false; touch() }
            view.onZoom = { factor -> zoom = (zoom * factor).coerceIn(1f, 4f); feedback("Zoom ${(zoom * 100).roundToInt()}%") }
            view.onHold = { held ->
                if (held && heldSpeed == null) { heldSpeed = model.speed; model.changeSpeed(2f); feedback("2× while held") }
                else if (!held) { heldSpeed?.let { model.changeSpeed(it) }; heldSpeed = null; indicator = null }
            }
        }, onRelease = { it.player = null; it.keepScreenOn = false })
        if (!inPip && pulseZone != 0) Box(Modifier.align(if (pulseZone < 0) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(24.dp).size((72 + 48 * pulse.value).dp).background(Color.White.copy(alpha = .22f * (1f - pulse.value)), CircleShape))
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().overlayVisible(visible).background(Color.Black.copy(alpha = .65f)).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back", color = Color.White) }
                Text(model.activeTitle.ifBlank { title }, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TrackMenu(model)
                if (Build.VERSION.SDK_INT >= 26) TextButton(onClick = { host?.enterPlayerPip(true) }, enabled = !model.backgroundEnabled) { Text("PiP", color = Color.White) }
                Box {
                    TextButton(onClick = { menu = true; touch() }) { Text("⋮", color = Color.White) }
                    DropdownMenu(menu, onDismissRequest = { menu = false; touch() }) {
                        PlayerViewModel.SPEEDS.forEach { rate -> DropdownMenuItem(text = { Text("${rate}x${if (rate == model.speed) " ✓" else ""}") }, onClick = { model.changeSpeed(rate); menu = false; touch() }) }
                        Row { OrientationControls(orientation) }
                        PlayerExtraMenu(model)
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().overlayVisible(visible).background(Color.Black.copy(alpha = .65f)).safeDrawingPadding().padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(slider?.toLong() ?: position), color = Color.White)
                Slider(value = slider ?: position.toFloat().coerceAtMost(duration.toFloat()), onValueChange = { slider = it; touch() },
                    onValueChangeFinished = { slider?.let { model.seekTo(it.toLong()) }; slider = null; touch() },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(), enabled = duration > 0, modifier = Modifier.weight(1f))
                TextButton(onClick = { remaining = !remaining; touch() }) { Text(if (remaining) "−${formatDuration(duration - position)}" else formatDuration(duration), color = Color.White) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { locked = true; controls = false; unlockVisible = true; touch() }) { Text("Lock", color = Color.White) }
                TextButton(onClick = { model.moveInPlaylist(-1); touch() }, enabled = adjacentVideo(model.playlist, model.activeUri, -1) != null) { Text("Previous") }
                FilledTonalButton(onClick = { model.player?.let { if (it.isPlaying) it.pause() else it.play() }; touch() }, modifier = Modifier.height(56.dp)) { Text(if (model.isPlaying) "Ⅱ" else "▶") }
                TextButton(onClick = { model.moveInPlaylist(1); touch() }, enabled = adjacentVideo(model.playlist, model.activeUri, 1) != null) { Text("Next") }
                TextButton(onClick = { aspect = (aspect + 1) % 4; zoom = 1f; feedback(listOf("Fit", "Fill", "Zoom", "100%")[aspect]); touch() }) { Text(listOf("Fit", "Fill", "Zoom", "100%")[aspect], color = Color.White) }
                TextButton(onClick = { orientation.locked = true; orientation.requested = if (context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; touch() }) { Text("Rotate", color = Color.White) }
            }
        }
        if (locked && unlockVisible && !inPip) TextButton(onClick = { locked = false; controls = true; unlockVisible = false; touch() }, modifier = Modifier.align(Alignment.CenterEnd).safeDrawingPadding().background(Color.Black.copy(alpha = .8f))) { Text("Unlock", color = Color.White) }
        if (!inPip) indicator?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .8f), RoundedCornerShape(12.dp)).padding(16.dp)) }
        if (model.failed) Text("Could not play this video", color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color.Black).padding(24.dp))
    }
}
