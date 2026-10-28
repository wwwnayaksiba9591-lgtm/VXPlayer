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
    @Query("SELECT * FROM playback_positions")
    suspend fun all(): List<PlaybackPosition>
    @Query("SELECT positionMs FROM playback_positions WHERE uri = :uri")
    suspend fun load(uri: String): Long?

    @Upsert
    suspend fun save(position: PlaybackPosition)

    @Query("DELETE FROM playback_positions WHERE uri = :uri")
    suspend fun clear(uri: String)
}

@Database(entities = [PlaybackPosition::class, RecentlyPlayed::class, WatchedVideo::class, TrackPreference::class], version = 4, exportSchema = false)
abstract class PlaybackDatabase : RoomDatabase() {
    abstract fun positions(): PlaybackPositionDao
    abstract fun recentlyPlayed(): RecentlyPlayedDao
    abstract fun watched(): WatchedVideoDao
    abstract fun trackPreferences(): TrackPreferenceDao
}

/** Application-owned work survives a player ViewModel being cleared on Back. */
class PlaybackPositionStore internal constructor(dao: PlaybackPositionDao, scope: CoroutineScope, history: RecentlyPlayedDao? = null, watched: WatchedVideoDao? = null, choices: TrackPreferenceDao? = null) {
    private sealed interface Command {
        data class Read(val uri: String, val result: CompletableDeferred<Long?>) : Command
        data class Write(val uri: String, val positionMs: Long) : Command
        data class Record(val video: RecentlyPlayed) : Command
        data class Recent(val result: CompletableDeferred<List<RecentlyPlayed>>) : Command
        data class Finished(val uri: String) : Command
        data class Snapshot(val result: CompletableDeferred<PlaybackSnapshot>) : Command
        data class ReadChoice(val uri: String, val result: CompletableDeferred<TrackPreference?>) : Command
        data class WriteChoice(val choice: TrackPreference) : Command
    }
    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launch {
            // Serialize reads and writes so reopening immediately observes the last save/clear.
            for (command in commands) {
                try {
                    when (command) {
                        is Command.Read -> command.result.complete(dao.load(command.uri))
                        is Command.ReadChoice -> command.result.complete(choices?.load(command.uri))
                        is Command.WriteChoice -> choices?.save(command.choice)
                        is Command.Record -> history?.record(command.video)
                        is Command.Recent -> command.result.complete(history?.recent().orEmpty())
                        is Command.Finished -> watched?.save(WatchedVideo(command.uri))
                        is Command.Snapshot -> command.result.complete(PlaybackSnapshot(dao.all().associate { it.uri to it.positionMs }, watched?.all().orEmpty().map { it.uri }.toSet()))
                        is Command.Write -> if (command.positionMs <= 0L) dao.clear(command.uri)
                            else dao.save(PlaybackPosition(command.uri, command.positionMs))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    when (command) {
                        is Command.Read -> command.result.completeExceptionally(error)
                        is Command.ReadChoice -> command.result.completeExceptionally(error)
                        is Command.Recent -> command.result.completeExceptionally(error)
                        is Command.Snapshot -> command.result.completeExceptionally(error)
                        else -> Log.e("PlaybackPositionStore", "Could not save playback data", error)
                    }
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

    fun recordPlayed(uri: String, title: String) {
        commands.trySend(Command.Record(RecentlyPlayed(uri, title, System.currentTimeMillis()))).getOrThrow()
    }

    fun finished(uri: String) { commands.trySend(Command.Finished(uri)).getOrThrow() }
    fun saveChoice(choice: TrackPreference) { commands.trySend(Command.WriteChoice(choice)).getOrThrow() }
    suspend fun loadChoice(uri: String): TrackPreference? {
        val result = CompletableDeferred<TrackPreference?>()
        commands.send(Command.ReadChoice(uri, result))
        return result.await()
    }

    suspend fun snapshot(): PlaybackSnapshot {
        val result = CompletableDeferred<PlaybackSnapshot>()
        commands.send(Command.Snapshot(result))
        return result.await()
    }

    suspend fun recent(): List<RecentlyPlayed> {
        val result = CompletableDeferred<List<RecentlyPlayed>>()
        commands.send(Command.Recent(result))
        return result.await()
    }

    companion object {
        @Volatile private var instance: PlaybackPositionStore? = null

        fun get(context: Context): PlaybackPositionStore = instance ?: synchronized(this) {
            instance ?: run {
                val database = Room.databaseBuilder(context.applicationContext, PlaybackDatabase::class.java, "playback.db")
                    .addMigrations(PLAYBACK_MIGRATION_1_2, PLAYBACK_MIGRATION_2_3, PLAYBACK_MIGRATION_3_4).build()
                PlaybackPositionStore(database.positions(), CoroutineScope(SupervisorJob() + Dispatchers.IO), database.recentlyPlayed(), database.watched(), database.trackPreferences())
            }.also { instance = it }
        }
    }
}
