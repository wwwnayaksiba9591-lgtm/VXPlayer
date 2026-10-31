package com.vxplayer.ui

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Reserve only the right edge; drags elsewhere remain available to sliders and artwork. */
fun Modifier.quickActionEdge(open: ()->Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down=awaitFirstDown(requireUnconsumed=false)
        if(down.position.x<size.width-24.dp.toPx())return@awaitEachGesture
        val start=down.position
        var opened=false
        do {
            val event=awaitPointerEvent()
            val point=event.changes.firstOrNull {it.id==down.id} ?: break
            if(point.isConsumed && !opened)return@awaitEachGesture
            val delta=point.position-start
            if(!opened && opensActionPanel(start.x/density,size.width/density,delta.x/density,delta.y/density)) {
                opened=true; open()
            }
            if(opened)point.consume()
        } while(event.changes.any {it.pressed})
    }
}

@Composable
fun rememberQuickActions(): Pair<QuickActionLayout,(QuickActionLayout)->Unit> {
    val context = LocalContext.current
    val prefs = remember(context) { context.applicationContext.getSharedPreferences("vxplayer",0) }
    fun read() = parseQuickActions(prefs.all["quickActions.order"] as? String,prefs.all["quickActions.hidden"] as? String)
    var value by remember(prefs) { mutableStateOf(read()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _,key -> if(key?.startsWith("quickActions.")==true)value=read() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return value to { next ->
        value=next
        prefs.edit().putString("quickActions.order",next.order.joinToString(","){it.name}).putString("quickActions.hidden",next.hidden.joinToString(","){it.name}).apply()
    }
}
private fun actionIcon(action: PlayerAction): ImageVector = when(action) {
    PlayerAction.Night -> Icons.Default.DarkMode
    PlayerAction.Customise -> Icons.Default.Tune
    PlayerAction.Shuffle -> Icons.Default.Shuffle
    PlayerAction.Loop -> Icons.Default.Repeat
    PlayerAction.Mute -> Icons.Default.VolumeOff
    PlayerAction.Sleep -> Icons.Default.Timer
    PlayerAction.RepeatAB -> Icons.Default.Replay
    PlayerAction.Equalizer -> Icons.Default.Equalizer
    PlayerAction.Speed -> Icons.Default.Speed
    PlayerAction.Screenshot -> Icons.Default.Screenshot
    PlayerAction.Rotation -> Icons.Default.ScreenRotation
    PlayerAction.RotationLock -> Icons.Default.ScreenLockRotation
    PlayerAction.Background -> Icons.Default.Headphones
}

@Composable
fun PlayerQuickActions(layout: QuickActionLayout,active: Set<PlayerAction>, labels: Map<PlayerAction,String> = emptyMap(),
    unavailable: Set<PlayerAction> = emptySet(), touch: ()->Unit, action: (PlayerAction)->Unit, leading: @Composable ()->Unit = {},
    open: Boolean = false, close: ()->Unit = {}) {
    var attached by remember {mutableStateOf(open)}
    LaunchedEffect(open){if(open)attached=true else {delay(180);attached=false}}
    if (!attached && !open) return
    val context=LocalContext.current
    var interaction by remember { mutableIntStateOf(0) }
    var pressed by remember {mutableStateOf(false)}
    var appeared by remember {mutableStateOf(false)}
    var dragOffset by remember {mutableFloatStateOf(0f)}
    LaunchedEffect(open){appeared=open}
    val slide by animateFloatAsState(if(appeared && open)0f else 1f,tween(180),label="Quick action panel")
    val scroll = rememberScrollState()
    val latestClose by rememberUpdatedState(close)
    LaunchedEffect(open) {
        if (open) snapshotFlow { Triple(interaction, pressed, scroll.isScrollInProgress) }
            .collectLatest { (_, touching, scrolling) ->
                if (!touching && !scrolling) { delay(4000); latestClose() }
            }
    }
    Dialog(onDismissRequest=close, properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().clickable(onClick=close))
    Column(Modifier.align(Alignment.CenterEnd).width(maxWidth * .25f).fillMaxHeight()
        .graphicsLayer {translationX=size.width*slide+dragOffset}
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                pressed=true;interaction++
                do {val event=awaitPointerEvent(PointerEventPass.Initial)} while(event.changes.any {it.pressed})
                pressed=false;interaction++
            }
        }
        .background(Color.Black.copy(alpha=.72f)).pointerInput(Unit) {
            detectHorizontalDragGestures(onDragStart={dragOffset=0f},onDragCancel={dragOffset=0f},onDragEnd={
                if(dragOffset>24.dp.toPx())close() else dragOffset=0f
            },onHorizontalDrag={change,amount ->
                change.consume(); interaction++; dragOffset=(dragOffset+amount).coerceAtLeast(0f)
            })
        }.verticalScroll(scroll).safeDrawingPadding().padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        leading()
        layout.order.filter {it !in layout.hidden}.forEach { item ->
            val supported=item !in unavailable
            Column(Modifier.width(80.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Surface(shape=CircleShape,color=if(item in active)Color(0xFF1565C0) else Color.White.copy(alpha=.12f),
                    modifier=Modifier.size(48.dp).semantics { if(!supported){stateDescription="Unavailable for this source";disabled()} }.clickable {
                        interaction++; touch()
                        if(supported)action(item) else Toast.makeText(context,"${item.label}: Unavailable for this source",Toast.LENGTH_SHORT).show()
                    }) {
                    Box(contentAlignment=Alignment.Center) {Icon(actionIcon(item),item.label,tint=Color.White.copy(alpha=if(supported)1f else .38f))}
                }
                Text(labels[item] ?: item.label,color=Color.White.copy(alpha=if(supported)1f else .5f),style=MaterialTheme.typography.labelSmall,
                    textAlign=TextAlign.Center,maxLines=2,modifier=Modifier.fillMaxWidth().heightIn(min=32.dp).padding(top=4.dp))
            }
        }
    }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomiseActionsSheet(layout: QuickActionLayout, update: (QuickActionLayout)->Unit, close: ()->Unit) {
    ModalBottomSheet(onDismissRequest=close,sheetMaxWidth=560.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Customise quick actions",style=MaterialTheme.typography.titleLarge)
            Text("Choose visible actions and use the arrows to reorder them. Saved for music and video.")
            layout.order.forEachIndexed { index,item ->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(item.label,Modifier.weight(1f))
                    IconButton({update(layout.move(item,-1))},enabled=index>0){Icon(Icons.Default.KeyboardArrowUp,"Move ${item.label} up")}
                    IconButton({update(layout.move(item,1))},enabled=index<layout.order.lastIndex){Icon(Icons.Default.KeyboardArrowDown,"Move ${item.label} down")}
                    Switch(item !in layout.hidden,{update(layout.visible(item,it))})
                }
            }
            Row {TextButton({update(QuickActionLayout())}){Text("Reset")};TextButton(close){Text("Done")}}
        }
    }
}

