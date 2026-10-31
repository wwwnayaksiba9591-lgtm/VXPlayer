package com.vxplayer.player

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subtitles
import com.vxplayer.ui.VXIconButton
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenu(model: PlayerViewModel) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(false) }
    var browserSearch by remember { mutableStateOf(false) }
    var searchTitle by remember { mutableStateOf("") }
    var browserError by remember { mutableStateOf<String?>(null) }
    val names = remember(context) { DefaultTrackNameProvider(context.resources) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) model.choosingSubtitle = false
        else scope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: uri.lastPathSegment.orEmpty()
                }
                if (!name.endsWith(".srt", ignoreCase = true)) error = true
                else {
                    withContext(Dispatchers.IO) {
                        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        catch (_: SecurityException) { /* The current document grant still allows playback. */ }
                    }
                    model.loadSubtitle(uri, name)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { model.choosingSubtitle = false }
        }
    }
    Box {
        VXIconButton(Icons.Default.Subtitles, "Audio and subtitles", { expanded = true })
        if (expanded) ModalBottomSheet(onDismissRequest = { expanded = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text("Audio & subtitles", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
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
                            text = { Text(names.getTrackName(group.getTrackFormat(index)) + if (!group.isTrackSupported(index, false)) " · Unsupported" else "") },
                            modifier = Modifier.semantics { selected = group.isTrackSelected(index) },
                            enabled = group.isTrackSupported(index, false),
                            onClick = { model.selectTrack(group, index); expanded = false },
                        )
                    }
                }
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.load_subtitle)) },
                onClick = {
                    expanded = false; model.choosingSubtitle = true
                    try { picker.launch(arrayOf("*/*")) }
                    catch (_: Exception) { model.choosingSubtitle = false; error = true }
                })
            HorizontalDivider()
            DropdownMenuItem(text={Text("Search subtitles in browser (no API key)")},enabled=!model.isPrivatePlayback,
                onClick={expanded=false;searchTitle=subtitleSearchTitle(model.activeTitle);browserError=null;browserSearch=true})
            DropdownMenuItem(text={Text("Online English subtitles (API key)")},enabled=!model.isPrivatePlayback,
                onClick={expanded=false;online=true})
            if(model.isPrivatePlayback) Text("Online subtitle search is disabled for Private videos.",modifier=Modifier.padding(horizontal=16.dp))
            if (model.hasExternalSrt) {
                var encodings by remember { mutableStateOf(false) }
                TextButton(onClick = { encodings = !encodings }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("SRT encoding: ${model.subtitleEncoding}") }
                if (encodings) Column(Modifier.padding(horizontal = 16.dp)) {
                    SubtitleText.encodings.forEach { encoding -> TextButton(onClick = { model.changeSubtitleEncoding(encoding); encodings = false }) { Text(encoding) } }
                }
                Text("Auto detection is best-effort. Choose the encoding if legacy captions look wrong.", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            }
            Text("Subtitle delay", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            if (model.canDelayExternalSubtitle) {
            Text("${if (model.subtitleDelayMs >= 0) "+" else ""}${model.subtitleDelayMs} ms", modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { model.changeSubtitleDelay(-100) }) { Text("−100 ms") }
                TextButton(onClick = { model.changeSubtitleDelay(0, reset = true) }) { Text("Reset") }
                TextButton(onClick = { model.changeSubtitleDelay(100) }) { Text("+100 ms") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { model.changeSubtitleDelay(-1000) }) { Text("−1 s") }
                TextButton(onClick = { model.changeSubtitleDelay(1000) }) { Text("+1 s") }
            }
            }
            Text("External SRT only. Positive delay shows captions later. Embedded subtitles cannot be shifted.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
        }
    }
    if (error) AlertDialog(onDismissRequest = { error = false }, text = { Text(stringResource(R.string.subtitle_error)) },
        confirmButton = { TextButton(onClick = { error = false }) { Text(stringResource(R.string.ok)) } })
    if(online) OnlineSubtitleSheet(model,onDismiss={online=false})
    if(browserSearch) AlertDialog(onDismissRequest={browserSearch=false},title={Text("Find English subtitles")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("No API key needed. Search in your browser, download an SRT file, then return here and choose Load subtitle. Your search text goes to Google and the sites you open.")
            OutlinedTextField(value=searchTitle,onValueChange={searchTitle=it.take(200)},singleLine=true,label={Text("Movie or episode title")})
            browserError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }},confirmButton={TextButton(enabled=searchTitle.trim().length>=2,onClick={
            try {
                val url=android.net.Uri.parse("https://www.google.com/search").buildUpon()
                    .appendQueryParameter("q","${searchTitle.trim()} English subtitles SRT download").build()
                val intent=Intent.createChooser(Intent(Intent.ACTION_VIEW,url).addCategory(Intent.CATEGORY_BROWSABLE),"Search subtitles in browser")
                    .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,arrayOf(android.content.ComponentName(context,com.vxplayer.MainActivity::class.java)))
                model.playback?.pause()
                context.startActivity(intent)
                browserSearch=false
            } catch(_: Exception){browserError="Could not open a browser. Install or enable a browser and try again."}
        }){Text("Search in browser")}},dismissButton={TextButton(onClick={browserSearch=false}){Text("Cancel")}})
}
