package com.vxplayer.network

import androidx.room.*
import com.google.gson.JsonParser
import com.vxplayer.library.StreamUrls
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "network_links")
data class NetworkLink(@PrimaryKey val url: String, val name: String = "", val favourite: Boolean = false,
    val lastPlayedAt: Long = 0, val streamType: String = "Auto", val subtitleUri: String? = null, val subtitleName: String? = null) {
    val title get() = name.ifBlank { StreamUrls.title(url) }
}

@Dao
interface NetworkLinkDao {
    @Query("SELECT * FROM network_links ORDER BY lastPlayedAt DESC, url ASC") fun observe(): Flow<List<NetworkLink>>
    @Query("SELECT * FROM network_links") suspend fun all(): List<NetworkLink>
    @Query("SELECT * FROM network_links WHERE url = :url") suspend fun find(url: String): NetworkLink?
    @Query("DELETE FROM network_links") suspend fun clear()
    @Upsert suspend fun saveAll(links: List<NetworkLink>)
    @Upsert suspend fun save(link: NetworkLink)
}

fun recentNetworkLinks(links: List<NetworkLink>) = links.filter { it.lastPlayedAt > 0 }
    .distinctBy { it.url }.sortedWith(compareByDescending<NetworkLink> { it.lastPlayedAt }.thenBy { it.url }).take(20)

fun recordNetworkPlay(previous: List<NetworkLink>, opened: NetworkLink, now: Long): List<NetworkLink> {
    val url = StreamUrls.validate(opened.url)
    val old = previous.firstOrNull { it.url == url }
    val latest = previous.maxOfOrNull { it.lastPlayedAt } ?: 0L
    val fresh = opened.copy(url = url, name = opened.name.ifBlank { old?.name.orEmpty() },
        favourite = opened.favourite || old?.favourite == true, lastPlayedAt = maxOf(now, latest + 1))
    val all = listOf(fresh) + previous.filter { it.url != url }.distinctBy { it.url }
    val recent = recentNetworkLinks(all).map { it.url }.toSet()
    return all.mapNotNull { if (it.url in recent) it else if (it.favourite) it.copy(lastPlayedAt = 0) else null }
}

/** Malformed individual legacy entries do not discard other valid links. */
fun legacyNetworkLinks(json: String?, type: (String) -> String = { "Auto" }): List<NetworkLink> {
    val array = runCatching { JsonParser.parseString(json ?: "[]").asJsonArray }.getOrNull() ?: return emptyList()
    return array.take(1000).mapNotNull { value ->
        runCatching { StreamUrls.validate(value.asString) }.getOrNull()
    }.distinct().take(20).mapIndexed { index, url ->
        NetworkLink(url, lastPlayedAt = (20 - index).toLong(), streamType = runCatching { type(url) }.getOrDefault("Auto").takeIf { it in listOf("Auto", "HLS", "DASH") } ?: "Auto")
    }
}
