package com.vxplayer.privatefiles

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vxplayer.player.PlayerScreen
import com.vxplayer.player.PlayerViewModel
import com.vxplayer.settings.AppSettings
import com.vxplayer.settings.VXTheme

@Composable
fun PrivateScreen(activity: PrivateActivity, model: PrivateViewModel = viewModel()) {
    val prefs = remember { activity.getSharedPreferences("vxplayer", 0) }
    var explained by remember { mutableStateOf(prefs.getBoolean("privateWarningAccepted", false)) }
    var delete by remember { mutableStateOf<PrivateEntry?>(null) }
    var permissionOperation by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionEntry by rememberSaveable { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        val entry = model.entries.firstOrNull { it.id == permissionEntry }
        if (allowed && entry != null) {
            if (permissionOperation == "restore") model.restore(entry) else model.finishMove(entry)
        }
        permissionEntry = null; permissionOperation = null
    }
    fun operate(entry: PrivateEntry, operation: String) {
        if (Build.VERSION.SDK_INT <= 28 && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionEntry = entry.id; permissionOperation = operation
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else if (operation == "restore") model.restore(entry) else model.finishMove(entry)
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        model.consentResult(it.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(model.consent, model.busy, PrivateAccess.unlocked) {
        if (PrivateAccess.unlocked && !model.busy) model.consent?.let { model.consumeConsent(); consent.launch(it) }
    }
    LaunchedEffect(model.pendingFinishId, model.busy, PrivateAccess.unlocked) {
        if (!model.busy && PrivateAccess.unlocked) {
            model.entries.firstOrNull { it.id == model.pendingFinishId }?.let {
                model.consumePendingFinish(); operate(it, "finish")
            }
        }
    }
    LaunchedEffect(PrivateAccess.unlocked, explained) {
        if (PrivateAccess.unlocked && explained) {
            model.refresh()
            model.beginMove(activity.intent.data, activity.intent.getStringExtra("title") ?: "Video",
                activity.intent.getLongExtra("size", -1))
        }
    }
    if (!explained) {
        AlertDialog(onDismissRequest = { activity.finish() }, title = { Text("About Private videos") },
            text = { Text("Private videos are hidden from the gallery and other apps but are NOT encrypted. They will be DELETED if VXPlayer is uninstalled or its data is cleared. Use Restore to library first. An unfinished move can leave the original visible until you approve its removal.") },
            confirmButton = { TextButton(onClick = { prefs.edit().putBoolean("privateWarningAccepted", true).apply(); explained = true }) { Text("I understand") } },
            dismissButton = { TextButton(onClick = { activity.finish() }) { Text("Cancel") } })
    }
    if (!PrivateAccess.unlocked || !explained) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Private is locked", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = activity::authenticate, enabled = explained) { Text("Unlock") }
                activity.authenticationMessage?.let { Text(it) }
                TextButton(onClick = { activity.finish() }) { Text("Back to library") }
            }
        }
        return
    }
    val selected = model.entries.firstOrNull { it.id == model.selectedId }
    if (selected != null && !model.busy) {
        val playerModel: PlayerViewModel = viewModel(key = "private-player:${selected.id}")
        BackHandler { model.select(null) }
        VXTheme(AppSettings.get(activity).state.value, playerMode = true) {
            PlayerScreen(Uri.fromFile(model.repository.layout.file(selected.id)), selected.title,
                onBack = { model.select(null) }, model = playerModel)
        }
        return
    }
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.safeDrawingPadding().fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Private", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { PrivateAccess.lock() }) { Text("Lock") }
                    TextButton(onClick = { activity.finish() }, enabled = !model.busy) { Text("Library") }
                }
                Text("Hidden, not encrypted. Restore before uninstalling or clearing app data.", style = MaterialTheme.typography.bodySmall)
            }
            if (model.busy) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Transferred ${model.copied / (1024 * 1024)} MiB")
                TextButton(onClick = model::cancel) { Text("Cancel transfer") }
            }
            model.message?.let { message -> item { Text(message); TextButton(onClick = model::clearMessage) { Text("Dismiss") } } }
            if (model.entries.isEmpty() && !model.busy) item { Text("No private videos. Use a library video’s menu → Move to Private.") }
            items(model.entries, key = { it.id }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                        Text("${entry.size / (1024 * 1024)} MiB")
                        if (entry.state != "private") Text("Move unfinished: the original may still be visible in the gallery.", color = MaterialTheme.colorScheme.error)
                        if (entry.state == "deletingOriginal") Text("An Android deletion request was started. Use Finish move to check or retry it before restoring or deleting this copy.")
                        if (entry.restoreUri != null) Text("Restore interrupted. Retry Restore to verify or finish it.")
                        Row {
                            TextButton(onClick = { model.select(entry.id) }, enabled = !model.busy) { Text("Play") }
                            if (entry.state != "private") TextButton(onClick = { operate(entry, "finish") }, enabled = !model.busy) { Text("Finish move") }
                        }
                        Row {
                            TextButton(onClick = { operate(entry, "restore") }, enabled = !model.busy && entry.state != "deletingOriginal") { Text("Restore to library") }
                            TextButton(onClick = { delete = entry }, enabled = !model.busy && entry.state != "deletingOriginal") { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
    delete?.let { entry ->
        AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete private copy?") }, text = { Text("${entry.title}\nThis permanently deletes the copy inside VXPlayer. Any remaining public original is kept.") },
            confirmButton = { TextButton(onClick = { delete = null; model.delete(entry) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } })
    }
}
