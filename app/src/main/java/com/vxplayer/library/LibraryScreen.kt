package com.vxplayer.library

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vxplayer.R
import kotlinx.coroutines.*
import java.util.Locale

private fun permissions() = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onVideoClick: (Video, String) -> Unit, onSettings: () -> Unit = {}, model: VideoLibraryViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun access() = permissions().any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    var allowed by remember { mutableStateOf(access()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    val preferences = remember(context) { context.getSharedPreferences("vxplayer", 0) }
    var query by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }
    var grid by remember { mutableStateOf(preferences.getBoolean("grid", false)) }
    var sort by remember { mutableStateOf(runCatching { LibrarySort.valueOf(preferences.getString("sort", "Name")!!) }.getOrDefault(LibrarySort.Name)) }
    var descending by remember { mutableStateOf(preferences.getBoolean("descending", false)) }
    var sortOpen by remember { mutableStateOf(false) }
    var appMenu by remember { mutableStateOf(false) }
    val folderScroll = rememberLazyListState()
    val videosScroll = rememberLazyListState()
    val recentScroll = rememberLazyListState()
    val detailScroll = rememberSaveable(selectedFolder, saver = LazyListState.Saver) { LazyListState() }
    val state by model.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allowed = access(); model.refresh(allowed)
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { allowed = access(); model.refresh(allowed) } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (!allowed && !requested) { requested = true; picker.launch(permissions()) }
        else model.refresh(allowed)
    }
    BackHandler(selectedFolder != null) { selectedFolder = null; tab = 0 }
    val folder = state.folders.firstOrNull { it.key == selectedFolder }
    val folders = orderLibrary(state.folders.filter { it.name.contains(query, true) || it.videos.any { video -> video.title.contains(query, true) } },
        sort, descending, { it.name }, { it.videos.maxOfOrNull { video -> video.dateAdded } ?: 0L },
        { it.videos.sumOf { video -> video.sizeBytes } }, { it.videos.sumOf { video -> video.durationMs } })
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectedFolder != null) TextButton(onClick = { selectedFolder = null; tab = 0 }) { Text(stringResource(R.string.back_to_library)) }
                Text(folder?.name ?: "VXPlayer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { search = !search; if (!search) query = "" }) { Text("Search") }
                Box {
                    TextButton(onClick = { appMenu = true }) { Text("⋮") }
                    DropdownMenu(appMenu, onDismissRequest = { appMenu = false }) {
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { appMenu = false; onSettings() })
                        DropdownMenuItem(text = { Text("Rescan library") }, onClick = { appMenu = false; model.refresh(access()) })
                    }
                }
            }
            if (search) OutlinedTextField(query, onValueChange = { query = it }, label = { Text("Search videos or folders") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box {
                    TextButton(onClick = { sortOpen = true }) { Text("Sort: $sort") }
                    DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
                        LibrarySort.entries.forEach { value -> DropdownMenuItem(text = { Text(value.name) }, onClick = { sort = value; preferences.edit().putString("sort", value.name).apply(); sortOpen = false }) }
                        DropdownMenuItem(text = { Text(if (descending) "Descending ✓" else "Ascending ✓") }, onClick = { descending = !descending; preferences.edit().putBoolean("descending", descending).apply(); sortOpen = false })
                    }
                }
                TextButton(onClick = { grid = !grid; preferences.edit().putBoolean("grid", grid).apply() }) { Text(if (grid) "List" else "Grid") }
            }
            if (selectedFolder == null) TabRow(selectedTabIndex = tab) {
                listOf("Folders", "Videos", "Recent").forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                }
            }
            PullToRefreshBox(isRefreshing = state.loading, onRefresh = { model.refresh(access()) }, modifier = Modifier.weight(1f)) {
            when {
                !allowed -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                    FolderIcon()
                    Text("Your video library", style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.permission_explanation))
                    Button(onClick = { picker.launch(permissions()) }) { Text(stringResource(R.string.allow_videos)) }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text(stringResource(R.string.settings)) }
                }
                state.loading && state.folders.isEmpty() -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) { CircularProgressIndicator(); Text("Finding videos…") }
                state.failed -> LibraryMessage("Unable to read the library", "Check video access, then refresh.", { model.refresh(access()) })
                else -> {
                    if (Build.VERSION.SDK_INT >= 34 && context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                        TextButton(onClick = { picker.launch(permissions()) }) { Text(stringResource(R.string.manage_access)) }
                    }
                    if (selectedFolder == null && tab == 0) {
                        LazyColumn(state = folderScroll, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (folders.isEmpty()) item { LibraryMessage("No folders found", "Add a video to your device or change the search.") }
                            items(folders, key = { it.key }) { group ->
                                Card(onClick = { selectedFolder = group.key }, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        FolderIcon()
                                        Column(Modifier.weight(1f)) {
                                            Text(group.name, style = MaterialTheme.typography.titleMedium)
                                            Text("${group.videos.size} videos · ${Formatter.formatShortFileSize(context, group.videos.sumOf { it.sizeBytes })}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val baseVideos = if (selectedFolder != null) folder?.videos.orEmpty() else if (tab == 2) state.recentlyPlayed else state.folders.flatMap { it.videos }
                        val filtered = baseVideos.filter { video -> video.title.contains(query, true) || state.folders.any { it.key == video.folderKey && it.name.contains(query, true) } }
                        val videos = if (tab == 2 && selectedFolder == null) filtered else orderLibrary(filtered, sort, descending, { it.title }, { it.dateAdded }, { it.sizeBytes }, { it.durationMs })
                        if (videos.isEmpty()) LibraryMessage("No videos here yet", "Try another folder, allow more videos, or change the search.")
                        else if (grid) LazyVerticalGrid(columns = GridCells.Adaptive(148.dp), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            gridItems(videos, key = { it.uri.toString() }) { video ->
                                Card(onClick = { onVideoClick(video, video.folderKey) }) {
                                    VideoThumbnail(video, Modifier.fillMaxWidth())
                                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text("${Formatter.formatShortFileSize(context, video.sizeBytes)} · ${video.width} × ${video.height}", style = MaterialTheme.typography.labelSmall)
                                            if (video.watched) Text("Watched", style = MaterialTheme.typography.labelSmall)
                                        }
                                        VideoActions(video, { onVideoClick(video, video.folderKey) }, { model.refresh(access()) })
                                    }
                                }
                            }
                        } else
                        LazyColumn(state = if (selectedFolder != null) detailScroll else if (tab == 2) recentScroll else videosScroll,
                            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (videos.isEmpty()) item { Text(stringResource(R.string.empty_library)) }
                            items(videos, key = { it.uri.toString() }) { video -> VideoRow(video, onClick = { onVideoClick(video, video.folderKey) }) {
                                VideoActions(video, { onVideoClick(video, video.folderKey) }, { model.refresh(access()) })
                            } }
                        }
                    }
                }
            }
        }
    }
}

}

