package com.vxplayer.library

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenUrlSheet(onDismiss: () -> Unit, onPlay: (String, String, String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vxplayer", 0) }
    var value by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("Auto") }
    var error by remember { mutableStateOf<String?>(null) }
    var recent by remember { mutableStateOf(runCatching {
        val array = JSONArray(prefs.getString("recentStreamUrls", "[]"))
        (0 until array.length().coerceAtMost(20)).map { StreamUrls.validate(array.getString(it)) }
    }.getOrDefault(emptyList())) }
    fun play() {
        val url = try { StreamUrls.validate(value) } catch (e: IllegalArgumentException) { error = e.message; return }
        val selectedType = type
        error = null
                val updated = StreamUrls.recent(recent, url)
                val editor = prefs.edit().putString("recentStreamUrls", JSONArray(updated).toString())
                    .putString("streamType:$url", selectedType)
                recent.filter { it !in updated }.forEach { editor.remove("streamType:$it") }
                editor.apply(); recent = updated
                onPlay(url, StreamUrls.title(url), selectedType)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Open URL", style = MaterialTheme.typography.titleLarge) }
            item { OutlinedTextField(value, onValueChange = { value = it; error = null }, label = { Text("HTTP / HTTPS direct video link") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = error != null) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Auto", "HLS", "DASH").forEach { option -> FilterChip(type == option, onClick = { type = option }, label = { Text(option) }) }
                }
                Text("Direct MP4, MKV, WEBM, HLS and DASH only. Choose HLS or DASH for extensionless playlists. Web pages are rejected.", style = MaterialTheme.typography.bodySmall)
            }
            item { Text("HTTP is unencrypted. Recent links are stored on this device and may include access tokens. Clear them when finished.", style = MaterialTheme.typography.bodySmall) }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item { Button(onClick = { play() }, enabled = value.isNotBlank()) { Text("Play") } }
            if (recent.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Recent URLs", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        val editor = prefs.edit().remove("recentStreamUrls")
                        recent.forEach { editor.remove("streamType:$it") }
                        editor.apply(); recent = emptyList()
                    }) { Text("Clear") }
                }
            }
            items(recent, key = { it }) { url ->
                TextButton(onClick = { value = url; type = prefs.getString("streamType:$url", "Auto") ?: "Auto"; error = null }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(StreamUrls.title(url), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Uri.parse(url).host.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
