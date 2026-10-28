package com.vxplayer.player

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "recently_played")
data class RecentlyPlayed(@PrimaryKey val uri: String, val title: String, val playedAt: Long)

@Dao
interface RecentlyPlayedDao {
    @Query("DELETE FROM recently_played") suspend fun clear()
    @Upsert
    suspend fun upsert(video: RecentlyPlayed)

    @Query("DELETE FROM recently_played WHERE uri NOT IN (SELECT uri FROM recently_played ORDER BY playedAt DESC, uri ASC LIMIT 20)")
    suspend fun trim()

    @Query("SELECT * FROM recently_played ORDER BY playedAt DESC, uri ASC LIMIT 20")
    suspend fun recent(): List<RecentlyPlayed>

    @Transaction
    suspend fun record(video: RecentlyPlayed) {
        upsert(video)
        trim()
    }
}

val PLAYBACK_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `recently_played` (`uri` TEXT NOT NULL, `title` TEXT NOT NULL, `playedAt` INTEGER NOT NULL, PRIMARY KEY(`uri`))")
    }
}
