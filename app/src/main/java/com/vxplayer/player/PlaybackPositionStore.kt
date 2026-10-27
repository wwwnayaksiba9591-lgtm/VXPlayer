package com.vxplayer.player

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@Entity(tableName = "playback_positions")
data class PlaybackPosition(@PrimaryKey val uri: String, val positionMs: Long)

@Dao
interface PlaybackPositionDao {
    @Query("SELECT positionMs FROM playback_positions WHERE uri = :uri")
    suspend fun load(uri: String): Long?

    @Upsert
    suspend fun save(position: PlaybackPosition)

    @Query("DELETE FROM playback_positions WHERE uri = :uri")
    suspend fun clear(uri: String)
}

@Database(entities = [PlaybackPosition::class], version = 1, exportSchema = false)
abstract class PlaybackDatabase : RoomDatabase() {
    abstract fun positions(): PlaybackPositionDao
}

/** Application-owned work survives a player ViewModel being cleared on Back. */
class PlaybackPositionStore internal constructor(dao: PlaybackPositionDao, scope: CoroutineScope) {
    private sealed interface Command {
        data class Read(val uri: String, val result: CompletableDeferred<Long?>) : Command
        data class Write(val uri: String, val positionMs: Long) : Command
    }
    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launch {
            // Serialize reads and writes so reopening immediately observes the last save/clear.
            for (command in commands) {
                try {
                    when (command) {
                        is Command.Read -> command.result.complete(dao.load(command.uri))
                        is Command.Write -> if (command.positionMs <= 0L) dao.clear(command.uri)
                            else dao.save(PlaybackPosition(command.uri, command.positionMs))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (command is Command.Read) command.result.completeExceptionally(error)
                    else Log.e("PlaybackPositionStore", "Could not save playback position", error)
                }
            }
        }
    }

    suspend fun load(uri: String): Long? {
        val result = CompletableDeferred<Long?>()
        commands.send(Command.Read(uri, result))
        return result.await()
    }

    fun save(uri: String, positionMs: Long) {
        commands.trySend(Command.Write(uri, positionMs)).getOrThrow()
    }

    companion object {
        @Volatile private var instance: PlaybackPositionStore? = null

        fun get(context: Context): PlaybackPositionStore = instance ?: synchronized(this) {
            instance ?: PlaybackPositionStore(
                Room.databaseBuilder(context.applicationContext, PlaybackDatabase::class.java, "playback.db")
                    .build().positions(),
                CoroutineScope(SupervisorJob() + Dispatchers.IO),
            ).also { instance = it }
        }
    }
}
