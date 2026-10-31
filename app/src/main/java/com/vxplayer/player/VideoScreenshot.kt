package com.vxplayer.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Copies video pixels, never the overlay. Protected surfaces return an explicit failure. */
@androidx.annotation.OptIn(UnstableApi::class)
suspend fun captureVideoFrame(view: PlayerView): Bitmap {
    val surface=view.videoSurfaceView ?: error("No video frame is available.")
    val scale=minOf(1f,1920f/surface.width.coerceAtLeast(1),1080f/surface.height.coerceAtLeast(1))
    val width=(surface.width*scale).toInt().coerceAtLeast(1)
    val height=(surface.height*scale).toInt().coerceAtLeast(1)
    if(surface is TextureView)return surface.getBitmap(width,height) ?: error("No video frame is available.")
    check(Build.VERSION.SDK_INT>=26 && surface is SurfaceView && surface.holder.surface.isValid) {"Video screenshots require Android 8 or a texture surface."}
    val bitmap=withContext(Dispatchers.Default){Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)}
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(surface,bitmap,{result ->
                if(result==PixelCopy.SUCCESS && continuation.isActive)continuation.resume(bitmap)
                else {bitmap.recycle();if(continuation.isActive)continuation.resumeWithException(IllegalStateException("Frame unavailable or protected. Try again after playback starts."))}
            },Handler(Looper.getMainLooper()))
        } catch(failure: RuntimeException) {bitmap.recycle();if(continuation.isActive)continuation.resumeWithException(failure)}
    }
}

suspend fun saveVideoScreenshot(context: Context,bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
    val resolver=context.contentResolver
    val name="VXPlayer_${System.currentTimeMillis()}.png"
    val values=ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME,name);put(MediaStore.Images.Media.MIME_TYPE,"image/png")
        if(Build.VERSION.SDK_INT>=29) {put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/VXPlayer");put(MediaStore.Images.Media.IS_PENDING,1)}
        else {
            @Suppress("DEPRECATION") val folder=java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),"VXPlayer")
            check(folder.isDirectory || folder.mkdirs()) {"Could not create Pictures/VXPlayer."}
            @Suppress("DEPRECATION") put(MediaStore.Images.Media.DATA,java.io.File(folder,name).absolutePath)
        }
    }
    val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: error("Gallery is unavailable.")
    try {
        resolver.openOutputStream(uri)?.use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) {"Could not save screenshot."} } ?: error("Could not open screenshot file.")
        if(Build.VERSION.SDK_INT>=29)resolver.update(uri,ContentValues().apply {put(MediaStore.Images.Media.IS_PENDING,0)},null,null)
        uri
    } catch(failure: Exception) {runCatching {resolver.delete(uri,null,null)};throw failure}
}
