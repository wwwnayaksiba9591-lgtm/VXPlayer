package com.vxplayer.player

import android.os.Build
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.*
import com.vxplayer.BuildConfig
import java.util.concurrent.CopyOnWriteArraySet

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackDiagnostics {
    private val candidates = CopyOnWriteArraySet<String>()
    private val initialized = CopyOnWriteArraySet<String>()
    private val failures = CopyOnWriteArraySet<String>()
    private val attempted = java.util.concurrent.CopyOnWriteArrayList<String>()
    fun trackAdapters(delegate: MediaCodecAdapter.Factory) = MediaCodecAdapter.Factory { configuration ->
        if (attempted.size < 200) attempted.add(configuration.codecInfo.name)
        try { delegate.createAdapter(configuration) }
        catch (failure: Exception) { failures.add(configuration.codecInfo.name + ": " + failure.javaClass.simpleName); throw failure }
    }
    var errorCode = "None"
        private set
    var errorMessage = "None"
        private set
    var effectsAtError = false
        private set
    val selector = MediaCodecSelector { mime, secure, tunneling ->
        MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling).also { decoders ->
            decoders.forEach { candidates.add(it.name) }
        }
    }
    val listener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(time: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { initialized.add("Video: $decoderName") }
        override fun onAudioDecoderInitialized(time: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { initialized.add("Audio: $decoderName") }
    }
    fun reset() { candidates.clear(); initialized.clear(); failures.clear(); attempted.clear(); errorCode = "None"; errorMessage = "None"; effectsAtError = false }
    fun capabilityFailure(format: Format) {
        errorCode = "Capability check: no supported video track (no Media3 decoder error emitted)"
        errorMessage = sanitizeProblemText(formatDetails(format))
    }
    fun record(error: PlaybackException, effects: Boolean) {
        errorCode = "${error.errorCodeName} (${error.errorCode})"
        errorMessage = sanitizeProblemText(error.message.orEmpty())
        effectsAtError = effects
        generateSequence<Throwable>(error) { it.cause }.take(20)
            .filterIsInstance<MediaCodecRenderer.DecoderInitializationException>().forEach { first ->
                generateSequence(first) { it.fallbackDecoderInitializationException }.take(20).forEach {
                    failures.add(it.codecInfo?.name ?: "${it.mimeType}: ${it.diagnosticInfo ?: "initialization failed"}")
                }
            }
    }
    fun report(title: String, network: Boolean, privateVideo: Boolean, size: Long, video: Format?, tracks: Tracks, effectsNow: String): String = sanitizeProblemText(buildString {
        appendLine("VXPlayer playback problem report")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("File: ${reportFileName(title, network, privateVideo)}")
        appendLine("Size: ${if (size > 0) "$size bytes" else "Unknown"}")
        appendLine("Video: ${video?.let(::formatDetails) ?: "Not available yet"}")
        for (type in listOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)) {
            appendLine(if (type == C.TRACK_TYPE_AUDIO) "Audio tracks:" else "Subtitle tracks:")
            val groups = tracks.groups.filter { it.type == type }
            if (groups.isEmpty()) appendLine("  None discovered")
            groups.forEachIndexed { groupIndex, group -> repeat(group.length) { index ->
                appendLine("  $groupIndex:$index ${formatDetails(group.getTrackFormat(index))}; supported=${group.isTrackSupported(index)}; selected=${group.isTrackSelected(index)}")
            } }
        }
        appendLine("Effects now: $effectsNow; active at error=$effectsAtError")
        appendLine("Error: $errorCode")
        appendLine("Error message (links/paths redacted): $errorMessage")
        appendLine("Decoder candidates considered: ${candidates.joinToString().ifBlank { "None recorded" }}")
        appendLine("Decoder creation attempts, in order: ${attempted.joinToString().ifBlank { "None recorded" }}")
        appendLine("Initialized decoders: ${initialized.joinToString().ifBlank { "None recorded" }}")
        appendLine("Failed initialization attempts: ${failures.joinToString().ifBlank { "None recorded" }}")
        appendLine("Candidates are not necessarily attempted. No URL, full file path, device identifier or account data is included.")
    })
}

@androidx.annotation.OptIn(UnstableApi::class)
fun formatDetails(format: Format): String {
    val profile = runCatching { MediaCodecUtil.getCodecProfileAndLevel(format) }.getOrNull()
    return "mime=${format.sampleMimeType ?: "unknown"}; container=${format.containerMimeType ?: "unknown"}; codec=${format.codecs ?: "unknown"}; " +
        "profile=${profile?.first ?: "unknown"}; level=${profile?.second ?: "unknown"}; ${format.width}×${format.height}; rotation=${format.rotationDegrees}; pixelAspect=${format.pixelWidthHeightRatio}; " +
        "fps=${format.frameRate}; bitrate=${format.bitrate}; channels=${format.channelCount}; rate=${format.sampleRate}; language=${format.language ?: "und"}; " +
        "HDR/colour=${format.colorInfo?.toLogString() ?: "unknown"}"
}

@androidx.annotation.OptIn(UnstableApi::class)
fun videoEffectsUnsafe(format: Format): Boolean {
    val profile = runCatching { MediaCodecUtil.getCodecProfileAndLevel(format)?.first ?: 0 }.getOrDefault(0)
    val highDepthProfile = when (format.sampleMimeType) {
        MimeTypes.VIDEO_H265, MimeTypes.VIDEO_AV1 -> profile == 2 || profile >= 4096
        MimeTypes.VIDEO_VP9 -> profile in setOf(4, 8, 4096, 8192, 16384, 32768)
        MimeTypes.VIDEO_H264 -> profile in setOf(16, 32, 64)
        else -> false
    }
    return highDepthProfile || format.colorInfo?.hdrStaticInfo != null || unsafeVideoEffects(format.sampleMimeType, format.codecs, format.colorInfo?.colorTransfer ?: -1,
        format.colorInfo?.lumaBitdepth ?: -1, format.drmInitData != null)
}
