package com.vxplayer.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

fun copyProblemReport(context: Context, report: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("VXPlayer playback report", report))
}

@Composable
fun PlaybackProblemDialog(report: String, close: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = close, title = { Text("Media info / Playback problem report") },
        text = { Text(report, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { copyProblemReport(context, report) }) { Text("Copy report") } },
        dismissButton = { TextButton(onClick = close) { Text("Close") } })
}
