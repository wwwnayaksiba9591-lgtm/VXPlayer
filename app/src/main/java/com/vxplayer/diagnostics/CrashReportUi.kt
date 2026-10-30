package com.vxplayer.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CrashReportDialog(report: String, title: String = "Last crash report", dismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) },
        text = { Text(report, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("VXPlayer crash report",report)) }) { Text("Copy report") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Dismiss") } })
}

@Composable
fun StartupCrashNotice() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<Pair<String,String>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        report = withContext(Dispatchers.IO) {
            val latest = LocalCrashReports.latest(context)
            latest?.takeUnless { context.getSharedPreferences("crash-report",0).all["ack"] == it.first }
        }
    }
    report?.let { item -> CrashReportDialog(item.second,"VXPlayer closed unexpectedly last time") {
        report = null
        scope.launch(Dispatchers.IO) { context.getSharedPreferences("crash-report",0).edit().putString("ack",item.first).apply() }
    } }
}

@Composable
fun LastCrashButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf<String?>(null) }
    TextButton(onClick = { scope.launch { text = withContext(Dispatchers.IO) { LocalCrashReports.latest(context)?.second ?: "No crash report saved." } } }) { Text("Last crash report") }
    text?.let { CrashReportDialog(it) { text = null } }
}
