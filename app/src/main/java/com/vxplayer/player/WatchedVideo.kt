package com.vxplayer.player

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "watched_videos")
data class WatchedVideo(@PrimaryKey val uri: String)

@Dao
interface WatchedVideoDao {
    @Upsert suspend fun save(video: WatchedVideo)
    @Query("SELECT * FROM watched_videos") suspend fun all(): List<WatchedVideo>
    @Query("DELETE FROM watched_videos") suspend fun clear()
}

data class PlaybackSnapshot(val positions: Map<String, Long> = emptyMap(), val watched: Set<String> = emptySet())

val PLAYBACK_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `watched_videos` (`uri` TEXT NOT NULL, PRIMARY KEY(`uri`))")
    }
}
