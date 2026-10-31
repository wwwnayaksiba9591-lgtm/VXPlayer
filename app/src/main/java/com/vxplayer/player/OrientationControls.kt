package com.vxplayer.player

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class RotationMode(val label: String,val orientation: Int) {
    Auto("Auto",ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR), Portrait("Portrait",ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    Landscape("Landscape",ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE), ReversePortrait("Reverse Portrait",ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
    ReverseLandscape("Reverse Landscape",ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
    fun next()=entries[(ordinal+1)%entries.size]
    companion object { fun parse(value: Any?)=entries.firstOrNull {it.name==(value as? String)?.replace(" ","")} ?: Auto }
}
fun currentOrientation(landscape: Boolean,rotation: Int): Int = when {
    landscape && rotation in 2..3 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    rotation in 2..3 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}
@Stable class OrientationController(initialMode: RotationMode,initialLocked: Boolean,initialRequested: Int) {
    var mode by mutableStateOf(initialMode); private set
    var locked by mutableStateOf(initialLocked); private set
    var requested by mutableIntStateOf(initialRequested); private set
    fun cycle() {locked=false;mode=mode.next();requested=mode.orientation}
    fun toggleLock(current: Int) {locked=!locked;if(locked)requested=current else {mode=RotationMode.Auto;requested=mode.orientation}}
}
@Composable fun rememberOrientationController(): OrientationController {
    val context=LocalContext.current
    val prefs=remember {context.applicationContext.getSharedPreferences("vxplayer",0)}
    val state=remember {
        val mode=RotationMode.parse(prefs.all["rotationMode"] ?: prefs.all["defaultOrientation"])
        val locked=prefs.all["rotationLocked"] as? Boolean ?: false
        val requested=(prefs.all["rotationRequested"] as? Int)?.takeIf {it in RotationMode.entries.map {m->m.orientation}} ?: mode.orientation
        OrientationController(mode,locked,if(locked)requested else mode.orientation)
    }
    val activity=LocalActivity.current
    val original=remember(activity){activity?.requestedOrientation}
    DisposableEffect(activity) {onDispose {if(activity!=null && original!=null && !activity.isChangingConfigurations)runCatching {activity.requestedOrientation=original}}}
    LaunchedEffect(activity,state.requested,state.mode,state.locked) {
        runCatching {activity?.requestedOrientation=state.requested}
        prefs.edit().putString("rotationMode",state.mode.name).putBoolean("rotationLocked",state.locked).putInt("rotationRequested",state.requested).apply()
    }
    return state
}
@Composable fun OrientationControls(state: OrientationController) {
    val activity=LocalActivity.current;val context=LocalContext.current
    TextButton(onClick={state.toggleLock(currentOrientation(context.resources.configuration.orientation==2,activity?.windowManager?.defaultDisplay?.rotation ?: 0))}) {
        Text(if(state.locked)"Unlock rotation" else "Lock rotation",color=Color.White)
    }
    TextButton(onClick=state::cycle){Text(state.mode.label,color=Color.White)}
}
