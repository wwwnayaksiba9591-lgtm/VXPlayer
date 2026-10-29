package com.vxplayer.network

import android.content.Context
import android.content.SharedPreferences
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vxplayer.library.StreamUrls
import com.vxplayer.player.PlaybackDatabase
import com.vxplayer.player.PlaybackPositionStore
import com.vxplayer.player.TrackPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val NETWORK_LINKS_CREATE = "CREATE TABLE IF NOT EXISTS `network_links` (`url` TEXT NOT NULL, `name` TEXT NOT NULL, `favourite` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, `streamType` TEXT NOT NULL, `subtitleUri` TEXT, `subtitleName` TEXT, PRIMARY KEY(`url`))"

private fun importLegacy(db: SupportSQLiteDatabase, prefs: SharedPreferences) {
    legacyNetworkLinks(prefs.getString("recentStreamUrls", "[]")) { prefs.getString("streamType:$it", "Auto") ?: "Auto" }.forEach {
        db.execSQL("INSERT OR IGNORE INTO network_links (url, name, favourite, lastPlayedAt, streamType, subtitleUri, subtitleName) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(it.url, it.name, 0, it.lastPlayedAt, it.streamType, null, null))
    }
}

class NetworkMigration(private val prefs: SharedPreferences) : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) { db.execSQL(NETWORK_LINKS_CREATE); importLegacy(db, prefs) }
}
class NetworkCreation(private val prefs: SharedPreferences) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) { importLegacy(db, prefs) }
}

class NetworkRepository(context: Context) {
    private val app = context.applicationContext
    private val db = PlaybackDatabase.get(app)
    private val dao = db.networkLinks()
    private val prefs = app.getSharedPreferences("vxplayer", 0)
    val links = dao.observe()

    private suspend fun mutate(change: (List<NetworkLink>) -> List<NetworkLink>) = db.withTransaction {
        val changed = change(dao.all())
        dao.clear(); dao.saveAll(changed)
    }
    suspend fun played(input: NetworkLink, replaceAttachment: Boolean): NetworkLink = withContext(Dispatchers.IO) {
        val url = StreamUrls.validate(input.url)
        var selected = input.copy(url = url)
        db.withTransaction {
            val old = dao.all()
            val previous = old.firstOrNull { it.url == url }
            if (!replaceAttachment && previous != null) selected = selected.copy(subtitleUri = previous.subtitleUri, subtitleName = previous.subtitleName)
            val updated = recordNetworkPlay(old, selected, System.currentTimeMillis())
            selected = updated.first { it.url == url }
            dao.clear(); dao.saveAll(updated)
        }
        check(prefs.edit().putString("streamType:$url", selected.streamType).commit()) { "Could not save stream access." }
        val positions = PlaybackPositionStore.get(app)
        val choice = positions.loadChoice(url) ?: TrackPreference(url)
        positions.saveChoice(choice.copy(externalUri = selected.subtitleUri, externalName = selected.subtitleName,
            textId = if (selected.subtitleUri != null) "external-srt" else null, textDisabled = false))
        selected
    }
    suspend fun favourite(input: NetworkLink, name: String) = mutate { old ->
        val url = StreamUrls.validate(input.url)
        val previous = old.firstOrNull { it.url == url }
        val link = input.copy(url = url, lastPlayedAt = previous?.lastPlayedAt ?: 0,
            subtitleUri = input.subtitleUri ?: previous?.subtitleUri, subtitleName = input.subtitleName ?: previous?.subtitleName)
        listOf(link.copy(name = name.trim().take(120), favourite = true)) + old.filter { it.url != url }
    }
    suspend fun removeRecent(url: String) = mutate { old -> old.mapNotNull {
        if (it.url != url) it else if (it.favourite) it.copy(lastPlayedAt = 0) else null
    } }
    suspend fun unfavourite(url: String) = mutate { old -> old.mapNotNull {
        if (it.url != url) it else if (it.lastPlayedAt > 0) it.copy(favourite = false) else null
    } }
    suspend fun clearRecent() = mutate { old -> old.filter { it.favourite }.map { it.copy(lastPlayedAt = 0) } }
    suspend fun subtitle(url: String, uri: String, name: String) = db.withTransaction {
        dao.find(url)?.let { dao.save(it.copy(subtitleUri = uri, subtitleName = name)) }
    }
}
