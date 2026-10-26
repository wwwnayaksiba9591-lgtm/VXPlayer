package com.vxplayer.player

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(uri: Uri, title: String, onBack: () -> Unit, model: PlayerViewModel = viewModel()) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalActivity.current

    DisposableEffect(lifecycle, uri) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> model.start(uri, title)
                Lifecycle.Event.ON_PAUSE -> model.savePlaybackState()
                Lifecycle.Event.ON_STOP -> model.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) model.start(uri, title)
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
                PlayerView(context).apply {
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setEnableComposeSurfaceSyncWorkaround(true)
                }
            },
            update = {
                it.player = model.player
                it.keepScreenOn = model.isPlaying
            },
            onRelease = {
                it.player = null
                it.keepScreenOn = false
            },
        )
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f)).safeDrawingPadding().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back_to_library), color = Color.White) }
            Text(title, color = Color.White, modifier = Modifier.weight(1f).padding(8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (model.failed) {
            Text(stringResource(R.string.playback_failed), color = Color.White,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.8f)).padding(24.dp))
        }
    }
}
