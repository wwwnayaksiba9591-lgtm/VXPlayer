package com.vxplayer.privatefiles

import android.app.Application
import android.app.RecoverableSecurityException
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

class PrivateViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val repository = PrivateRepository(application)
    var entries by mutableStateOf<List<PrivateEntry>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var copied by mutableLongStateOf(0)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var consent by mutableStateOf<IntentSenderRequest?>(null)
        private set
    var selectedId by mutableStateOf(saved.get<String>("selectedPrivateId"))
        private set
    var pendingFinishId by mutableStateOf<String?>(null)
        private set
    private var work: Job? = null

    fun select(id: String?) { selectedId = id; saved["selectedPrivateId"] = id }
    fun cancel() { work?.cancel() }
    fun consumeConsent() { consent = null }
    fun consumePendingFinish() { pendingFinishId = null }
    fun clearMessage() { message = null }
    fun refresh() {
        if (!busy) viewModelScope.launch {
            try { entries = repository.entries() }
            catch (_: Exception) { message = "Could not read the private index. Files have been kept." }
        }
    }
    private fun runWork(action: suspend () -> String) {
        if (busy) return
        busy = true; copied = 0; message = null
        work = viewModelScope.launch {
            try { message = PrivateRepository.operations.withLock { action() } }
            catch (cancelled: CancellationException) { message = "Cancelled. Source files are kept; verified copies can be recovered here." }
            catch (failure: Exception) { message = failure.message ?: "The operation failed. Source files are kept." }
            finally {
                withContext(NonCancellable) {
                    try { entries = repository.entries() }
                    catch (_: Exception) { message = "Could not read the private index. Files have been kept." }
                }
                busy = false
            }
        }
    }
    fun beginMove(source: Uri?, title: String, expected: Long) {
        if (source == null || saved.get<Boolean>("moveStarted") == true || !PrivateAccess.unlocked) return
        saved["moveStarted"] = true
        runWork {
            // Throttle updates in the copy function; no Activity is retained by the worker.
            val entry = repository.prepareMove(source, title, expected) { count ->
                viewModelScope.launch(Dispatchers.Main) { copied = count }
            }
            pendingFinishId = entry.id
            "Private copy verified. Tap Finish move for ${entry.title} to remove the gallery original."
        }
    }

    fun finishMove(entry: PrivateEntry, granted: Boolean = false) = runWork {
        repository.verifiedMedia(entry)
        if (repository.originalExists(entry)) {
            if (Build.VERSION.SDK_INT >= 30 && !granted) {
                saved["consentEntry"] = entry.id
                val request = MediaStore.createDeleteRequest(getApplication<Application>().contentResolver, listOf(Uri.parse(entry.originalUri)))
                repository.markDeletionRequest(entry)
                consent = IntentSenderRequest.Builder(request.intentSender).build()
                return@runWork "Confirm Android’s deletion request to finish hiding the original."
            }
            if (Build.VERSION.SDK_INT >= 30) error("The original is still visible. Try Finish move again.")
            try { repository.deleteOriginal(entry) }
            catch (failure: SecurityException) {
                if (Build.VERSION.SDK_INT == 29 && failure is RecoverableSecurityException && !granted) {
                    saved["consentEntry"] = entry.id
                    repository.markDeletionRequest(entry)
                    consent = IntentSenderRequest.Builder(failure.userAction.actionIntent.intentSender).build()
                    return@runWork "Confirm Android’s request to finish the move."
                }
                throw failure
            }
        }
        repository.finishMove(entry)
        "Moved to Private. The gallery original was removed."
    }

    fun consentResult(granted: Boolean) {
        val id = saved.get<String>("consentEntry") ?: return
        saved.remove<String>("consentEntry")
        if (!granted) {
            runWork {
                repository.cancelDeletionRequest(id)
                "Move unfinished. The original may still be in the gallery; the verified private copy is kept."
            }
            return
        }
        viewModelScope.launch {
            val entry = runCatching { repository.entries().firstOrNull { it.id == id } }.getOrNull()
            if (entry != null) finishMove(entry, granted = true)
            else message = "Reopen Private and use Finish move to check the operation."
        }
    }

    fun restore(entry: PrivateEntry) = runWork {
        repository.restore(entry) { count -> viewModelScope.launch(Dispatchers.Main) { copied = count } }
        "Restored to Movies/VXPlayer. The private copy was removed."
    }
    fun delete(entry: PrivateEntry) = runWork {
        repository.delete(entry)
        "Private copy deleted."
    }
}
