package com.vxplayer.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.vxplayer.library.StreamPageProbe
import com.vxplayer.library.StreamUrls
import java.io.IOException
import androidx.media3.datasource.cache.*
import androidx.media3.database.StandaloneDatabaseProvider

/** Constructed on Dispatchers.IO; one bounded cache owns the directory for the process. */
@androidx.annotation.OptIn(UnstableApi::class)
private object StreamCache {
    private var instance: SimpleCache? = null
    @Synchronized fun get(context: Context): SimpleCache = instance ?: SimpleCache(
        java.io.File(context.cacheDir,"media-streams"),LeastRecentlyUsedCacheEvictor(256L*1024*1024),
        StandaloneDatabaseProvider(context.applicationContext)).also {instance=it}
}

@androidx.annotation.OptIn(UnstableApi::class)
class DirectMediaLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        if (generateSequence<Throwable>(info.exception) { it.cause }.take(20).any {
            it is HttpDataSource.InvalidContentTypeException && StreamPageProbe.isHtml(it.contentType)
        }) C.TIME_UNSET else super.getRetryDelayMsFor(info)
}

/** The only HTTP entry point. Local playback has no network capability. */
@androidx.annotation.OptIn(UnstableApi::class)
fun directMediaFactory(context: Context, approved: Boolean, headers: Map<String,String> = emptyMap()): DataSource.Factory {
    val safe=headers.filter {it.key in setOf("Referer","User-Agent") && it.value.none {ch->ch=='\r'||ch=='\n'}}
    val http = DefaultHttpDataSource.Factory().setUserAgent(safe["User-Agent"] ?: StreamPageProbe.MOBILE_UA).setDefaultRequestProperties(safe)
        .setConnectTimeoutMs(8000).setReadTimeoutMs(12000).setAllowCrossProtocolRedirects(true)
        .setContentTypePredicate { !StreamPageProbe.isHtml(it) }
    val guarded = DataSource.Factory { DirectMediaDataSource(http.createDataSource(), approved) }
    if(!approved)return DefaultDataSource.Factory(context,guarded)
    val cached = runCatching { CacheDataSource.Factory().setCache(StreamCache.get(context)).setUpstreamDataSourceFactory(guarded)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR) }.getOrNull() ?: guarded
    return DefaultDataSource.Factory(context,cached)
}

@androidx.annotation.OptIn(UnstableApi::class)
private class DirectMediaDataSource(private val upstream: HttpDataSource, private val approved: Boolean) : DataSource by upstream {
    private var prefix = ByteArray(0)
    private var offset = 0
    override fun open(dataSpec: DataSpec): Long {
        if (!approved || runCatching { StreamUrls.validate(dataSpec.uri.toString()) }.isFailure) throw IOException("Network access is only allowed for user-entered media links.")
        try {
            val length = upstream.open(dataSpec)
            val buffer = ByteArray(512)
            var count = 0
            while (count < buffer.size) {
                val read = upstream.read(buffer, count, buffer.size - count)
                if (read == C.RESULT_END_OF_INPUT) break
                if (read == 0) break
                count += read
            }
            val text = String(buffer, 0, count, Charsets.UTF_8)
            if (StreamPageProbe.isHtml(null, text)) throw HttpDataSource.InvalidContentTypeException("text/html", dataSpec)
            prefix = buffer.copyOf(count); offset = 0
            return length
        } catch (failure: Exception) {
            runCatching { upstream.close() }
            throw failure
        }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val available = prefix.size - this.offset
        if (available > 0) {
            val size = minOf(length, available)
            prefix.copyInto(buffer, offset, this.offset, this.offset + size)
            this.offset += size
            return size
        }
        return upstream.read(buffer, offset, length)
    }
    override fun close() { prefix = ByteArray(0); offset = 0; upstream.close() }
}
