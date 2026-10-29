package com.vxplayer.library

import android.net.Uri
import android.content.Intent
import com.vxplayer.player.WebVideoActivity
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
    fun play(webpage: Boolean = false) {
        val url = try { StreamUrls.validate(value) } catch (e: IllegalArgumentException) { error = e.message; return }
        val updated = StreamUrls.recent(recent, url)
        val editor = prefs.edit().putString("recentStreamUrls", JSONArray(updated).toString())
        recent.filter { it !in updated }.forEach { editor.remove("streamType:$it") }
        editor.apply(); recent = updated
        val host = Uri.parse(url).host.orEmpty()
        if (webpage || host.equals("morencius.com", true) || host.equals("www.morencius.com", true)) {
            context.startActivity(Intent(context, WebVideoActivity::class.java).setData(Uri.parse(url)))
            onDismiss()
        } else onPlay(url, StreamUrls.title(url), type)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Open URL", style = MaterialTheme.typography.titleLarge) }
            item { OutlinedTextField(value, onValueChange = { value = it; error = null }, label = { Text("HTTP / HTTPS video link") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = error != null) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Auto", "HLS", "DASH").forEach { option -> FilterChip(type == option, onClick = { type = option }, label = { Text(option) }) }
                }
                Text("Choose HLS or DASH for links without a playlist extension. DRM, login pages, and custom authentication headers are not supported.", style = MaterialTheme.typography.bodySmall)
            }
            item { Text("HTTP is unencrypted. Recent links are stored on this device and may include access tokens. Clear them when finished.", style = MaterialTheme.typography.bodySmall) }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item { Button(onClick = { play() }, enabled = value.isNotBlank()) { Text("Play") } }
            item {
                TextButton(onClick = { play(webpage = true) }, enabled = value.isNotBlank()) { Text("Watch webpage") }
                Text("For video hosting pages such as Morencius. Opens the website player inside VXPlayer; website controls and availability apply.", style = MaterialTheme.typography.bodySmall)
            }
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
