package com.vxplayer.player

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTrackNameProvider
import com.vxplayer.R

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TrackMenu(model: PlayerViewModel) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val names = remember(context) { DefaultTrackNameProvider(context.resources) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        model.choosingSubtitle = false
        if (uri != null) {
            try {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: uri.lastPathSegment.orEmpty()
                if (!name.endsWith(".srt", ignoreCase = true)) error = true
                else {
                    try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    catch (_: SecurityException) { /* The current document grant still allows playback. */ }
                    model.loadSubtitle(uri, name)
                }
            } catch (_: Exception) { error = true }
        }
    }
    Box {
        TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.tracks), color = Color.White) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 400.dp)) {
            listOf(C.TRACK_TYPE_AUDIO to R.string.audio_tracks, C.TRACK_TYPE_TEXT to R.string.subtitle_tracks).forEach { (type, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {}, enabled = false)
                if (type == C.TRACK_TYPE_AUDIO) DropdownMenuItem(text = { Text(stringResource(R.string.automatic_track)) },
                    onClick = { model.setTrackType(type, true); expanded = false })
                else DropdownMenuItem(text = { Text(stringResource(R.string.subtitles_off)) },
                    onClick = { model.setTrackType(type, false); expanded = false })
                val groups = model.tracks.groups.filter { it.type == type }
                if (groups.isEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.no_tracks)) }, onClick = {}, enabled = false)
                groups.forEach { group ->
                    repeat(group.length) { index ->
                        DropdownMenuItem(
                            text = { Text(names.getTrackName(group.getTrackFormat(index))) },
                            modifier = Modifier.semantics { selected = group.isTrackSelected(index) },
                            enabled = group.isTrackSupported(index),
                            onClick = { model.selectTrack(group, index); expanded = false },
                        )
                    }
                }
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.load_subtitle)) },
                onClick = { expanded = false; model.choosingSubtitle = true; picker.launch(arrayOf("*/*")) })
        }
    }
    if (error) AlertDialog(onDismissRequest = { error = false }, text = { Text(stringResource(R.string.subtitle_error)) },
        confirmButton = { TextButton(onClick = { error = false }) { Text(stringResource(R.string.ok)) } })
}
