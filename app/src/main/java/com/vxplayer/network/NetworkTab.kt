package com.vxplayer.network

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vxplayer.library.StreamUrls
import com.vxplayer.library.formatDuration
import com.vxplayer.ui.VXIconButton
import com.vxplayer.web.WebPageRoutes
import com.vxplayer.web.WebPlayerActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkTab(onPlay: (NetworkLink) -> Unit, model: NetworkViewModel = viewModel(), onOpened: () -> Unit = {}) {
    val context = LocalContext.current
    val links by model.links.collectAsStateWithLifecycle()
    val recent = recentNetworkLinks(links)
    val favourites = links.filter { it.favourite }.sortedBy { it.title.lowercase() }
    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("Auto") }
    var subtitle by rememberSaveable { mutableStateOf<String?>(null) }
    var subtitleName by rememberSaveable { mutableStateOf<String?>(null) }
    var attachmentChanged by rememberSaveable { mutableStateOf(false) }
    var copiedLink by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var clear by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NetworkLink?>(null) }
    var favouriteName by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val validation = if (url.isBlank()) null else runCatching { StreamUrls.normalize(url) }.exceptionOrNull()?.message
    fun clipboardText(): String? {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
    }
    LaunchedEffect(Unit) {
        copiedLink = runCatching { clipboardText()?.let(StreamUrls::normalize) }.getOrNull()
        model.refreshPositions()
    }
    fun play(link: NetworkLink, replaceAttachment: Boolean = true) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                val normalized=link.copy(url=StreamUrls.normalize(link.url))
                val probe=if(WebPageRoutes.needsProbe(normalized.url,normalized.streamType)) com.vxplayer.library.StreamPageProbe.inspect(normalized.url) else null
                if (WebPageRoutes.useWeb(normalized.url, normalized.streamType) || (probe!=null && !probe.media)) {
                    // Let WebView follow the original page's redirects/cookies itself.
                    val selected = model.repository.played(normalized.copy(streamType = "Web", subtitleUri = null, subtitleName = null), true)
                    WebPlayerActivity.open(context, selected.url)
                } else onPlay(model.repository.played(normalized.copy(url=probe?.url ?: normalized.url), replaceAttachment))
                onOpened()
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Could not open this link. Try again." }
            finally { busy = false }
        }
    }
    fun edit(link: NetworkLink) { editing = link; favouriteName = link.name.ifBlank { link.title } }
    fun remove(link: NetworkLink, favourite: Boolean) {
        scope.launch {
            try {
                if (favourite) model.repository.unfavourite(link.url) else model.repository.removeRecent(link.url)
                if (snackbar.showSnackbar("Link removed",actionLabel = "Undo",duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) model.repository.restore(link)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Could not update saved links. Try again." }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        if (selected != null) {
            try {
                val label = context.contentResolver.query(selected, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: selected.lastPathSegment.orEmpty()
                require(label.endsWith(".srt", true)) { "Choose an SRT subtitle file." }
                runCatching { context.contentResolver.takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                subtitle = selected.toString(); subtitleName = label; attachmentChanged = true
            } catch (failure: Exception) { error = failure.message ?: "Could not attach this subtitle." }
        }
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Open a video link", style = MaterialTheme.typography.titleLarge)
                    }
                    OutlinedTextField(url, onValueChange = { url = it; error = null }, label = { Text("Paste a video link") },
                        placeholder = { Text("https://example.com/video.mp4") }, leadingIcon = { Icon(Icons.Default.Link,null) },
                        trailingIcon = { Row {
                            IconButton(onClick = { url = runCatching { clipboardText() }.getOrNull().orEmpty(); error = null }, enabled = !busy) { Icon(Icons.Default.ContentPaste,"Paste") }
                            IconButton(onClick = { url = ""; error = null }, enabled = !busy && url.isNotEmpty()) { Icon(Icons.Default.Clear,"Clear") }
                        } }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (!busy && url.isNotBlank() && validation == null) play(NetworkLink(StreamUrls.normalize(url),name.trim(),streamType = type,subtitleUri = subtitle,subtitleName = subtitleName),attachmentChanged) }),
                        modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy, isError = validation != null,
                        supportingText = { Text(validation ?: if (WebPageRoutes.useWeb(url, type))
                            "Video page detected. Play opens the web player with Shield ON." else
                            "Play direct media, or choose Web page for a website video.") })
                    Row {
                        TextButton(enabled = !busy, onClick = { url = runCatching { clipboardText() }.getOrNull().orEmpty(); error = null }) {
                            Icon(Icons.Default.ContentPaste, null); Spacer(Modifier.width(6.dp)); Text("Paste")
                        }
                        TextButton(enabled = !busy, onClick = { url = ""; name = ""; subtitle = null; subtitleName = null; attachmentChanged = false; type = "Auto"; error = null }) { Text("Clear") }
                    }
                    copiedLink?.takeIf { it != url }?.let { copied ->
                        SuggestionChip(onClick = { url = copied; copiedLink = null; error = null }, label = { Text("Paste copied link") }, enabled = !busy)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Auto" to "Auto · MP4, MKV, WEBM", "HLS" to "HLS · .m3u8", "DASH" to "DASH · .mpd", "Web" to "Web page").forEach { (value, label) ->
                            FilterChip(selected = type == value, onClick = { type = value }, enabled = !busy, label = { Text(label) })
                        }
                    }
                    OutlinedTextField(name, onValueChange = { name = it.take(120) }, modifier = Modifier.fillMaxWidth(),
                        enabled = !busy, singleLine = true, label = { Text("Display name (optional)") })
                    TextButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy && !WebPageRoutes.useWeb(url, type)) {
                        Icon(Icons.Default.Subtitles, null); Spacer(Modifier.width(6.dp)); Text(subtitleName ?: "Attach SRT (optional)")
                    }
                    if (subtitle != null) TextButton(onClick = { subtitle = null; subtitleName = null; attachmentChanged = true }) { Text("Remove SRT") }
                    Text("HTTP is unencrypted. Links and access tokens are stored locally.", style = MaterialTheme.typography.bodySmall)
                    if (WebPageRoutes.useWeb(url, type)) Text("Tap the video's play icon after the page opens. Web pages use their own controls and subtitles.", style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (error != null) TextButton(onClick = { play(NetworkLink(StreamUrls.normalize(url),name.trim(),streamType = type,subtitleUri = subtitle,subtitleName = subtitleName),attachmentChanged) }, enabled = !busy && url.isNotBlank() && validation == null) { Text("Retry") }
                    Button(onClick = { play(NetworkLink(StreamUrls.normalize(url), name.trim(), streamType = type,
                        subtitleUri = subtitle, subtitleName = subtitleName), attachmentChanged) },
                        enabled = !busy && url.isNotBlank() && validation == null, modifier = Modifier.fillMaxWidth()) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth = 2.dp) else Icon(Icons.Default.PlayArrow, null)
                        Text(if (busy) "Opening…" else if (WebPageRoutes.useWeb(url, type)) "Play in web player" else "Play")
                    }
                    OutlinedButton(onClick = { play(NetworkLink(StreamUrls.normalize(url), name.trim(), streamType = "Web")) },
                        enabled = !busy && url.isNotBlank() && validation == null, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Language, null); Spacer(Modifier.width(8.dp)); Text("Open web player · Shield ON")
                    }
                    Text("Web Shield blocks known ad/tracker requests and popups. Some websites may not work with blocking enabled.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { edit(NetworkLink(StreamUrls.normalize(url), name.trim(), streamType = type,
                        subtitleUri = subtitle, subtitleName = subtitleName)) }, enabled = !busy && url.isNotBlank() && validation == null) {
                        Icon(Icons.Outlined.StarBorder, null); Spacer(Modifier.width(6.dp)); Text("Save favourite")
                    }
                }
            }
        }
        model.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (favourites.isNotEmpty()) item { Text("Favourites", style = MaterialTheme.typography.titleMedium) }
        items(favourites, key = { "favourite:${it.url}" }) { link ->
            NetworkLinkCard(link, model.positions[link.url], busy, { play(link) }, { edit(link) }, { remove(link,true) }, "Remove favourite", { remove(link,true) })
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Recent links", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { clear = true }, enabled = recent.isNotEmpty() && !busy) { Text("Clear all") }
            }
        }
        items(recent, key = { "recent:${it.url}" }) { link ->
            NetworkLinkCard(link, model.positions[link.url], busy, { play(link) }, { edit(link) }, { remove(link,false) }, "Remove recent link", { if (link.favourite) remove(link,true) else edit(link) })
        }
        if (recent.isEmpty() && favourites.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.OndemandVideo, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Your streams, in one place", style = MaterialTheme.typography.titleMedium)
                Text("Paste a direct video or playlist link. Recent links and favourites will appear here.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).imePadding())
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear recent links?") },
        text = { Text("Favourites and saved resume positions are kept.") },
        confirmButton = { TextButton(onClick = { clear = false; model.action { clearRecent() } }) { Text("Clear all") } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
    editing?.let { link ->
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Favourite link") },
            text = { OutlinedTextField(favouriteName, onValueChange = { favouriteName = it.take(120) }, label = { Text("Name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { val label = favouriteName; model.action { favourite(link, label) }; editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
    }
}

@Composable
private fun NetworkLinkCard(link: NetworkLink, position: Long?, busy: Boolean, play: () -> Unit, edit: () -> Unit, remove: () -> Unit, removeLabel: String, star: () -> Unit) {
    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value -> if (value != SwipeToDismissBoxValue.Settled) { remove(); true } else false })
    SwipeToDismissBox(state = dismiss, enableDismissFromStartToEnd = !busy, enableDismissFromEndToStart = !busy,
        backgroundContent = { Box(Modifier.fillMaxSize().padding(20.dp),contentAlignment = Alignment.CenterEnd) { Icon(Icons.Default.DeleteOutline,removeLabel) } }) {
    Card(Modifier.fillMaxWidth().combinedClickable(enabled = !busy, onClick = play, onLongClick = edit)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(link.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(Uri.parse(link.url).host.orEmpty(), style = MaterialTheme.typography.labelSmall)
                }
                VXIconButton(if (link.favourite) Icons.Default.Star else Icons.Outlined.StarBorder, "Toggle favourite (long press row to rename)", star, !busy)
                VXIconButton(Icons.Default.DeleteOutline, removeLabel, remove, !busy)
            }
            Text(link.url, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (link.streamType == "Web") Text("Web player · tap the page's play button", style = MaterialTheme.typography.labelMedium)
            else if (position != null && position > 0) Text("Resume · ${formatDuration(position)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            link.subtitleName?.let { Text("SRT · $it", style = MaterialTheme.typography.labelSmall) }
        }
    }
    }
}
