package com.vxplayer.player

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@androidx.annotation.OptIn(UnstableApi::class)
suspend fun sampleVideoFrame(view: PlayerView): FrameSummary? {
    val surface = view.videoSurfaceView
    val bitmap = try {
        if (Build.VERSION.SDK_INT >= 26 && surface is SurfaceView && surface.holder.surface.isValid) {
            suspendCancellableCoroutine<Bitmap?> { continuation ->
                val small = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888)
                try {
                    PixelCopy.request(surface, small, { result ->
                        if (result == PixelCopy.SUCCESS && continuation.isActive) continuation.resume(small)
                        else { small.recycle(); if (continuation.isActive) continuation.resume(null) }
                    }, Handler(Looper.getMainLooper()))
                } catch (_: RuntimeException) { small.recycle(); if (continuation.isActive) continuation.resume(null) }
            }
        } else (surface as? TextureView)?.getBitmap(32, 18)
    } catch (_: RuntimeException) { null } ?: return null
    return try {
        withContext(Dispatchers.Default) {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            summarizeFrame(pixels)
        }
    } finally { bitmap.recycle() }
}