@Composable
private fun LibraryMessage(title: String, detail: String, retry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FolderIcon()
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (retry != null) OutlinedButton(onClick = retry) { Text("Refresh") }
    }
}

@Composable
private fun FolderIcon() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(44.dp, 36.dp)) {
        drawPath(Path().apply {
            moveTo(0f, size.height * .1f); lineTo(size.width * .4f, size.height * .1f)
            lineTo(size.width * .5f, size.height * .3f); lineTo(size.width, size.height * .3f)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }, color)
    }
}

private val thumbnails = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}

@Composable
fun VideoThumbnail(video: Video, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cacheKey = "${video.uri}:${video.dateAdded}:${video.sizeBytes}"
    val bitmap by produceState(thumbnails.get(cacheKey), cacheKey) {
        if (value == null) value = withContext(Dispatchers.IO) {
            try {
                val result = if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(video.uri, Size(256, 160), null)
                else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, ContentUris.parseId(video.uri), MediaStore.Video.Thumbnails.MINI_KIND, null)
                }
                result?.also { thumbnails.put(cacheKey, it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.6f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Text(formatDuration(video.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = .75f)).padding(3.dp))
        }
        if (video.positionMs > 0L && video.durationMs > 0L) LinearProgressIndicator(
            progress = { (video.positionMs.toFloat() / video.durationMs).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp))
    }
}

@Composable
fun VideoRow(video: Video, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        VideoThumbnail(video, Modifier.width(112.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text("${Formatter.formatShortFileSize(context, video.sizeBytes)} · ${video.width} × ${video.height}", style = MaterialTheme.typography.bodySmall)
            if (video.watched) Text(stringResource(R.string.watched), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

fun formatDuration(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}
