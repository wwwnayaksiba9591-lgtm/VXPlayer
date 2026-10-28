package com.vxplayer.player

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

fun subtitleDelayKey(videoUri: String): String = "srtDelay:" + MessageDigest.getInstance("SHA-256").digest(videoUri.toByteArray()).joinToString("") { "%02x".format(it) }

object ShiftedSubtitleCache {
    fun prepare(context: Context, source: Uri, delayMs: Int, protectedUri: Uri?): Uri {
        val bytes = context.contentResolver.openInputStream(source)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 4 * 1024 * 1024) { "Subtitle exceeds 4 MiB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Subtitle is not readable")
        val charset = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        val shifted = SrtTiming.shift(bytes.toString(charset), delayMs)
        val digest = MessageDigest.getInstance("SHA-256").digest((source.toString() + delayMs + shifted).toByteArray()).joinToString("") { "%02x".format(it) }
        val directory = File(context.cacheDir, "shifted-subtitles").apply { mkdirs() }
        val file = File(directory, "$digest.srt")
        synchronized(this) {
            if (!file.exists()) {
                val temporary = File.createTempFile("subtitle-", ".tmp", directory)
                try {
                    temporary.writeText(shifted, Charsets.UTF_8)
                    check(temporary.renameTo(file) || file.exists()) { "Could not cache subtitle" }
                } finally { temporary.delete() }
            }
            directory.listFiles()?.filter { it != file && it.absolutePath != protectedUri?.path }
                ?.sortedByDescending { it.lastModified() }?.drop(14)?.forEach { it.delete() }
        }
        return Uri.fromFile(file)
    }
}
