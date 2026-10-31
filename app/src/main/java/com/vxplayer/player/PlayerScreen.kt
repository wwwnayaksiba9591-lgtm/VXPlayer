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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.core.animateFloatAsState
import com.vxplayer.ui.VXIconButton
import com.vxplayer.ui.*
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

// Keep menu/picker composition alive while hiding its placement and touch targets.
@Composable
private fun Modifier.overlayVisible(visible: Boolean, immediate: Boolean = false): Modifier {
    val opacity by animateFloatAsState(if (visible) 1f else 0f, tween(180), label = "player controls")
    return graphicsLayer { alpha = opacity }.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { if (visible || (!immediate && opacity > 0f)) placeable.place(0, 0) }
    }
}

/** Gesture labels update here without recomposing the video and all its controls. */
@Composable
private fun GestureFeedback(text: () -> String?, boosted: () -> Boolean, modifier: Modifier) {
    val label = text() ?: return
    Text(label, color = if (label.startsWith("Volume") && boosted()) Color(0xFFFFC857) else Color.White,
        modifier = modifier.background(Color.Black.copy(alpha = .8f), RoundedCornerShape(12.dp)).padding(16.dp))
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(uri: Uri, title: String, onBack: () -> Unit, folder: String = "", model: PlayerViewModel = viewModel()) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalActivity.current
    val host = activity as? MainActivity
    val inPip = host?.pipMode == true
    val context = LocalContext.current
    var playerView by remember { mutableStateOf<GesturePlayerView?>(null) }
    var report by remember { mutableStateOf<String?>(null) }
    var screenshot by remember {mutableStateOf<Uri?>(null)}
    var screenshotBusy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val focus=remember {FocusRequester()}
    LaunchedEffect(Unit){focus.requestFocus()}
    report?.let { PlaybackProblemDialog(it) { report = null } }
    val defaults by AppSettings.get(context).state.collectAsStateWithLifecycle()
    val window = activity?.window
    val orientation = rememberOrientationController()
    val audio = remember(context) { context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var locked by rememberSaveable { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var unlockVisible by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var speedSheet by remember { mutableStateOf(false) }
    var enhanceSheet by remember { mutableStateOf(false) }
    var customise by remember { mutableStateOf(false) }
    var audioSheet by remember { mutableStateOf(false) }
    var sleepDialog by remember { mutableStateOf(false) }
    var night by rememberSaveable { mutableStateOf(false) }
    var actionsOpen by remember { mutableStateOf(false) }
    val (quickLayout,saveQuickLayout) = rememberQuickActions()
    val layoutPrefs=remember(context){context.getSharedPreferences("vxplayer",0)}
    var aspect by rememberSaveable { mutableStateOf(AspectMode.parse(layoutPrefs.all["playerAspect"],defaults.aspect)) }
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
    fun feedback(text: String) { indicator = text; indicatorTick++ }
    fun touch() { interaction++ }
    fun takeScreenshot() {
        if(screenshotBusy)return
        if(model.casting){feedback("Screenshots capture phone playback. Disconnect Cast first.");return}
        if(model.isPrivatePlayback || model.player?.videoFormat?.drmInitData!=null) {feedback("Screenshots are unavailable for protected video.");return}
        actionsOpen=false;screenshotBusy=true
        scope.launch {
            try {
                val bitmap=captureVideoFrame(playerView ?: error("No video frame is available."))
                try {screenshot=saveVideoScreenshot(context,bitmap)} finally {bitmap.recycle()}
                android.widget.Toast.makeText(context,"Saved to Pictures/VXPlayer",android.widget.Toast.LENGTH_SHORT).show()
            } catch(cancelled: kotlinx.coroutines.CancellationException){throw cancelled}
            catch(failure: Exception){feedback(if(failure is IllegalStateException)failure.message ?: "Frame unavailable." else "Could not capture this frame. Protected video cannot be captured.")}
            finally {screenshotBusy=false}
        }
    }
    val screenshotPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->if(granted)takeScreenshot() else feedback("Gallery access was denied.")}
    screenshot?.let { image -> AlertDialog(onDismissRequest={screenshot=null},title={Text("Screenshot saved")},text={Text("Pictures/VXPlayer")},
        confirmButton={TextButton(onClick={runCatching {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(image,"image/png").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))}.onFailure {feedback("No image viewer found.")};screenshot=null}){Text("Open")}},
        dismissButton={TextButton(onClick={runCatching {context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("image/png").putExtra(android.content.Intent.EXTRA_STREAM,image).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),"Share screenshot"))};screenshot=null}){Text("Share")}}) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!model.setBackground(true)) feedback("Background playback could not start. Try again while the app is visible.")
    }
    LaunchedEffect(model.notice) { model.notice?.let { feedback(it) } }
    LaunchedEffect(model.player) {if(model.player!=null && !model.isPrivatePlayback)model.prepareCast()}
    LaunchedEffect(playerView, model.player, model.isPlaying, model.videoEnhancer.enabled,
        model.videoEnhancer.lastMode, model.videoEnhancer.suspended, model.videoEnhancer.comparing, model.videoEnhancer.available, model.seeking, model.seekDragging, model.buffering) {
        val view = playerView
        val engine = model.videoEnhancer
        if (view != null && !model.casting && model.isPlaying && engine.enabled && engine.available && engine.lastMode == "Auto" && !engine.suspended && !engine.comparing && !model.seeking && !model.seekDragging && !model.buffering) {
            while (true) {
                if (model.foreground && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.autoEnhanceSample(sampleVideoFrame(view))
                delay(1500)
            }
        }
    }
    BackHandler(locked && !inPip) { unlockVisible = true; touch() }
    LaunchedEffect(model) {
        snapshotFlow {
            (controls && model.isPlaying && !menu && !speedSheet && !enhanceSheet && !customise && !audioSheet &&
                !sleepDialog && !actionsOpen && !model.choosingSubtitle && !swiping && !model.seekDragging) to interaction
        }.collectLatest { (hide, _) -> if (hide) { delay(3000); controls = false } }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(indicatorTick, swiping, heldSpeed) }.collectLatest { (_, dragging, held) ->
            if (!dragging && held == null) { delay(1000); indicator = null }
        }
    }
    LaunchedEffect(pulseTick) {
        if (pulseZone != 0) { pulse.snapTo(0f); pulse.animateTo(1f, tween(650)); pulseZone = 0 }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { unlockVisible to interaction }.collectLatest { (shown, _) ->
            if (shown) { delay(3000); unlockVisible = false }
        }
    }
    LaunchedEffect(model.activeUri) { zoom = 1f; controls = true; touch() }
    DisposableEffect(host, model) {
        host?.playerCallbackOwner = model
        host?.canEnterPip = { model.player?.let { it.playWhenReady && it.playbackState != androidx.media3.common.Player.STATE_ENDED } == true && !model.casting && !model.failed && !model.choosingSubtitle && !model.backgroundEnabled }
        host?.releaseHiddenPlayer = { model.onHidden() }
        onDispose {
            if (host?.playerCallbackOwner === model) {
                host.playerCallbackOwner = null; host.canEnterPip = { false }; host.releaseHiddenPlayer = {}; host.updatePip()
            }
        }
    }
    LaunchedEffect(host, model.isPlaying, model.choosingSubtitle, model.backgroundEnabled) { host?.updatePip() }
    LaunchedEffect(inPip) { model.setPipActive(inPip) }
    DisposableEffect(lifecycle, uri) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { model.onForeground(); model.start(uri, title, folder); playerView?.player=model.player }
                Lifecycle.Event.ON_PAUSE -> model.savePlaybackState()
                Lifecycle.Event.ON_STOP -> if (host?.pipMode != true) { playerView?.player=null; model.onHidden() }
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
    if (model.audioEnhancer.warning) AlertDialog(
        onDismissRequest = { model.audioEnhancer.acknowledgeWarning(false) },
        title = { Text("High volume boost") },
        text = { Text("Above 150% can distort sound and may damage speakers or hearing. Keep listening levels low.") },
        confirmButton = { TextButton(onClick = { model.audioEnhancer.acknowledgeWarning(true) }) { Text("Enable boost") } },
        dismissButton = { TextButton(onClick = { model.audioEnhancer.acknowledgeWarning(false) }) { Text("Cancel") } })
    val visible = controls && !locked && !inPip
    CompositionLocalProvider(LocalContentColor provides Color.White) {
    Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).onPreviewKeyEvent {event ->
        if(event.type!=KeyEventType.KeyDown)false else when(event.key){
            Key.DirectionRight,Key.MediaFastForward->{model.seekBy(10_000);true}
            Key.DirectionLeft,Key.MediaRewind->{model.seekBy(-10_000);true}
            else->false
        }
    }.focusable()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx -> GesturePlayerView(ctx).apply {
            playerView = this
            useController = false
            setEnableComposeSurfaceSyncWorkaround(true)
            layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
        } }, update = { view ->
            view.player = if(!model.casting && (model.foreground || inPip))model.player else null
            view.keepScreenOn = model.isPlaying && model.foreground
            view.subtitleView?.apply {
                setApplyEmbeddedStyles(false); setApplyEmbeddedFontSizes(false)
                setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, model.subtitleSize)
                setStyle(androidx.media3.ui.CaptionStyleCompat(model.subtitleColor, model.subtitleBackground, android.graphics.Color.TRANSPARENT,
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null))
            }
            view.locked = locked || inPip
            view.applyDisplayMode(if(inPip)AspectMode.Fit else aspect)
            val frame = view.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
            val size = model.videoSize
            val width = if (aspect == AspectMode.Original && size.width > 0 && !inPip) displayDimensions(size.width, size.height, 0, size.pixelWidthHeightRatio).first else -1
            val height = if (aspect == AspectMode.Original && size.height > 0 && !inPip) size.height else -1
            if (frame.layoutParams.width != width || frame.layoutParams.height != height) frame.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.CENTER)
            frame.scaleX = if (inPip) 1f else zoom; frame.scaleY = if (inPip) 1f else zoom
            view.onTap = { if (locked) { unlockVisible = true; touch() } else { controls = !controls; touch() } }
            view.onOpenActions = { if (!locked && !inPip) { actionsOpen=true; controls=true; touch() } }
            view.onDoubleTapZone = { zone ->
                if (zone == 0) {model.playback?.let { if (it.isPlaying) it.pause() else it.play() }}
                else if (model.seekBy(zone * 10000L)) { feedback(if (zone < 0) "−10 s" else "+10 s"); pulseZone = zone; pulseTick++ }
                touch()
            }
            view.onVerticalStart = { left ->
                swiping = true; leftSwipe = left
                swipeStart = if (left) brightness.takeIf { it >= 0 } ?: (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
                    else { model.audioEnhancer.syncSystemVolume(); model.audioEnhancer.percent.toFloat() }
            }
            view.onVertical = { fraction ->
                if (leftSwipe) { brightness = (swipeStart + fraction).coerceIn(.01f, 1f); feedback("Brightness ${(brightness * 100).roundToInt()}%") }
                else {
                    val limit = if (defaults.allowBoost && model.audioEnhancer.boostAvailable) defaults.maxBoost else 100
                    model.audioEnhancer.request((swipeStart + fraction * limit).roundToInt())
                    feedback(model.audioEnhancer.message ?: "Volume ${model.audioEnhancer.percent}%")
                }
            }
            view.onHorizontalStart = { swiping = true; model.changeSeekDragging(true); seekStart = model.playback?.currentPosition ?: 0L }
            view.onHorizontal = { distanceDp, widthDp ->
                if (model.playback?.isCurrentMediaItemSeekable == true) {
                    val target = seekPreview(seekStart, distanceDp, widthDp, model.playback?.duration ?: 0L, defaults.seekSensitivity); seekTarget = target
                    val delta = (target - seekStart) / 1000
                    feedback("${formatDuration(target)} (${if (delta >= 0) "+" else ""}$delta s)")
                } else feedback("Seeking is not available for this stream")
            }
            view.onGestureEnd = { cancelled -> if (!cancelled) seekTarget?.let { model.seekTo(it) }; seekTarget = null; swiping = false; model.changeSeekDragging(false); touch() }
            view.onZoom = { factor ->
                if (factor.isFinite() && factor > 0f) {
                    zoom = (zoom * factor).coerceIn(.25f, 4f)
                    feedback("Zoom ${(zoom * 100).roundToInt()}%")
                    touch()
                }
            }
            view.onHold = { held ->
                if (held && heldSpeed == null) { heldSpeed = model.speed; model.changeSpeed(2f); feedback("2× while held") }
                else if (!held) { heldSpeed?.let { model.changeSpeed(it) }; heldSpeed = null; indicator = null }
            }
        }, onRelease = { it.player = null; it.keepScreenOn = false })
        if (night) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.3f)))
        if (!inPip && pulseZone != 0) Box(Modifier.align(if (pulseZone < 0) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(24.dp).size((72 + 48 * pulse.value).dp).background(Color.White.copy(alpha = .22f * (1f - pulse.value)), CircleShape))
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().overlayVisible(visible, locked || inPip)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .88f), Color.Black.copy(alpha = .3f), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal))) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                VXIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Text(model.activeTitle.ifBlank { title }, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                if(!model.casting)TrackMenu(model)
                if(!model.isPrivatePlayback)com.vxplayer.cast.VXCastButton {model.prepareCast()}
                if(!model.casting)TextButton(onClick={model.toggleDecoder();touch()}) {Text(if(model.softwareDecoding)"SW" else "HW",color=Color.White)}
                VXIconButton(Icons.Default.MoreVert, "Playback options", { menu = true; touch() })
            }
        }
        PlayerExtraMenu(model, show = menu && !inPip, onDismiss = { menu = false; touch() },
            onCustomise = { customise = true; touch() },
            onSpeed = { menu = false; speedSheet = true; touch() }, orientationLocked = orientation.locked,
            onOrientation = {
                orientation.toggleLock(currentOrientation(context.resources.configuration.orientation==2,activity?.windowManager?.defaultDisplay?.rotation ?: 0))
                touch()
            })
        if (speedSheet && !inPip) SpeedSheet(model) { speedSheet = false; touch() }
        if (enhanceSheet && !inPip) EnhanceSheet(model) { enhanceSheet = false; touch() }
        if (customise && !inPip) CustomiseActionsSheet(quickLayout,saveQuickLayout) { customise = false; touch() }
        if (audioSheet && !inPip) AudioEnhanceSheet(model) { audioSheet = false; touch() }
        if (sleepDialog && !inPip) AlertDialog(onDismissRequest={sleepDialog=false},title={Text("Sleep timer · ${model.sleepLabel}")},text={Column {
            listOf(0,15,30,60,-1).forEach { minutes -> TextButton(onClick={model.setSleep(minutes);sleepDialog=false;touch()}) {Text(if(minutes<0)"End of video" else if(minutes==0)"Off" else "$minutes minutes")} }
        }},confirmButton={TextButton(onClick={sleepDialog=false;touch()}){Text("Close")}})
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().overlayVisible(visible, locked || inPip)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f), Color.Black.copy(alpha = .9f))))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom+WindowInsetsSides.Horizontal)).padding(horizontal = 16.dp, vertical = 8.dp)) {
            PlayerQuickActions(if(model.casting)quickLayout.copy(hidden=quickLayout.hidden+setOf(PlayerAction.Screenshot,PlayerAction.Equalizer,PlayerAction.RepeatAB,PlayerAction.Sleep,PlayerAction.Shuffle,PlayerAction.Loop,PlayerAction.Mute,PlayerAction.Background)) else quickLayout,buildSet {
                if(night)add(PlayerAction.Night)
                if(model.playbackMode==PlaybackMode.Shuffle)add(PlayerAction.Shuffle)
                if(model.playbackMode in listOf(PlaybackMode.RepeatOne,PlaybackMode.RepeatFolder))add(PlayerAction.Loop)
                if(model.muted)add(PlayerAction.Mute)
                if(model.sleepLabel!="Off")add(PlayerAction.Sleep)
                if(model.aMs!=null)add(PlayerAction.RepeatAB)
                if(model.backgroundEnabled)add(PlayerAction.Background)
                if(orientation.locked)add(PlayerAction.RotationLock)
                if(orientation.mode!=RotationMode.Auto)add(PlayerAction.Rotation)
                if(model.speed!=1f)add(PlayerAction.Speed)
            },labels=mapOf(PlayerAction.Speed to "Speed\n${speedActionLabel(model.speed)}",PlayerAction.Loop to when(model.playbackMode){PlaybackMode.RepeatOne->"Loop · One";PlaybackMode.RepeatFolder->"Loop · All";else->"Loop"},PlayerAction.RepeatAB to if(model.aMs!=null&&model.bMs==null)"Set B" else "A-B Repeat"),
                touch=::touch,action={ action ->
                    when(action) {
                        PlayerAction.Night -> night=!night
                        PlayerAction.Customise -> {actionsOpen=false;customise=true}
                        PlayerAction.Shuffle -> model.selectPlaybackMode(if(model.playbackMode==PlaybackMode.Shuffle)PlaybackMode.Off else PlaybackMode.Shuffle)
                        PlayerAction.Loop -> model.selectPlaybackMode(when(model.playbackMode){PlaybackMode.RepeatOne->PlaybackMode.RepeatFolder;PlaybackMode.RepeatFolder->PlaybackMode.Off;else->PlaybackMode.RepeatOne})
                        PlayerAction.Mute -> model.toggleMute()
                        PlayerAction.Sleep -> {actionsOpen=false;sleepDialog=true}
                        PlayerAction.RepeatAB -> when {model.aMs==null->{model.setA();feedback("A set. Seek ahead and tap Set B.")};model.bMs==null->{if(!model.setB())feedback("Set B at least half a second after A.")};else->model.clearRepeat()}
                        PlayerAction.Equalizer -> {actionsOpen=false;audioSheet=true}
                        PlayerAction.Speed -> {actionsOpen=false;speedSheet=true}
                        PlayerAction.Rotation -> {orientation.cycle();feedback("Rotation: ${orientation.mode.label}")}
                        PlayerAction.RotationLock -> {orientation.toggleLock(currentOrientation(context.resources.configuration.orientation==2,activity?.windowManager?.defaultDisplay?.rotation ?: 0));feedback(if(orientation.locked)"Rotation locked" else "Rotation: Auto")}
                        PlayerAction.Background -> {
                            if(!model.backgroundEnabled && Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            else if(!model.setBackground(!model.backgroundEnabled))feedback("Background play is unavailable for this video.")
                        }
                        PlayerAction.Screenshot -> {
                            if(Build.VERSION.SDK_INT<29 && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                                screenshotPermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            else takeScreenshot()
                        }
                    }
                },leading={Column(horizontalAlignment=Alignment.CenterHorizontally){
                    if(!model.casting)SmartEnhanceControl(model,{actionsOpen=false;enhanceSheet=true},::touch)
                    Text(if(model.casting)"Phone effects only" else if(model.videoEnhancer.enabled)"Smart Enhance: ${if(model.enhancementUnavailable)"Unavailable" else if(model.videoEnhancer.comparing)"Original preview" else model.videoEnhancer.lastMode}" else "Smart Enhance",
                        color=if(model.videoEnhancer.enabled)MaterialTheme.colorScheme.primary else Color.White,style=MaterialTheme.typography.labelSmall,
                        modifier=Modifier.heightIn(min=32.dp).background(Color.Black.copy(alpha=.6f),RoundedCornerShape(12.dp)).padding(4.dp))
                }},open=actionsOpen && !locked && !inPip,close={actionsOpen=false;touch()})
            PlaybackTimeline(model, visible && !inPip, ::touch)
            CenteredPlaybackControls(model.isPlaying,model.buffering,locked,
                onLock={locked=true;controls=false;unlockVisible=true;touch()},
                onToggle={model.playback?.let {if(it.playWhenReady)it.pause() else it.play()};touch()},
                previous={model.moveInPlaylist(-1);touch()},next={model.moveInPlaylist(1);touch()},
                canPrevious=!model.casting&&model.player!=null&&model.canMoveInPlaylist(-1),canNext=!model.casting&&model.player!=null&&model.canMoveInPlaylist(1),
                aspect={aspect=aspect.next();zoom=1f;layoutPrefs.edit().putString("playerAspect",aspect.name).apply();feedback(aspect.label);touch()},
                aspectIcon=when(aspect){AspectMode.Fit->Icons.Default.FitScreen;AspectMode.Fill->Icons.Default.Fullscreen;AspectMode.Zoom->Icons.Default.ZoomIn;AspectMode.Wide,AspectMode.Classic->Icons.Default.Crop;AspectMode.Original->Icons.Default.AspectRatio},
                pip=if(Build.VERSION.SDK_INT>=26&&host!=null&&!model.casting&&!model.isPrivatePlayback&&!model.backgroundEnabled) { {host.enterPlayerPip(true);touch();Unit} } else null)
        }
        if(model.casting)Text("Playing on Cast receiver",color=Color.White,modifier=Modifier.align(Alignment.Center).background(Color.Black).padding(20.dp))
        if(visible && !locked && !inPip) IconButton(onClick={actionsOpen=true;touch()},modifier=Modifier.align(Alignment.CenterEnd).background(Color.Black.copy(alpha=.35f),CircleShape)) {
            Icon(Icons.Default.ChevronLeft,"Open quick actions",tint=Color.White)
        }
        if (locked && unlockVisible && !inPip) IconButton(onClick = { locked = false; controls = true; unlockVisible = false; touch() }, modifier = Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(16.dp).background(Color.Black.copy(alpha = .8f),CircleShape)) { Icon(Icons.Default.LockOpen,"Unlock controls",tint=Color.White) }
        if (!inPip) GestureFeedback({ indicator }, { model.audioEnhancer.percent > 100 }, Modifier.align(Alignment.Center))
        if (model.buffering && !model.failed) Column(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .7f), RoundedCornerShape(16.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Text("Buffering…", color = Color.White, modifier = Modifier.padding(top = 8.dp))
        }
        if (model.failed) Column(Modifier.align(Alignment.Center).fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).background(Color.Black.copy(alpha = .85f)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, "Playback error", tint = Color.White, modifier = Modifier.size(40.dp))
            Text(model.failureMessage, color = Color.White)
            TextButton(onClick = { model.retryPlayback() }) { Text("Retry", color = Color.White) }
            TextButton(onClick = { model.playWithoutEnhance() }) { Text("Play without enhance", color = Color.White) }
            TextButton(onClick = { val text = model.problemReport(); copyProblemReport(context, text); report = text }) { Text("Copy report", color = Color.White) }
            if (model.failureMessage.contains(com.vxplayer.library.StreamPageProbe.HTML_MESSAGE) &&
                com.vxplayer.web.WebShield.host(uri.toString()) != null) {
                TextButton(onClick = { model.player?.pause(); com.vxplayer.web.WebPlayerActivity.open(context, uri.toString()) }) {
                    Text("Open web player · Shield ON", color = Color.White)
                }
            }
            TextButton(onClick = onBack) { Text("Back", color = Color.White) }
        }
        if (!inPip && !model.failed) model.audioWarning?.let { warning -> Text(warning, color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 80.dp, start = 16.dp, end = 16.dp)
                .background(Color.Black.copy(alpha = .85f), RoundedCornerShape(12.dp)).padding(12.dp), style = MaterialTheme.typography.bodySmall) }
    }
    }
}
