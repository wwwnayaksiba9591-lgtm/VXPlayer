package com.vxplayer.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.vxplayer.library.formatDuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ArrowForward

fun playbackModeIcon(mode: PlaybackMode) = when (mode) {
    PlaybackMode.Off -> Icons.Default.ArrowForward
    PlaybackMode.RepeatOne -> Icons.Default.RepeatOne
    PlaybackMode.RepeatFolder -> Icons.Default.Repeat
    PlaybackMode.Shuffle -> Icons.Default.Shuffle
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerExtraMenu(model: PlayerViewModel, show: Boolean, onDismiss: () -> Unit, onSpeed: () -> Unit,
    onOrientation: () -> Unit, orientationLocked: Boolean) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var audioSheet by remember { mutableStateOf(false) }
    var enhanceSheet by remember { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!model.setBackground(true)) message = "Background playback could not start."
    }
    if (audioSheet) AudioEnhanceSheet(model) { audioSheet = false }
    if (enhanceSheet) EnhanceSheet(model) { enhanceSheet = false }
    if (show) ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
    Text("Playback options", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
    DropdownMenuItem(text = { Text("Speed · ${model.speed}×") }, onClick = onSpeed)
    DropdownMenuItem(text = { Text("Folder playback · ${model.playbackMode.label}") },
        leadingIcon = { Icon(playbackModeIcon(model.playbackMode), null) }, onClick = { model.cyclePlaybackMode() })
    DropdownMenuItem(text = { Text("Audio enhance · ${model.audioEnhancer.percent}%") }, onClick = { onDismiss(); audioSheet = true })
    if (model.videoEnhancer.available) DropdownMenuItem(text = { Text("Video enhance · ${model.videoEnhancer.preset}") }, onClick = { onDismiss(); enhanceSheet = true })
    else DropdownMenuItem(text = { Text(model.videoEnhancer.reason.orEmpty()) }, onClick = {}, enabled = false)
    DropdownMenuItem(text = { Text(if (orientationLocked) "Unlock orientation" else "Lock orientation") }, onClick = onOrientation)
    DropdownMenuItem(text = { Text("Sleep timer: ${model.sleepLabel}") }, onClick = { dialog = "sleep" })
    if (!model.isPrivatePlayback) DropdownMenuItem(text = { Text(if (model.backgroundEnabled) "Stop background/audio-only play" else "Background/audio-only play") }, onClick = {
        if (model.backgroundEnabled) model.setBackground(false)
        else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (!model.setBackground(true)) message = "Background playback could not start."
    })
    if (model.isPrivatePlayback) Text("Private playback pauses when hidden; PiP and background audio are unavailable here.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
    DropdownMenuItem(text = { Text("Set A${model.aMs?.let { " (${formatDuration(it)})" }.orEmpty()}") }, onClick = { model.setA() })
    DropdownMenuItem(text = { Text("Set B${model.bMs?.let { " (${formatDuration(it)})" }.orEmpty()}") }, onClick = { if (!model.setB()) message = "Set A first, then set B at least half a second later." })
    DropdownMenuItem(text = { Text("Clear A–B repeat") }, onClick = { model.clearRepeat() })
    DropdownMenuItem(text = { Text("Subtitle appearance") }, onClick = { dialog = "style" })
    }
    }
    if (dialog == "sleep") AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Sleep timer") }, text = {
        Column { listOf(0, 15, 30, 45, 60, -1).forEach { minutes ->
            TextButton(onClick = { model.setSleep(minutes); dialog = null }) { Text(when { minutes < 0 -> "End of video"; minutes == 0 -> "Off"; else -> "$minutes minutes" }) }
        } }
    }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } })
    if (dialog == "style") AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Subtitle appearance") }, text = {
        Column {
            Text("Size")
            Row { listOf(16f, 20f, 24f, 28f).forEach { size -> TextButton(onClick = { model.styleSubtitles(size = size) }) { Text("${size.toInt()}") } } }
            Text("Color")
            Row { listOf("White" to android.graphics.Color.WHITE, "Yellow" to android.graphics.Color.YELLOW, "Green" to android.graphics.Color.GREEN).forEach { (label, color) -> TextButton(onClick = { model.styleSubtitles(color = color) }) { Text(label) } } }
            Text("Background")
            Row { listOf("None" to 0, "Dark" to 0x99000000.toInt(), "Black" to android.graphics.Color.BLACK).forEach { (label, color) -> TextButton(onClick = { model.styleSubtitles(background = color) }) { Text(label) } } }
        }
    }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("Done") } })
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }) }
}
