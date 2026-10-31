package com.vxplayer.privatefiles

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.AtomicFile
import android.webkit.MimeTypeMap
import com.google.gson.Gson
import com.vxplayer.player.PlaybackPositionStore
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PrivateRepository(context: Context) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    val layout = PrivateFileLayout(File(app.filesDir, "private"))
    @Volatile var unreadableRecords=0
        private set
    private fun ensureStorage(){check(layout.root.mkdirs() || layout.root.isDirectory){"Private storage is unavailable. Your original files are kept."}}
    private val gson = Gson()
    private fun read(id: String): PrivateEntry = AtomicFile(layout.file(id, ".json")).openRead().bufferedReader().use {
        gson.fromJson(it, PrivateEntry::class.java)
    }

    private fun write(entry: PrivateEntry) {
        val atomic = AtomicFile(layout.file(entry.id, ".json"))
        val output = atomic.startWrite()
        try { output.write(gson.toJson(entry).toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (failure: Throwable) { atomic.failWrite(output); throw failure }
    }

    suspend fun entries(): List<PrivateEntry> = withContext(Dispatchers.IO) {
        ensureStorage()
        val ids=layout.root.listFiles().orEmpty().mapNotNull { file ->
            when {
                file.name.endsWith(".json") -> file.name.removeSuffix(".json")
                file.name.endsWith(".json.bak") -> file.name.removeSuffix(".json.bak")
                else -> null
            }
        }.distinct()
        val coroutine=currentCoroutineContext()
        val (records,errors)=readPrivateRecords(ids,read@{ id ->
            coroutine.ensureActive()
            if (id in activeCopies) return@read null
            val entry = AtomicFile(layout.file(id, ".json")).openRead().bufferedReader().use { gson.fromJson(it, PrivateEntry::class.java) }
            require(entry.id == id && entry.size >= 0 && entry.title.isNotBlank() && entry.state in listOf("copying", "awaitingDelete", "deletingOriginal", "private")) { "Invalid private index" }
            val media = layout.recoverVerifiedCopy(entry) ?: layout.file(id)
            val partial = layout.file(id, ".part")
            if (!media.exists()) {
                if (entry.state == "copying") {
                    // A process died before verification; its public source was never removed.
                    partial.delete()
                    AtomicFile(layout.file(id, ".json")).delete()
                }
                null
            }
            else if (entry.state == "copying") {
                verifyPrivateCopy(entry.size, media.length(), media.length())
                entry.copy(state = "awaitingDelete").also(::write)
            } else entry
        })
        unreadableRecords=errors
        records.sortedBy {it.title.lowercase()}
    }

    suspend fun prepareMove(source: Uri, title: String, expected: Long, progress: (Long) -> Unit): PrivateEntry = withContext(Dispatchers.IO) {
        ensureStorage()
        require(source.scheme == "content" && source.authority == "media") { "Only library videos can be moved." }
        val size = resolver.openFileDescriptor(source, "r")?.use { it.statSize }?.takeIf { it >= 0 } ?: expected
        val entry = PrivateEntry(UUID.randomUUID().toString(), title, source.toString(), size.coerceAtLeast(0))
        val partial = layout.file(entry.id, ".part")
        val media = layout.file(entry.id)
        activeCopies.add(entry.id)
        try {
            write(entry)
            val coroutine = currentCoroutineContext()
            val copied = requireNotNull(resolver.openInputStream(source)).use { input ->
                FileOutputStream(partial).use { output ->
                    copyPrivateBytes(input, output, size.takeIf { it > 0 } ?: -1, progress) { coroutine.ensureActive() }
                        .also { output.fd.sync() }
                }
            }
            verifyPrivateCopy(size.takeIf { it > 0 } ?: -1, copied, partial.length())
            currentCoroutineContext().ensureActive()
            // Record the exact verified length before promoting the file or requesting deletion.
            val ready = entry.copy(size = copied, state = "awaitingDelete")
            write(ready)
            check(layout.recoverVerifiedCopy(ready) != null) { "Could not finish the private copy. The original is kept." }
            val position = PlaybackPositionStore.get(app).load(source.toString()) ?: 0L
            app.getSharedPreferences("private-playback", 0).edit().putLong(entry.id, position).apply()
            ready
        } catch (failure: Throwable) {
            // No deletion request has been made at this point.
            partial.delete()
            if (!media.exists()) AtomicFile(layout.file(entry.id, ".json")).delete()
            throw failure
        } finally {
            activeCopies.remove(entry.id)
        }
    }

    fun verifiedMedia(entry: PrivateEntry): File = layout.file(entry.id).also {
        check(it.isFile); verifyPrivateCopy(entry.size, entry.size, it.length())
    }

    suspend fun originalExists(entry: PrivateEntry): Boolean = withContext(Dispatchers.IO) {
        resolver.query(Uri.parse(entry.originalUri), arrayOf(MediaStore.Video.Media._ID), null, null, null)?.use { it.moveToFirst() }
            ?: error("Cannot check the public original. Try granting library access again.")
    }

    suspend fun deleteOriginal(entry: PrivateEntry) = withContext(Dispatchers.IO) {
        verifiedMedia(entry)
        resolver.delete(Uri.parse(entry.originalUri), null, null)
        check(!originalExists(entry)) { "The original is still in the library. Finish the move to hide it." }
    }

    suspend fun finishMove(entry: PrivateEntry) = withContext(Dispatchers.IO) {
        verifiedMedia(entry)
        check(!originalExists(entry)) { "The original is still in the library. Finish the move to hide it." }
        write(entry.copy(state = "private"))
    }

    suspend fun markDeletionRequest(entry: PrivateEntry) = withContext(Dispatchers.IO) {
        verifiedMedia(entry)
        write(entry.copy(state = "deletingOriginal"))
    }
    suspend fun cancelDeletionRequest(id: String) = withContext(Dispatchers.IO) {
        val entry = read(id)
        if (entry.state == "deletingOriginal") write(entry.copy(state = "awaitingDelete"))
    }

    suspend fun delete(entry: PrivateEntry) = withContext(Dispatchers.IO) {
        requirePrivateCopyRemovable(read(entry.id).state)
        // This never deletes a public original, including when a move was cancelled.
        if (Build.VERSION.SDK_INT >= 29) entry.restoreUri?.let { saved ->
            val target = Uri.parse(saved)
            val pending = resolver.query(target, arrayOf(MediaStore.Video.Media.IS_PENDING), null, null, null)?.use {
                it.moveToFirst() && it.getInt(0) == 1
            } == true
            if (pending) resolver.delete(target, null, null)
        }
        val media = layout.file(entry.id)
        check(!media.exists() || media.delete()) { "Could not delete the private video." }
        layout.file(entry.id, ".part").delete()
        AtomicFile(layout.file(entry.id, ".json")).delete()
        app.getSharedPreferences("private-playback", 0).edit().remove(entry.id).commit()
    }

    suspend fun restore(entry: PrivateEntry, progress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        requirePrivateCopyRemovable(read(entry.id).state)
        val media = verifiedMedia(entry)
        // A crash after publishing must not create another public copy on retry.
        entry.restoreUri?.let { saved ->
            val old = Uri.parse(saved)
            val state = resolver.query(old, arrayOf(MediaStore.Video.Media.SIZE) +
                if (Build.VERSION.SDK_INT >= 29) arrayOf(MediaStore.Video.Media.IS_PENDING) else emptyArray(), null, null, null)?.use {
                if (!it.moveToFirst()) null else it.getLong(0) to (Build.VERSION.SDK_INT >= 29 && it.getInt(1) == 1)
            }
            if (state != null && !state.second && state.first == entry.size) {
                verifyPrivateCopy(entry.size, entry.size, requireNotNull(resolver.openFileDescriptor(old, "r")).use { it.statSize })
                restorePosition(entry, old)
                delete(entry); return@withContext
            }
            if (state != null && (state.second || state.first != entry.size)) resolver.delete(old, null, null)
        }
        val title = entry.title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(180).ifBlank { "video.mp4" }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, title)
            put(MediaStore.Video.Media.MIME_TYPE, MimeTypeMap.getSingleton().getMimeTypeFromExtension(title.substringAfterLast('.').lowercase()) ?: "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VXPlayer")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VXPlayer").apply { check(mkdirs() || isDirectory) }
                put(MediaStore.Video.Media.DATA, File(directory, "${entry.id}-$title").absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val target = requireNotNull(resolver.insert(collection, values)) { "Could not create a Movies entry." }
        var published = false
        try {
            write(entry.copy(restoreUri = target.toString()))
            val coroutine = currentCoroutineContext()
            val copied = media.inputStream().use { input ->
                ParcelFileDescriptor.AutoCloseOutputStream(requireNotNull(resolver.openFileDescriptor(target, "w"))).use { output ->
                    copyPrivateBytes(input, output, entry.size, progress) { coroutine.ensureActive() }.also { output.fd.sync() }
                }
            }
            val length = requireNotNull(resolver.openFileDescriptor(target, "r")).use { it.statSize }
            verifyPrivateCopy(entry.size, copied, length)
            currentCoroutineContext().ensureActive()
            if (Build.VERSION.SDK_INT >= 29) check(resolver.update(target, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) > 0)
            published = true
            restorePosition(entry, target)
            // No suspension between publishing and the durable record. If interrupted, retry verifies the published copy.
            delete(entry)
        } catch (failure: Throwable) {
            if (!published) withContext(NonCancellable) {
                val removed = runCatching { resolver.delete(target, null, null); true }.getOrDefault(false)
                if (removed) write(entry.copy(restoreUri = null))
            }
            throw failure
        }
    }

    private fun restorePosition(entry: PrivateEntry, target: Uri) {
        val position = (app.getSharedPreferences("private-playback", 0).all[entry.id] as? Number)?.toLong()?.coerceAtLeast(0) ?: 0L
        PlaybackPositionStore.get(app).save(target.toString(), position)
    }

    companion object {
        val operations = kotlinx.coroutines.sync.Mutex()
        private val activeCopies = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        fun privateId(context: Context, uri: Uri): String? {
            if (uri.scheme != "file") return null
            val root = File(context.filesDir, "private").canonicalFile
            val path = uri.path ?: return null
            val file = File(path).canonicalFile
            return file.takeIf { it.parentFile == root && it.name.endsWith(".media") }
                ?.name?.removeSuffix(".media")?.takeIf { runCatching { PrivateFileLayout(root).file(it) }.isSuccess }
        }
    }
}
