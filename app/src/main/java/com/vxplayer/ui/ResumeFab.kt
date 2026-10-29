package com.vxplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ResumeFab(video: ResumeVideo, scrolling: Boolean, busy: Boolean, resume: (Boolean) -> Unit, onDismiss: () -> Unit = {}) {
    var dismissed by rememberSaveable(video.uri) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    if (dismissed) return
    Box {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shadowElevation = 6.dp) {
            Column(Modifier.widthIn(max = 280.dp).combinedClickable(enabled = !busy, role = Role.Button,
                onClick = { resume(false) }, onLongClick = { menu = true })) {
                Row(Modifier.heightIn(min = 64.dp).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, "Resume last video", Modifier.size(28.dp))
                    AnimatedVisibility(!scrolling) {
                        Column(Modifier.padding(start = 12.dp).widthIn(max = 190.dp)) {
                            Text("Resume", style = MaterialTheme.typography.labelLarge)
                            Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (video.durationMs > 0 && video.positionMs > 0) LinearProgressIndicator(
                    progress = { (video.positionMs.toFloat() / video.durationMs).coerceIn(0f, 1f) },
                    modifier = (if (scrolling) Modifier.width(64.dp) else Modifier.fillMaxWidth()).height(2.dp))
            }
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Play from start") }, onClick = { menu = false; resume(true) })
            DropdownMenuItem(text = { Text("Dismiss for now") }, onClick = { menu = false; dismissed = true; onDismiss() })
        }
    }
}
