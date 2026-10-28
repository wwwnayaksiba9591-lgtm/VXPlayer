package com.vxplayer.player

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "track_preferences")
data class TrackPreference(@PrimaryKey val uri: String, val audioId: String? = null, val audioLanguage: String? = null,
    val textId: String? = null, val textLanguage: String? = null, val textDisabled: Boolean = false,
    val externalUri: String? = null, val externalName: String? = null)

@Dao interface TrackPreferenceDao {
    @Query("SELECT * FROM track_preferences WHERE uri = :uri") suspend fun load(uri: String): TrackPreference?
    @Upsert suspend fun save(preference: TrackPreference)
}

val PLAYBACK_MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `track_preferences` (`uri` TEXT NOT NULL, `audioId` TEXT, `audioLanguage` TEXT, `textId` TEXT, `textLanguage` TEXT, `textDisabled` INTEGER NOT NULL, `externalUri` TEXT, `externalName` TEXT, PRIMARY KEY(`uri`))")
    }
}

fun preferredTrackIndex(tracks: List<Pair<String?, String?>>, id: String?, language: String?): Int {
    val exact = if (id != null) tracks.indexOfFirst { it.first == id } else -1
    return if (exact >= 0) exact else if (!language.isNullOrBlank()) tracks.indexOfFirst { it.second == language } else -1
}
