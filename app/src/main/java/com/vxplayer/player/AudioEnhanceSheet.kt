package com.vxplayer.player

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
import com.vxplayer.settings.AppSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEnhanceSheet(model: PlayerViewModel, onDismiss: () -> Unit) {
    val engine = model.audioEnhancer
    val settings by AppSettings.get(LocalContext.current).state.collectAsStateWithLifecycle()
    val max = if (settings.allowBoost && engine.boostAvailable) settings.maxBoost else 100
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Audio enhance", style = MaterialTheme.typography.titleLarge)
            Text("Volume ${engine.percent}%", style = MaterialTheme.typography.titleMedium,
                color = if (engine.percent > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Slider(value = engine.percent.coerceAtMost(max).toFloat(), onValueChange = { engine.request(it.roundToInt()) }, valueRange = 0f..max.toFloat(), enabled = model.player != null)
            Text("0% — 100% system volume${if (max > 100) " — ${max}% boost" else ""}", style = MaterialTheme.typography.bodySmall)
            if (!engine.boostAvailable) Text("Volume boost is unavailable on this audio output.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Night mode")
                    Text(if (engine.nightAvailable) "Compress loud peaks for quieter listening" else "Not supported on this device or output", style = MaterialTheme.typography.bodySmall)
                }
                Switch(engine.night && engine.nightAvailable, onCheckedChange = { engine.updateNight(it) }, enabled = engine.nightAvailable)
            }
            Text("Bass boost ${if (engine.bassAvailable) "${engine.bassStrength / 10}%" else "· unavailable"}")
            Slider(engine.bassStrength.toFloat(), onValueChange = { engine.updateBass(it.roundToInt()) }, valueRange = 0f..1000f, enabled = engine.bassAvailable)
            engine.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = { engine.request(100); engine.updateBass(0); engine.updateNight(false) }) { Text("Reset audio") }
            Text("Boost can distort sound. Actual loudness depends on the device and recording.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
