package com.vxplayer.player

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSubtitleSheet(model: PlayerViewModel,onDismiss: ()->Unit) {
    val context=LocalContext.current.applicationContext
    val browser=LocalUriHandler.current
    val prefs=remember {context.getSharedPreferences("subtitle-provider",0)}
    var key by remember {mutableStateOf(prefs.all["apiKey"] as? String ?: "")}
    var query by remember {mutableStateOf(subtitleSearchTitle(model.activeTitle))}
    var results by remember {mutableStateOf<List<OnlineSubtitle>>(emptyList())}
    var busy by remember {mutableStateOf(false)}
    var searched by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    val provider=remember {OnlineSubtitles()}
    DisposableEffect(model) {
        model.choosingSubtitle=true
        onDispose {model.choosingSubtitle=false}
    }
    ModalBottomSheet(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=560.dp)) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                Text("Online English subtitles",style=MaterialTheme.typography.titleLarge)
                Text("Search text is sent to OpenSubtitles only when you tap Search. Video files, paths and stream URLs are not sent. Provider limits apply.")
            }
            item {
                OutlinedTextField(value=key,onValueChange={key=it},label={Text("OpenSubtitles API key")},
                    visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
                TextButton(onClick={runCatching {browser.openUri("https://www.opensubtitles.com/en/consumers")}.onFailure {message="Could not open browser."}}){Text("Get an API key")}
                TextButton(enabled=!busy,onClick={key="";prefs.edit().remove("apiKey").apply()}){Text("Forget saved key")}
            }
            item {OutlinedTextField(value=query,onValueChange={query=it.take(200)},label={Text("Movie or episode title")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())}
            item {
                Button(enabled=!busy && key.isNotBlank() && query.trim().length>=2,onClick={
                    busy=true;message=null;results=emptyList()
                    scope.launch {
                        try {
                            results=withContext(Dispatchers.IO) {provider.search(key,query)}
                            prefs.edit().putString("apiKey",key.trim()).apply();searched=true
                        } catch(cancelled: CancellationException){throw cancelled}
                        catch(_: java.io.IOException){message="Network error. Check your connection and tap Search to retry."}
                        catch(failure: Exception){message=failure.message ?: "Subtitle search failed."}
                        finally {busy=false}
                    }
                },modifier=Modifier.fillMaxWidth()){Text("Search English subtitles")}
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                if(searched && results.isEmpty() && !busy && message==null)Text("No English subtitles found. Try a shorter title or include the episode number.")
            }
            items(results,key={it.id}) {subtitle ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(subtitle.title,style=MaterialTheme.typography.titleSmall)
                        Text(subtitle.release,style=MaterialTheme.typography.bodySmall)
                        TextButton(enabled=!busy,onClick={
                            busy=true;message=null
                            scope.launch {
                                var stored: File?=null
                                try {
                                    val file=withContext(Dispatchers.IO) {
                                        val bytes=provider.download(key,subtitle.id)
                                        val directory=File(context.filesDir,"online-subtitles")
                                        check(directory.mkdirs() || directory.isDirectory) {"Subtitle storage is unavailable."}
                                        File(directory,"${UUID.randomUUID()}.srt").also {
                                            stored=it
                                            try {it.writeBytes(bytes)}catch(failure: Exception){it.delete();throw failure}
                                        }
                                    }
                                    model.loadSubtitle(Uri.fromFile(file),"English.srt")
                                    onDismiss()
                                } catch(cancelled: CancellationException){withContext(NonCancellable+Dispatchers.IO){stored?.delete()};throw cancelled}
                                catch(_: java.io.IOException){message="Download failed. Check your connection and retry."}
                                catch(failure: Exception){message=failure.message ?: "Could not download this subtitle."}
                                finally {busy=false}
                            }
                        }){Text("Download and apply")}
                    }
                }
            }
            item {TextButton(onClick=onDismiss){Text("Close")}}
        }
    }
}
