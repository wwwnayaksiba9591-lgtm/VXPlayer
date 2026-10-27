package com.vxplayer.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.vxplayer.R

@Composable
fun OrientationControls() {
    val activity = LocalActivity.current ?: return
    val configuration = LocalConfiguration.current
    val original = rememberSaveable { activity.requestedOrientation }
    var locked by rememberSaveable { mutableStateOf(false) }
    var requested by rememberSaveable { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    DisposableEffect(activity) {
        onDispose { if (!activity.isChangingConfigurations) activity.requestedOrientation = original }
    }
    LaunchedEffect(activity, requested) { activity.requestedOrientation = requested }
    TextButton(onClick = {
        locked = !locked
        requested = if (!locked) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }) { Text(stringResource(if (locked) R.string.unlock_orientation else R.string.lock_orientation), color = Color.White) }
    TextButton(onClick = {
        locked = true
        requested = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }) { Text(stringResource(R.string.rotate_screen), color = Color.White) }
}
