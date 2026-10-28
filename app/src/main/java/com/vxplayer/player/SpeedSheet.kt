package com.vxplayer.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(model: PlayerViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text("Playback speed", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            PlayerViewModel.SPEEDS.forEach { rate ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { model.changeSpeed(rate); onDismiss() }.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(model.speed == rate, onClick = null)
                    Text("${rate.toString().removeSuffix(".0")}×", modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
