package com.vxplayer.player

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.vxplayer.ui.VXIconButton

@Composable
fun SmartEnhanceControl(model: PlayerViewModel, openModes: () -> Unit, touch: () -> Unit) {
    val engine = model.videoEnhancer
    val accent = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).combinedClickable(role = Role.Button,
            onClick = { model.toggleSmartEnhance(); touch() }, onLongClick = { openModes(); touch() })
            .semantics { contentDescription = "Enhance"; stateDescription = if (engine.enabled) "On, ${engine.lastMode}" else "Off" },
            contentAlignment = Alignment.Center) {
            Icon(if (engine.enabled && !model.enhancementUnavailable) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome, null,
                tint = if (engine.enabled && !model.enhancementUnavailable) accent else Color.White)
        }
        VXIconButton(Icons.Default.ExpandMore, "Smart Enhance modes", { openModes(); touch() })
        if (engine.enabled && !model.enhancementUnavailable) {
            DisposableEffect(model) { onDispose { model.compareEnhance(false) } }
            Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = "Hold A/B to compare with original" }
                .pointerInput(model) {
                    detectTapGestures(onPress = {
                        model.compareEnhance(true); touch()
                        try { tryAwaitRelease() } finally { model.compareEnhance(false); touch() }
                    })
                }, contentAlignment = Alignment.Center) {
                Text(if (engine.comparing) "Original" else "A/B", color = if (engine.comparing) accent else Color.White)
            }
        }
    }
}
