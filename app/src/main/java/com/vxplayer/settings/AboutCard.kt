package com.vxplayer.settings

import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vxplayer.BuildConfig
import com.vxplayer.R

@Composable
fun AboutCard() {
    val flag = remember { if (runCatching { Paint().hasGlyph("🇮🇳") }.getOrDefault(false)) "🇮🇳" else "(IN)" }
    val accent = MaterialTheme.colorScheme.primary
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), "VXPlayer app icon", Modifier.size(56.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(buildAnnotatedString {
                append("Made in India $flag with ")
                withStyle(SpanStyle(color = accent)) { append("❤") }
                append(" by Siba")
            }, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Text(stringResource(R.string.privacy_line), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.streaming_privacy_detail), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            com.vxplayer.diagnostics.LastCrashButton()
        }
    }
}
