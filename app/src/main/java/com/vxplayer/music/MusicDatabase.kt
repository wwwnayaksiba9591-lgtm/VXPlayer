package com.vxplayer.music

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "music_stats")
data class MusicStat(@PrimaryKey val uri: String, val playCount: Long = 0, val lastPlayed: Long = 0, val favourite: Boolean = false)
@Entity(tableName = "music_playlists")
data class MusicPlaylist(@PrimaryKey val id: String, val name: String)
@Entity(tableName = "music_playlist_items", primaryKeys = ["playlistId","uri"])
data class MusicPlaylistItem(val playlistId: String, val uri: String, val sortIndex: Int)
@Entity(tableName = "music_session")
data class MusicSessionState(@PrimaryKey val id: Int = 1, val songsJson: String, val currentIndex: Int, val position: Long, val shuffle: Boolean, val repeatMode: Int)

@Dao
abstract class MusicDao {
    @Query("SELECT * FROM music_stats") abstract fun stats(): Flow<List<MusicStat>>
    @Query("SELECT * FROM music_playlists ORDER BY name COLLATE NOCASE") abstract fun playlists(): Flow<List<MusicPlaylist>>
    @Query("SELECT * FROM music_playlist_items ORDER BY sortIndex") abstract fun items(): Flow<List<MusicPlaylistItem>>
    @Query("SELECT * FROM music_stats WHERE uri = :uri") abstract suspend fun stat(uri: String): MusicStat?
    @Upsert abstract suspend fun saveStat(stat: MusicStat)
    @Transaction open suspend fun played(uri: String) { val old = stat(uri) ?: MusicStat(uri); saveStat(old.copy(playCount=old.playCount+1,lastPlayed=System.currentTimeMillis())) }
    @Transaction open suspend fun favourite(uri: String) { val old = stat(uri) ?: MusicStat(uri); saveStat(old.copy(favourite = !old.favourite)) }
    @Upsert abstract suspend fun savePlaylist(playlist: MusicPlaylist)
    @Upsert abstract suspend fun saveItems(items: List<MusicPlaylistItem>)
    @Query("DELETE FROM music_playlist_items WHERE playlistId = :id") abstract suspend fun clearItems(id: String)
    @Query("DELETE FROM music_playlists WHERE id = :id") abstract suspend fun deletePlaylistRow(id: String)
    @Transaction open suspend fun deletePlaylist(id: String) { clearItems(id); deletePlaylistRow(id) }
    @Transaction open suspend fun replaceItems(id: String, uris: List<String>) { clearItems(id); saveItems(uris.distinct().mapIndexed { i,u -> MusicPlaylistItem(id,u,i) }) }
    @Query("SELECT * FROM music_session WHERE id = 1") abstract suspend fun session(): MusicSessionState?
    @Upsert abstract suspend fun saveSession(state: MusicSessionState)
}

val MUSIC_MIGRATION_5_6 = object : Migration(5,6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS music_stats (uri TEXT NOT NULL PRIMARY KEY, playCount INTEGER NOT NULL, lastPlayed INTEGER NOT NULL, favourite INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS music_playlists (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS music_playlist_items (playlistId TEXT NOT NULL, uri TEXT NOT NULL, sortIndex INTEGER NOT NULL, PRIMARY KEY(playlistId,uri))")
        db.execSQL("CREATE TABLE IF NOT EXISTS music_session (id INTEGER NOT NULL PRIMARY KEY, songsJson TEXT NOT NULL, currentIndex INTEGER NOT NULL, position INTEGER NOT NULL, shuffle INTEGER NOT NULL, repeatMode INTEGER NOT NULL)")
    }
}
