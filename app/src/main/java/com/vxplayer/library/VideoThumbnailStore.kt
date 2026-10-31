package com.vxplayer.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.CancellationSignal
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest

/** Bounded caches and decode concurrency keep fast scrolling away from the UI thread. */
internal object VideoThumbnailStore {
    private val memory=object : LruCache<String,Bitmap>(8*1024*1024) {override fun sizeOf(key: String,value: Bitmap)=value.allocationByteCount}
    private val decodes=Semaphore(3)
    private val writes=java.util.concurrent.atomic.AtomicInteger()
    fun key(video: Video)="${video.uri}:${video.dateAdded}:${video.sizeBytes}"
    fun peek(video: Video): Bitmap?=memory.get(key(video))
    suspend fun load(context: Context,video: Video,signal: CancellationSignal): Bitmap?=withContext(Dispatchers.IO) {
        decodes.withPermit {
            val key=key(video);memory.get(key)?.let {return@withPermit it}
            signal.throwIfCanceled();currentCoroutineContext().ensureActive()
            val directory=File(context.cacheDir,"video-thumbnails").apply {mkdirs()}
            val hash=MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
            val disk=File(directory,"$hash.jpg")
            val cached=runCatching {BitmapFactory.decodeFile(disk.absolutePath)}.getOrNull()
            if(cached!=null){disk.setLastModified(System.currentTimeMillis());memory.put(key,cached);return@withPermit cached}
            var bitmap=runCatching {if(Build.VERSION.SDK_INT>=29)context.contentResolver.loadThumbnail(video.uri,Size(384,240),signal) else null}.getOrNull()
            signal.throwIfCanceled();currentCoroutineContext().ensureActive()
            if(bitmap==null)bitmap=fallbackVideoThumbnail(context,video.uri)
            val result=bitmap ?: return@withPermit null
            signal.throwIfCanceled();currentCoroutineContext().ensureActive()
            val factor=minOf(1f,384f/result.width,240f/result.height)
            val small=if(factor<1f)Bitmap.createScaledBitmap(result,(result.width*factor).toInt().coerceAtLeast(1),(result.height*factor).toInt().coerceAtLeast(1),true).also {result.recycle()} else result
            val temporary=File(directory,"$hash.${java.util.UUID.randomUUID()}.tmp")
            try {
                runCatching {temporary.outputStream().use {small.compress(Bitmap.CompressFormat.JPEG,85,it)};temporary.renameTo(disk)}
                // Amortize disk metadata scans during fast scrolling; never trim an active temporary file.
                if(writes.getAndIncrement()%32==0) {
                    val files=directory.listFiles().orEmpty().filter {it.extension=="jpg"}.sortedBy {it.lastModified()}
                    var bytes=files.sumOf {it.length()}
                    for(file in files){if(bytes<=64L*1024*1024)break;val length=file.length();if(file.delete())bytes-=length}
                }
            } finally {temporary.delete()}
            memory.put(key,small);small
        }
    }
}
