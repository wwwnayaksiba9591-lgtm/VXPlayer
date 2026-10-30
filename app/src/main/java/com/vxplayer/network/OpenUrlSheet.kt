package com.vxplayer.network

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenUrlSheet(onPlay: (NetworkLink) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), sheetMaxWidth = 560.dp) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.96f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link,null); Spacer(Modifier.width(12.dp))
                Text("Open network stream",Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close,"Close") }
            }
            Box(Modifier.weight(1f)) { NetworkTab(onPlay = onPlay, onOpened = onDismiss) }
        }
    }
}
