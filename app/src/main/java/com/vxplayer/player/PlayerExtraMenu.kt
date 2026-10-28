package com.vxplayer.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.vxplayer.library.formatDuration

@Composable
fun PlayerExtraMenu(model: PlayerViewModel) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!model.setBackground(true)) message = "Background playback could not start."
    }
    DropdownMenuItem(text = { Text("Sleep timer: ${model.sleepLabel}") }, onClick = { dialog = "sleep" })
    DropdownMenuItem(text = { Text(if (model.backgroundEnabled) "Stop background/audio-only play" else "Background/audio-only play") }, onClick = {
        if (model.backgroundEnabled) model.setBackground(false)
        else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (!model.setBackground(true)) message = "Background playback could not start."
    })
    DropdownMenuItem(text = { Text("Set A${model.aMs?.let { " (${formatDuration(it)})" }.orEmpty()}") }, onClick = { model.setA() })
    DropdownMenuItem(text = { Text("Set B${model.bMs?.let { " (${formatDuration(it)})" }.orEmpty()}") }, onClick = { if (!model.setB()) message = "Set A first, then set B at least half a second later." })
    DropdownMenuItem(text = { Text("Clear A–B repeat") }, onClick = { model.clearRepeat() })
    DropdownMenuItem(text = { Text("Subtitle appearance") }, onClick = { dialog = "style" })
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
