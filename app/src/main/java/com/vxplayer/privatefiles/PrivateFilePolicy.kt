package com.vxplayer.privatefiles

import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class PrivateEntry(val id: String, val title: String, val originalUri: String, val size: Long,
    val state: String = "copying", val restoreUri: String? = null)

fun requirePrivateCopyRemovable(state: String) {
    check(state != "deletingOriginal") { "Finish or cancel the Android deletion request before removing this private copy." }
}

class PrivateFileLayout(val root: File) {
    fun file(id: String, suffix: String = ".media"): File {
        require(id.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))) { "Invalid private file ID" }
        require(suffix in listOf(".media", ".part", ".json"))
        return File(root, id + suffix).also { require(it.canonicalFile.parentFile == root.canonicalFile) }
    }
    /** Only a journaled, verified copy can be promoted after an interruption. */
    fun recoverVerifiedCopy(entry: PrivateEntry): File? {
        val media = file(entry.id)
        val partial = file(entry.id, ".part")
        if (!media.exists() && entry.state == "awaitingDelete" && partial.exists()) {
            verifyPrivateCopy(entry.size, partial.length(), partial.length())
            check(partial.renameTo(media)) { "Could not recover the completed private copy." }
        }
        return media.takeIf { it.isFile }
    }
}

fun verifyPrivateCopy(expected: Long, copied: Long, stored: Long) {
    require(copied > 0 && stored == copied && (expected < 0 || expected == copied)) { "Copy size could not be verified. The original is kept." }
}

fun copyPrivateBytes(input: InputStream, output: OutputStream, expected: Long,
    progress: (Long) -> Unit, checkCancelled: () -> Unit): Long {
    val buffer = ByteArray(256 * 1024)
    var copied = 0L
    var reported = 0L
    while (true) {
        checkCancelled()
        val count = input.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
        copied += count
        if (copied - reported >= 1024 * 1024) { progress(copied); reported = copied }
    }
    checkCancelled()
    require(expected < 0 || copied == expected) { "Source size changed. The original is kept." }
    progress(copied)
    return copied
}

class PrivateLockPolicy {
    var unlocked = false
        private set
    private var hiddenAt: Long? = null
    fun unlock() { unlocked = true; hiddenAt = null }
    fun lock() { unlocked = false; hiddenAt = null }
    fun background(now: Long) { if (unlocked && hiddenAt == null) hiddenAt = now }
    fun foreground(now: Long) {
        if (hiddenAt?.let { now - it >= 30_000 } == true) lock()
        hiddenAt = null
    }
}
