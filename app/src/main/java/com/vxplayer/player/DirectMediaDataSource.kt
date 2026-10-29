package com.vxplayer.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import com.vxplayer.library.StreamPageProbe
import com.vxplayer.library.StreamUrls
import java.io.IOException

/** The only HTTP entry point. Local playback has no network capability. */
@androidx.annotation.OptIn(UnstableApi::class)
fun directMediaFactory(context: Context, approved: Boolean): DataSource.Factory {
    val http = DefaultHttpDataSource.Factory().setUserAgent("VXPlayer/1.1")
        .setConnectTimeoutMs(8000).setReadTimeoutMs(12000).setAllowCrossProtocolRedirects(true)
        .setContentTypePredicate { !StreamPageProbe.isHtml(it) }
    return DefaultDataSource.Factory(context, DataSource.Factory { DirectMediaDataSource(http.createDataSource(), approved) })
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
