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
            Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("Back") }; Text("Settings", style = MaterialTheme.typography.headlineSmall) }
            SettingChoice("Default speed", "${settings.speed}x", listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).map { "${it}x" }) { label -> store.update { it.copy(speed = label.removeSuffix("x").toFloat()) } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-play next", modifier = Modifier.weight(1f))
                Switch(settings.autoNext, onCheckedChange = { checked -> store.update { it.copy(autoNext = checked) } })
            }
            val aspects = listOf("Fit", "Fill", "Zoom", "100%")
            SettingChoice("Default aspect ratio", aspects[settings.aspect], aspects) { label -> store.update { it.copy(aspect = aspects.indexOf(label)) } }
            SettingChoice("Default orientation", settings.orientation, listOf("Auto", "System", "Portrait", "Landscape")) { label -> store.update { it.copy(orientation = label) } }
            SettingChoice("Theme", settings.theme, listOf("System", "Light", "Dark")) { label -> store.update { it.copy(theme = label) } }
            if (android.os.Build.VERSION.SDK_INT >= 31) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Dynamic color (Material You)", modifier = Modifier.weight(1f))
                Switch(settings.dynamicColor, onCheckedChange = { checked -> store.update { it.copy(dynamicColor = checked) } })
            }
            HorizontalDivider()
            OutlinedButton(onClick = { confirm = true }) { Text("Clear history and resume positions") }
            Text("Track preferences and playback defaults are kept.", style = MaterialTheme.typography.bodySmall)
            result?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
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
