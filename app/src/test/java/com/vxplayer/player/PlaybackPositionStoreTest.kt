package com.vxplayer.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPositionStoreTest {
    private class MemoryDao : PlaybackPositionDao {
        val values = mutableMapOf<String, Long>()
        var saveGate: CompletableDeferred<Unit>? = null
        override suspend fun load(uri: String) = values[uri]
        override suspend fun all() = values.map { PlaybackPosition(it.key, it.value) }
        override suspend fun save(position: PlaybackPosition) {
            saveGate?.await()
            values[position.uri] = position.positionMs
        }
        override suspend fun clear(uri: String) { values.remove(uri) }
    }

    @Test fun positionsAreIndependentAndVisibleOnImmediateReopen() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = PlaybackPositionStore(MemoryDao(), scope)
            store.save("content://videos/1", 12_000L)
            store.save("content://videos/2", 45_000L)
            withTimeout(5000) {
                assertEquals(12_000L, store.load("content://videos/1"))
                assertEquals(45_000L, store.load("content://videos/2"))
                assertNull(store.load("content://videos/3"))
            }
        } finally { scope.cancel() }
    }

    @Test fun completionClearRunsAfterAnInFlightCheckpoint() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val gate = CompletableDeferred<Unit>()
            val dao = MemoryDao().apply { saveGate = gate }
            val store = PlaybackPositionStore(dao, scope)
            store.save("content://videos/1", 90_000L)
            store.save("content://videos/1", 0L)
            gate.complete(Unit)
            withTimeout(5000) { assertNull(store.load("content://videos/1")) }
            // A new store represents reopening after process restart, reading the same database.
            val reopened = PlaybackPositionStore(dao, scope)
            withTimeout(5000) { assertNull(reopened.load("content://videos/1")) }
        } finally { scope.cancel() }
    }
}
