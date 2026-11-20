package com.karaoketv.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

object QueueStatus {
    const val WAITING = "WAITING"
    const val PLAYING = "PLAYING"
    const val PLAYED = "PLAYED"
}

@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: String,
    val title: String,
    val artistName: String,
    val styleTag: String,
    val thumbnailUrl: String,
    val position: Int,
    val status: String = QueueStatus.WAITING,
    val addedAt: Long = System.currentTimeMillis(),
    val songCode: String = ""
)

@Entity(tableName = "song_cache")
data class SongCacheEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val artistName: String,
    val styleTag: String,
    val thumbnailUrl: String,
    val channelTitle: String,
    val searchKey: String,
    val cachedAt: Long = System.currentTimeMillis(),
    val songCode: String = "",
    val eraTag: String = ""
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey
    val query: String,
    val filterType: String,
    val searchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteSongEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val artistName: String,
    val styleTag: String,
    val thumbnailUrl: String,
    val channelTitle: String,
    val addedAt: Long = System.currentTimeMillis(),
    val songCode: String = ""
)

@Entity(tableName = "favorite_artists")
data class FavoriteArtistEntity(
    @PrimaryKey
    val artistName: String,
    val styleHint: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "play_stats")
data class PlayStatEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val artistName: String,
    val styleTag: String,
    val thumbnailUrl: String,
    val songCode: String = "",
    val playCount: Int = 1,
    val lastPlayedAt: Long = System.currentTimeMillis()
)
