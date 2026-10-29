package com.vxplayer.network

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vxplayer.player.PlaybackPositionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NetworkViewModel(application: Application) : AndroidViewModel(application) {
    val repository = NetworkRepository(application)
    var error by mutableStateOf<String?>(null)
        private set
    val links = repository.links.catch { error = "Could not load saved links. Your existing data is kept." }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var positions by mutableStateOf<Map<String, Long>>(emptyMap())
        private set
    fun refreshPositions() = viewModelScope.launch {
        try { positions = PlaybackPositionStore.get(getApplication()).snapshot().positions }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not read resume positions." }
    }
    fun action(block: suspend NetworkRepository.() -> Unit) {
        viewModelScope.launch {
            try { repository.block(); error = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Could not update links. Try again." }
        }
    }
}
