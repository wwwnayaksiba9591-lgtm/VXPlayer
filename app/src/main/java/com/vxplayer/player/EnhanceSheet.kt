package com.vxplayer.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vxplayer.ui.VXIconButton
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EnhanceSheet(model: PlayerViewModel, onDismiss: () -> Unit) {
    val engine = model.videoEnhancer
    val values = engine.values
    val enabled = engine.enabled && engine.available && !engine.suspended
    var advanced by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Smart Enhance", style = MaterialTheme.typography.titleLarge); Text(engine.preset, style = MaterialTheme.typography.bodySmall) }
                Switch(engine.enabled, onCheckedChange = { model.enableEnhance(it) })
            }
            if (!engine.available && !model.audioEnhancer.smartAvailable) Text("Not supported on this device", color = MaterialTheme.colorScheme.error)
            if (engine.suspended) Text("Enhancement is suspended in PiP and audio-only mode.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ENHANCE_PRESETS.keys.forEach { name -> FilterChip(engine.preset == name, onClick = { model.presetEnhance(name) }, label = { Text(name) }) }
            }
            Text("Modes tune both video and supported audio effects. Auto samples locally; unavailable sampling uses a gentle preset.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Advanced ▴" else "Advanced ▾") }
            if (advanced) {
            EnhanceSlider("Brightness", values.brightness, -.25f.. .25f, enabled, { model.updateEnhance(values.copy(brightness = it)) }, { model.updateEnhance(values.copy(brightness = 0f)) })
            EnhanceSlider("Contrast", values.contrast, -.5f.. .5f, enabled, { model.updateEnhance(values.copy(contrast = it)) }, { model.updateEnhance(values.copy(contrast = 0f)) })
            EnhanceSlider("Saturation", values.saturation, 0f..2f, enabled, { model.updateEnhance(values.copy(saturation = it)) }, { model.updateEnhance(values.copy(saturation = 1f)) })
            EnhanceSlider("Sharpness", values.sharpness, -.5f..1f, enabled, { model.updateEnhance(values.copy(sharpness = it)) }, { model.updateEnhance(values.copy(sharpness = 0f)) })
            EnhanceSlider("Warmth", values.warmth, -.2f.. .2f, enabled, { model.updateEnhance(values.copy(warmth = it)) }, { model.updateEnhance(values.copy(warmth = 0f)) })
            OutlinedButton(onClick = { model.presetEnhance("Original") }) { Text("Reset to Original") }
            Text("Changes video pixels only. Display brightness is still controlled by the left-side swipe.", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
        }
    }
}

@Composable
private fun EnhanceSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, change: (Float) -> Unit, reset: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text("${(value * 100).roundToInt()}", style = MaterialTheme.typography.labelMedium)
            VXIconButton(Icons.Default.RestartAlt, "Reset $label", reset, enabled)
        }
        Slider(value, onValueChange = change, valueRange = range, enabled = enabled)
    }
}
