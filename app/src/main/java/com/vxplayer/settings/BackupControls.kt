package com.vxplayer.settings

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vxplayer.player.PlaybackPosition
import com.vxplayer.player.PlaybackPositionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var pending by mutableStateOf<SettingsBackup?>(null)
        private set
    private val app = application
    private val prefs = app.getSharedPreferences("vxplayer", 0)
    private val positions = PlaybackPositionStore.get(app)
    fun dismiss() { pending = null }
    private fun work(action: suspend () -> String) {
        if (busy) return
        busy = true; message = null
        viewModelScope.launch {
            try { message = action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Could not complete backup/restore. Check the file, free space, and document access. Invalid backups make no changes; storage failures may leave a partial restore." }
            finally { busy = false }
        }
    }
    fun export(uri: Uri) = work {
        withContext(Dispatchers.IO) {
            val settings = prefs.all.filterKeys(BackupCodec::supports).mapValues { requireNotNull(it.value) }
            val data = BackupCodec.encode(SettingsBackup(settings, positions.snapshot().positions))
            requireNotNull(app.contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(data) }
        }
        "Backup saved. Keep this unencrypted JSON file private."
    }
    fun read(uri: Uri) = work {
        val data = withContext(Dispatchers.IO) {
            val bytes = requireNotNull(app.contentResolver.openInputStream(uri)).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= BackupCodec.MAX_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            BackupCodec.decode(bytes.toString(Charsets.UTF_8))
        }
        pending = data
        "Backup validated. Confirm to restore."
    }
    fun restore() {
        val data = pending ?: return
        pending = null
        work {
            withContext(Dispatchers.IO) {
                positions.importPositions(data.positions.map { PlaybackPosition(it.key, it.value) })
                val editor = prefs.edit()
                data.settings.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                    }
                }
                try { check(editor.commit()) } finally { AppSettings.get(app).reload() }
            }
            "Settings and resume positions restored. Videos and access permissions are unchanged."
        }
    }
}

@Composable
fun BackupControls(model: BackupViewModel = viewModel()) {
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::export) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::read) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Backup and restore", style = MaterialTheme.typography.titleMedium)
        Text("Portable playback settings and per-video resume positions only. Plain JSON may contain file paths and stream access tokens. It contains no videos, history, track/file grants, recent URL list, or warning consent.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { export.launch("VXPlayer-backup.json") }, enabled = !model.busy) { Text("Back up to JSON") }
        OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !model.busy) { Text("Restore from JSON") }
        if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        model.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    model.pending?.let { data ->
        AlertDialog(onDismissRequest = model::dismiss, title = { Text("Restore backup?") },
            text = { Text("Merge ${data.settings.size} settings and ${data.positions.size} resume positions. Matching values are overwritten; other saved data stays. Resume requires the same video URI and access on this device. Videos are not copied.") },
            confirmButton = { TextButton(onClick = model::restore) { Text("Restore") } },
            dismissButton = { TextButton(onClick = model::dismiss) { Text("Cancel") } })
    }
}
