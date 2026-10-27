package com.vxplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.util.Rational
import android.provider.MediaStore
import android.provider.Settings
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vxplayer.library.Video
import com.vxplayer.library.VideoLibraryViewModel
import com.vxplayer.player.PlayerScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    var pipMode by mutableStateOf(false)
        private set
    var canEnterPip: () -> Boolean = { false }
    var releaseHiddenPlayer: () -> Unit = {}

    fun updatePip() {
        if (Build.VERSION.SDK_INT >= 31 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            setPictureInPictureParams(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
                .setAutoEnterEnabled(canEnterPip()).build())
        }
    }

    fun enterPlayerPip(manual: Boolean = false): Boolean {
        if (Build.VERSION.SDK_INT < 26 || (!manual && !canEnterPip()) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        return try { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
        catch (_: IllegalStateException) { false }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31) enterPlayerPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // Visible PiP is STARTED. STOPPED means even the PiP surface is no longer visible.
        releaseHiddenPlayer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val navigation = rememberNavController()
                NavHost(navigation, startDestination = "library") {
                    composable("library") {
                        LibraryScreen(onVideoClick = { video, folder ->
                            navigation.navigate("player?uri=${Uri.encode(video.uri.toString())}&title=${Uri.encode(video.title)}&folder=${Uri.encode(folder)}") {
                                launchSingleTop = true
                            }
                        })
                    }
                    composable(
                        "player?uri={uri}&title={title}&folder={folder}",
                        arguments = listOf(
                            navArgument("uri") { type = NavType.StringType },
                            navArgument("title") { type = NavType.StringType },
                            navArgument("folder") { type = NavType.StringType; defaultValue = "" },
                        ),
                    ) { entry ->
                        PlayerScreen(
                            uri = Uri.parse(requireNotNull(entry.arguments?.getString("uri"))),
                            title = entry.arguments?.getString("title").orEmpty(),
                            folder = entry.arguments?.getString("folder").orEmpty(),
                            onBack = { navigation.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

private fun videoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
private fun LibraryScreen(onVideoClick: (Video, String) -> Unit, model: VideoLibraryViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    fun access() = videoPermissions().any(::granted)
    fun partial() = Build.VERSION.SDK_INT >= 34 && !granted(Manifest.permission.READ_MEDIA_VIDEO) && access()
    var hasAccess by remember { mutableStateOf(access()) }
    var limited by remember { mutableStateOf(partial()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    val state by model.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasAccess = access()
        limited = partial()
        model.refresh(hasAccess)
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = access()
                limited = partial()
                model.refresh(hasAccess)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (!hasAccess && !requested) {
            requested = true
            launcher.launch(videoPermissions())
        } else model.refresh(hasAccess)
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 20.dp))
            if (!hasAccess) {
                Text(stringResource(R.string.permission_explanation))
                Button(onClick = { requested = true; launcher.launch(videoPermissions()) }) {
                    Text(stringResource(R.string.allow_videos))
                }
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text(stringResource(R.string.settings)) }
            } else {
                Row {
                    TextButton(onClick = { model.refresh(access()) }) { Text(stringResource(R.string.refresh)) }
                    if (limited) TextButton(onClick = { launcher.launch(videoPermissions()) }) {
                        Text(stringResource(R.string.manage_access))
                    }
                }
                if (limited) Text(stringResource(R.string.partial_access))
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.failed -> Text(stringResource(R.string.scan_failed))
                    state.folders.isEmpty() -> Text(stringResource(R.string.empty_library))
                    else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.recentlyPlayed.isNotEmpty()) {
                            item(key = "recent-heading") { Text(stringResource(R.string.recently_played), style = MaterialTheme.typography.titleMedium) }
                            items(state.recentlyPlayed, key = { "recent:${it.uri}" }) { video ->
                                VideoRow(video, onClick = {
                                    val folder = state.folders.firstOrNull { group -> group.videos.any { it.uri == video.uri } }
                                    onVideoClick(video, folder?.key.orEmpty())
                                })
                            }
                        }
                        state.folders.forEach { folder ->
                            item(key = "folder:${folder.key}") {
                                Text(stringResource(R.string.folder_count, folder.name, folder.videos.size),
                                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                            }
                            items(folder.videos, key = { it.uri.toString() }) { video -> VideoRow(video, onClick = { onVideoClick(video, folder.key) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoRow(video: Video, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, video.uri) {
        value = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(video.uri, Size(240, 144), null)
                else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver,
                        android.content.ContentUris.parseId(video.uri), MediaStore.Video.Thumbnails.MINI_KIND, null)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(112.dp, 72.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: Text(stringResource(R.string.thumbnail_placeholder), style = MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.weight(1f)) {
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.video_metadata, formatDuration(video.durationMs), android.text.format.Formatter.formatShortFileSize(context, video.sizeBytes)),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return if (seconds >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}