/** Side groups never participate in transport positioning. Play's center is the Box center. */
@Composable
fun CenteredPlaybackControls(playing: Boolean,buffering: Boolean,locked: Boolean,onLock: ()->Unit,onToggle: ()->Unit,
    previous: ()->Unit,next: ()->Unit,canPrevious: Boolean=true,canNext: Boolean=true,
    aspect: (() -> Unit)? = null,pip: (() -> Unit)? = null,aspectIcon: ImageVector=Icons.Default.AspectRatio,enabled: Boolean=true) {
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min=80.dp)) {
        val compact=maxWidth<360.dp
        val tiny=maxWidth<304.dp
        val edge=48.dp
        val play=if(tiny)56.dp else if(compact)64.dp else 76.dp
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 48.dp) {
            IconButton(onLock,Modifier.align(Alignment.CenterStart).size(edge)) {Icon(if(locked)Icons.Default.LockOpen else Icons.Default.Lock,if(locked)"Unlock controls" else "Lock controls",tint=Color.White)}
            Row(Modifier.align(Alignment.Center),verticalAlignment=Alignment.CenterVertically) {
                IconButton(previous,Modifier.size(edge),enabled=enabled&&canPrevious){Icon(Icons.Default.SkipPrevious,"Previous",tint=Color.White.copy(alpha=if(canPrevious)1f else .38f))}
                FilledIconButton(onToggle,Modifier.size(play),enabled=enabled,colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.White.copy(alpha=.18f),contentColor=Color.White)) {
                    if(buffering)CircularProgressIndicator(Modifier.size(32.dp),color=Color.White,strokeWidth=2.dp)
                    else Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,if(playing)"Pause" else "Play",Modifier.size(40.dp))
                }
                IconButton(next,Modifier.size(edge),enabled=enabled&&canNext){Icon(Icons.Default.SkipNext,"Next",tint=Color.White.copy(alpha=if(canNext)1f else .38f))}
            }
            @Composable fun edges() {
                aspect?.let {IconButton(it,Modifier.size(edge)){Icon(aspectIcon,"Fit / aspect ratio",tint=Color.White)}}
                pip?.let {IconButton(it,Modifier.size(edge)){Icon(Icons.Default.PictureInPictureAlt,"Picture in picture",tint=Color.White)}}
            }
            if(compact)Column(Modifier.align(Alignment.CenterEnd)){edges()} else Row(Modifier.align(Alignment.CenterEnd)){edges()}
        }
    }
}
