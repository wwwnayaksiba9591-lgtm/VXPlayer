package com.vxplayer.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import com.vxplayer.library.formatDuration
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

/** Only this small subtree observes position; a hidden timeline does not poll the player. */
@Composable
fun PlaybackTimeline(model: PlayerViewModel, visible: Boolean, touch: () -> Unit) {
    var position by remember { mutableLongStateOf(0) }
    var duration by remember { mutableLongStateOf(0) }
    var preview by remember { mutableStateOf<Float?>(null) }
    var remaining by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(model.playback,visible) {
        while (visible) {
            val current = model.playback
            val next = current?.currentPosition?.coerceAtLeast(0) ?: 0
            val length = current?.duration?.takeIf { it > 0 } ?: 0
            if (position != next) position = next
            if (duration != length) duration = length
            delay(300)
        }
    }
    DisposableEffect(Unit) { onDispose { model.changeSeekDragging(false) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick={model.seekBy(-10_000);touch()}){Icon(Icons.Default.Replay10,"Back 10 seconds",tint=Color.White)}
        Text(formatDuration(preview?.toLong() ?: position),color = Color.White, style = MaterialTheme.typography.labelMedium)
        SmoothPositionSlider(position, duration, preview, model.isPlaying && !model.seeking && !model.buffering,
            onValueChange = { preview = it; model.changeSeekDragging(true); touch() },
            onValueChangeFinished = { preview?.let { model.seekTo(it.toLong(), exact = true) }; preview = null; model.changeSeekDragging(false); touch() },
            enabled = duration > 0 && model.playback?.isCurrentMediaItemSeekable == true,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        TextButton(onClick = { remaining = !remaining; touch() }) {
            Text(if (duration <= 0) "--:--" else if (remaining) "−${formatDuration((duration-position).coerceAtLeast(0))}" else formatDuration(duration),color = Color.White)
        }
        IconButton(onClick={model.seekBy(10_000);touch()}){Icon(Icons.Default.Forward10,"Forward 10 seconds",tint=Color.White)}
    }
}

/** Only the slider animates between polls; drag previews always follow the finger immediately. */
@Composable
private fun SmoothPositionSlider(position: Long, duration: Long, preview: Float?, smooth: Boolean,
    onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val end=duration.coerceAtLeast(1).toFloat()
    val target=position.coerceIn(0,duration.coerceAtLeast(1)).toFloat()
    val animated by animateFloatAsState(targetValue=target,
        animationSpec=if(smooth && preview==null)tween(300,easing=LinearEasing) else snap(),label="Playback progress")
    Slider(value=(preview ?: animated).coerceIn(0f,end),onValueChange=onValueChange,
        onValueChangeFinished=onValueChangeFinished,valueRange=0f..end,enabled=enabled,
        colors=SliderDefaults.colors(thumbColor=Color.White,activeTrackColor=Color.White),modifier=modifier)
}
