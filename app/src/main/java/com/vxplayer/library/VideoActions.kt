package com.vxplayer.library

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import com.vxplayer.ui.VXIconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@Composable
fun VideoActions(video: Video, onPlay: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable(video.uri.toString()) { mutableStateOf(video.title) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val executor = remember { arrayOfNulls<(String, Boolean) -> Unit>(1) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val action = pending
        pending = null
        if (result.resultCode == Activity.RESULT_OK && action != null) {
            if (action == "delete" && Build.VERSION.SDK_INT >= 30) onChanged()
            else executor[0]?.invoke(action, true)
        }
    }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        val action = pending; pending = null
        if (allowed && action != null) executor[0]?.invoke(action, true) else error = "Storage access was not granted."
    }
    run {
        executor[0] = { action, granted ->
            scope.launch {
                try {
                    if (Build.VERSION.SDK_INT <= 28 && !granted && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        pending = action; storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else if (Build.VERSION.SDK_INT >= 30 && !granted) {
                        pending = action
                        val request = if (action == "delete") MediaStore.createDeleteRequest(context.contentResolver, listOf(video.uri))
                            else MediaStore.createWriteRequest(context.contentResolver, listOf(video.uri))
                        consent.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    } else {
                        withContext(Dispatchers.IO) {
                            val count = if (action == "delete") context.contentResolver.delete(video.uri, null, null)
                                else context.contentResolver.update(video.uri, ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, name.trim()) }, null, null)
                            if (count == 0) error("The video is no longer available.")
                        }
                        onChanged()
                    }
                } catch (failure: SecurityException) {
                    if (Build.VERSION.SDK_INT == 29 && failure is RecoverableSecurityException && !granted) {
                        pending = action
                        consent.launch(IntentSenderRequest.Builder(failure.userAction.actionIntent.intentSender).build())
                    } else error = "The video could not be changed. Access was denied."
                } catch (_: Exception) { error = "The video could not be changed. Try refreshing the library." }
            }
        }
    }
    Column {
        VXIconButton(Icons.Default.MoreVert, "Video actions", { expanded = true })
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            listOf("Play", "Info", "Share", "Rename", "Delete").forEach { action ->
                DropdownMenuItem(text = { Text(action) }, onClick = {
                    expanded = false
                    when (action) {
                        "Play" -> onPlay()
                        "Share" -> try {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "video/*"; putExtra(Intent.EXTRA_STREAM, video.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                clipData = android.content.ClipData.newRawUri(video.title, video.uri)
                            }, "Share video"))
                        } catch (_: Exception) { error = "No app is available to share this video." }
                        else -> dialog = action
                    }
                })
            }
        }
    }
    when (dialog) {
        "Info" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(video.title) }, text = {
            Text("Path: ${video.path}\nSize: ${video.sizeBytes} bytes\nResolution: ${video.width} × ${video.height}\nDuration: ${formatDuration(video.durationMs)}\nDate: ${DateFormat.getDateTimeInstance().format(Date(video.dateAdded * 1000))}")
        }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } })
        "Rename" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Rename video") }, text = {
            OutlinedTextField(name, onValueChange = { name = it }, singleLine = true, label = { Text("File name, including extension") })
        }, confirmButton = { TextButton(onClick = {
            if (name.isBlank() || name.length > 255 || name.any { it == '/' || it == '\\' } || name == "." || name == "..") error = "Enter a valid file name."
            else { dialog = null; executor[0]?.invoke("rename", false) }
        }) { Text("Rename") } }, dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } })
        "Delete" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Delete video?") }, text = { Text(video.title) },
            confirmButton = { TextButton(onClick = { dialog = null; executor[0]?.invoke("delete", false) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } })
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}
