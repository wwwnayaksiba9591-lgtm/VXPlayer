package com.vxplayer.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vxplayer.player.PlaybackPositionStore
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.vxplayer.ui.VXIconButton

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppSettings.get(context) }
    val settings by store.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { VXIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack); Text("Settings", style = MaterialTheme.typography.titleLarge) }
            SettingChoice("Default speed", "${settings.speed}x", listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).map { "${it}x" }) { label -> store.update { it.copy(speed = label.removeSuffix("x").toFloatOrNull() ?: 1f) } }
            SettingChoice("Folder playback", settings.playbackMode.label, com.vxplayer.player.PlaybackMode.entries.map { it.label }) { label ->
                store.update { it.copy(playbackMode = com.vxplayer.player.PlaybackMode.entries.firstOrNull { mode -> mode.label == label } ?: com.vxplayer.player.PlaybackMode.Off) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-play next", modifier = Modifier.weight(1f))
                Switch(settings.autoNext, onCheckedChange = { checked -> store.update { it.copy(autoNext = checked) } })
            }
            val aspectPrefs=remember {context.getSharedPreferences("vxplayer",0)}
            var aspectMode by remember {mutableStateOf(com.vxplayer.player.AspectMode.parse(aspectPrefs.all["playerAspect"],settings.aspect))}
            SettingChoice("Default aspect ratio",aspectMode.label,com.vxplayer.player.AspectMode.entries.map {it.label}) { label ->
                aspectMode=com.vxplayer.player.AspectMode.entries.first {it.label==label}
                aspectPrefs.edit().putString("playerAspect",aspectMode.name).apply()
                store.update {it.copy(aspect=when(aspectMode){com.vxplayer.player.AspectMode.Fill->1;com.vxplayer.player.AspectMode.Zoom->2;com.vxplayer.player.AspectMode.Original->3;else->0})}
            }
            SettingChoice("Accent color", settings.accent, listOf("Orange","Blue","Teal","Green","Purple")) { label -> store.update { it.copy(accent = label) } }
            SettingChoice("Default orientation", settings.orientation, com.vxplayer.player.RotationMode.entries.map {it.label}) { label ->
                store.update {it.copy(orientation=label)}
                val mode=com.vxplayer.player.RotationMode.parse(label)
                context.getSharedPreferences("vxplayer",0).edit().putString("rotationMode",mode.name).putBoolean("rotationLocked",false).putInt("rotationRequested",mode.orientation).apply()
            }
            SettingChoice("Theme", settings.theme, listOf("Dark", "AMOLED", "Light", "System")) { label -> store.update { it.copy(theme = label) } }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Touch feedback",Modifier.weight(1f))
                Switch(settings.haptics,{ checked -> store.update {it.copy(haptics=checked)} })
            }
            val webPrefs=remember {context.getSharedPreferences("vxplayer",0)}
            var blockWeb by remember {mutableStateOf(webPrefs.all["webShield"] as? Boolean ?: true)}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Block ads and popups",Modifier.weight(1f))
                Switch(blockWeb,{blockWeb=it;webPrefs.edit().putBoolean("webShield",it).apply()})
            }
            if (android.os.Build.VERSION.SDK_INT >= 31) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Dynamic color (Material You)", modifier = Modifier.weight(1f))
                Switch(settings.dynamicColor, onCheckedChange = { checked -> store.update { it.copy(dynamicColor = checked) } })
            }
            HorizontalDivider()
            Text("Gestures", style = MaterialTheme.typography.titleMedium)
            SettingChoice("Seek swipe sensitivity", settings.seekSensitivity.label,
                com.vxplayer.player.SeekSensitivity.entries.map { it.label }) { label ->
                store.update { it.copy(seekSensitivity = com.vxplayer.player.SeekSensitivity.entries.firstOrNull { level -> level.label == label } ?: com.vxplayer.player.SeekSensitivity.Medium) }
            }
            Text("Full-width swipe: Low 60 s · Medium 120 s · High 240 s", style = MaterialTheme.typography.bodySmall)
            Text("Video", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Remember enhance settings", modifier = Modifier.weight(1f))
                Switch(settings.rememberEnhance, onCheckedChange = { checked -> store.update { it.copy(rememberEnhance = checked) } })
            }
            Text("Audio", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Allow volume boost above 100%", modifier = Modifier.weight(1f))
                Switch(settings.allowBoost, onCheckedChange = { checked -> store.update { it.copy(allowBoost = checked) } })
            }
            SettingChoice("Max boost", "${settings.maxBoost}%", listOf("150%", "200%")) { label -> store.update { it.copy(maxBoost = label.removeSuffix("%").toIntOrNull() ?: 200) } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Keep boost between videos", modifier = Modifier.weight(1f))
                Switch(settings.rememberBoost, onCheckedChange = { checked -> store.update { it.copy(rememberBoost = checked) } })
            }
            HorizontalDivider()
            BackupControls()
            HorizontalDivider()
            OutlinedButton(onClick = { confirm = true }) { Text("Clear history and resume positions") }
            Text("Track preferences and playback defaults are kept.", style = MaterialTheme.typography.bodySmall)
            result?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            AboutCard()
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Clear playback history?") }, text = { Text("This removes recently played, watched markers, and saved resume positions.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            scope.launch { result = try { PlaybackPositionStore.get(context).clearHistory(); "Playback history cleared." } catch (_: Exception) { "Could not clear history. Try again." } }
        }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable
private fun SettingChoice(title: String, value: String, choices: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(value) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { onSelect(choice); expanded = false }) }
            }
        }
    }
}
