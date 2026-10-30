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
import com.vxplayer.library.formatDuration
import kotlinx.coroutines.delay

/** Only this small subtree observes position; a hidden timeline does not poll the player. */
@Composable
fun PlaybackTimeline(model: PlayerViewModel, visible: Boolean, touch: () -> Unit) {
    var position by remember { mutableLongStateOf(0) }
    var duration by remember { mutableLongStateOf(0) }
    var preview by remember { mutableStateOf<Float?>(null) }
    var remaining by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(model.player,visible) {
        while (visible) {
            val current = model.player
            val next = current?.currentPosition?.coerceAtLeast(0) ?: 0
            val length = current?.duration?.takeIf { it > 0 } ?: 0
            if (position != next) position = next
            if (duration != length) duration = length
            delay(300)
        }
    }
    DisposableEffect(Unit) { onDispose { model.changeSeekDragging(false) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatDuration(preview?.toLong() ?: position),color = Color.White, style = MaterialTheme.typography.labelMedium)
        Slider(value = preview ?: position.coerceIn(0,duration.coerceAtLeast(1)).toFloat(),
            onValueChange = { preview = it; model.changeSeekDragging(true); touch() },
            onValueChangeFinished = { preview?.let { model.seekTo(it.toLong(), exact = true) }; preview = null; model.changeSeekDragging(false); touch() },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = duration > 0 && model.player?.isCurrentMediaItemSeekable == true,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        TextButton(onClick = { remaining = !remaining; touch() }) {
            Text(if (duration <= 0) "--:--" else if (remaining) "−${formatDuration((duration-position).coerceAtLeast(0))}" else formatDuration(duration),color = Color.White)
        }
    }
}
