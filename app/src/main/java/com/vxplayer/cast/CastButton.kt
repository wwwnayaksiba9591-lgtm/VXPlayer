package com.vxplayer.cast

import android.content.Intent
import android.provider.Settings
import android.view.ContextThemeWrapper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.vxplayer.R

@Composable fun VXCastButton(ready: ()->Unit) {
    val context=LocalContext.current
    val available=remember(context){runCatching {CastContext.getSharedInstance(context);true}.getOrDefault(false)}
    if(available)AndroidView(modifier=Modifier.size(48.dp),factory={
        MediaRouteButton(ContextThemeWrapper(it,androidx.appcompat.R.style.Theme_AppCompat)).apply {
            setAlwaysVisible(true);contentDescription="Cast to a device"
            CastButtonFactory.setUpMediaRouteButton(context,this)
            ready()
        }
    }) else IconButton(onClick={runCatching {context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS))}.onFailure {android.widget.Toast.makeText(context,"Screen casting settings are unavailable on this device.",android.widget.Toast.LENGTH_SHORT).show()}}) {Icon(Icons.Default.Cast,"Cast screen")}
}
