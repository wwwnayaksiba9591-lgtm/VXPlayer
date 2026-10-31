package com.vxplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shadowElevation = 6.dp) {
            Column(Modifier.size(64.dp).combinedClickable(enabled = !busy, role = Role.Button,
                onClick = { resume(false) }, onLongClick = { menu = true })) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape),contentAlignment=Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, "Resume last video", Modifier.size(28.dp),tint=MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Play from start") }, onClick = { menu = false; resume(true) })
            DropdownMenuItem(text = { Text("Dismiss for now") }, onClick = { menu = false; dismissed = true; onDismiss() })
        }
    }
}
