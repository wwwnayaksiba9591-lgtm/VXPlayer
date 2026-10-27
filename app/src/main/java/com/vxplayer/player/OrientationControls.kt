package com.vxplayer.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.vxplayer.R

class OrientationController(locked: Boolean = false, requested: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
    var locked by mutableStateOf(locked)
    var requested by mutableIntStateOf(requested)
}

@Composable
fun rememberOrientationController(): OrientationController {
    val state = rememberSaveable(saver = Saver<OrientationController, List<Int>>(
        save = { listOf(if (it.locked) 1 else 0, it.requested) },
        restore = { OrientationController(it[0] == 1, it[1]) },
    )) { OrientationController() }
    val activity = LocalActivity.current ?: return state
    val original = rememberSaveable { activity.requestedOrientation }
    DisposableEffect(activity) {
        onDispose { if (!activity.isChangingConfigurations) activity.requestedOrientation = original }
    }
    LaunchedEffect(activity, state.requested) { activity.requestedOrientation = state.requested }
    return state
}

@Composable
fun OrientationControls(state: OrientationController) {
    val configuration = LocalConfiguration.current
    TextButton(onClick = {
        state.locked = !state.locked
        state.requested = if (!state.locked) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }) { Text(stringResource(if (state.locked) R.string.unlock_orientation else R.string.lock_orientation), color = Color.White) }
    TextButton(onClick = {
        state.locked = true
        state.requested = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }) { Text(stringResource(R.string.rotate_screen), color = Color.White) }
}
