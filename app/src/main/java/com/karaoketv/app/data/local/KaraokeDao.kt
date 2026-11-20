package com.karaoketv.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class ArtistCountRow(
    val name: String,
    val styleHint: String,
    val songCount: Int
)

data class StyleCountRow(
    val name: String,
    val songCount: Int
)

data class VideoCodeRow(
    val videoId: String,
    val songCode: String
)

@Dao
interface KaraokeDao {

    // --- Queue Management (Local SQLite Only) ---

    @Query("SELECT * FROM queue_items WHERE status IN ('PLAYING', 'WAITING') ORDER BY CASE WHEN status = 'PLAYING' THEN 0 ELSE 1 END, position ASC")
    fun observeActiveQueue(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items WHERE status IN ('PLAYING', 'WAITING') ORDER BY CASE WHEN status = 'PLAYING' THEN 0 ELSE 1 END, position ASC")
    suspend fun getActiveQueueSnapshot(): List<QueueItemEntity>

    @Query("SELECT * FROM song_cache WHERE videoId = :videoId LIMIT 1")
    suspend fun getSongByVideoId(videoId: String): SongCacheEntity?

    @Query("SELECT * FROM queue_items WHERE status = 'PLAYING' LIMIT 1")
    fun observeCurrentPlaying(): Flow<QueueItemEntity?>

    @Query("SELECT COALESCE(MAX(position), 0) FROM queue_items")
    suspend fun getMaxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: QueueItemEntity): Long

    @Query("UPDATE queue_items SET status = :status WHERE id = :id")
    suspend fun updateQueueStatus(id: Long, status: String)

    @Query("UPDATE queue_items SET status = 'PLAYED' WHERE status = 'PLAYING'")
    suspend fun markAllPlayingAsPlayed()

    @Query("SELECT * FROM queue_items WHERE status = 'WAITING' ORDER BY position ASC LIMIT 1")
    suspend fun getNextWaitingItem(): QueueItemEntity?

    @Query("DELETE FROM queue_items WHERE id = :id")
    suspend fun removeFromQueue(id: Long)

    @Query("DELETE FROM queue_items WHERE status = 'WAITING'")
    suspend fun clearWaitingQueue()

    @Transaction
    suspend fun enqueueAtEnd(item: QueueItemEntity) {
        val nextPos = getMaxPosition() + 1
        insertQueueItem(item.copy(position = nextPos, status = QueueStatus.WAITING))
    }

    @Transaction
    suspend fun playImmediately(item: QueueItemEntity) {
        markAllPlayingAsPlayed()
        val nextPos = getMaxPosition() + 1
        insertQueueItem(item.copy(position = nextPos, status = QueueStatus.PLAYING))
    }

    @Transaction
    suspend fun advanceToNextSong(): QueueItemEntity? {
        markAllPlayingAsPlayed()
        val next = getNextWaitingItem() ?: return null
        updateQueueStatus(next.id, QueueStatus.PLAYING)
        return next.copy(status = QueueStatus.PLAYING)
    }

    @Transaction
    suspend fun playQueueItemNow(itemId: Long) {
        markAllPlayingAsPlayed()
        updateQueueStatus(itemId, QueueStatus.PLAYING)
    }

    // --- Song Cache / SQLite Catalog ---

    @Query("SELECT * FROM song_cache ORDER BY cachedAt DESC")
    fun observeAllCachedSongs(): Flow<List<SongCacheEntity>>

    @Query("SELECT COUNT(*) FROM song_cache")
    suspend fun countCachedSongs(): Int

    @Query("SELECT MAX(CAST(songCode AS INTEGER)) FROM song_cache WHERE songCode != ''")
    suspend fun getMaxSongCodeNumber(): Int?

    @Query("SELECT videoId, songCode FROM song_cache")
    suspend fun getAllVideoIdCodes(): List<VideoCodeRow>

    @Query("SELECT * FROM song_cache WHERE searchKey = :searchKey AND cachedAt >= :minTimestamp ORDER BY cachedAt DESC")
    suspend fun getCachedSongs(searchKey: String, minTimestamp: Long): List<SongCacheEntity>

    @Query(
        "SELECT * FROM song_cache WHERE songCode = :keyword OR songCode LIKE '%' || :keyword || '%' " +
            "OR title LIKE '%' || :keyword || '%' " +
            "OR artistName LIKE '%' || :keyword || '%' " +
            "OR styleTag LIKE '%' || :keyword || '%' " +
            "ORDER BY CASE WHEN songCode = :keyword THEN 0 ELSE 1 END, cachedAt DESC"
    )
    suspend fun searchLocalCache(keyword: String): List<SongCacheEntity>

    @Query(
        "SELECT artistName AS name, MAX(styleTag) AS styleHint, COUNT(*) AS songCount " +
            "FROM song_cache WHERE artistName != '' AND artistName != 'ศิลปินอื่นๆ' AND artistName != channelTitle " +
            "GROUP BY artistName ORDER BY songCount DESC"
    )
    fun observeArtistCounts(): Flow<List<ArtistCountRow>>

    @Query(
        "SELECT channelTitle AS name, MAX(styleTag) AS styleHint, COUNT(*) AS songCount " +
            "FROM song_cache WHERE channelTitle != '' " +
            "GROUP BY channelTitle ORDER BY songCount DESC LIMIT 60"
    )
    fun observeChannelCounts(): Flow<List<ArtistCountRow>>

    @Query("SELECT styleTag AS name, COUNT(*) AS songCount FROM song_cache GROUP BY styleTag ORDER BY songCount DESC")
    fun observeStyleCounts(): Flow<List<StyleCountRow>>

    @Query("SELECT eraTag AS name, COUNT(*) AS songCount FROM song_cache WHERE eraTag != '' GROUP BY eraTag ORDER BY songCount DESC")
    fun observeEraCounts(): Flow<List<StyleCountRow>>

    @Query("SELECT * FROM song_cache WHERE artistName = :artistName ORDER BY cachedAt DESC")
    suspend fun getSongsByArtist(artistName: String): List<SongCacheEntity>

    @Query("SELECT * FROM song_cache WHERE channelTitle = :channelTitle ORDER BY cachedAt DESC")
    suspend fun getSongsByChannel(channelTitle: String): List<SongCacheEntity>

    @Query("SELECT * FROM song_cache WHERE styleTag = :styleTag ORDER BY cachedAt DESC")
    suspend fun getSongsByStyle(styleTag: String): List<SongCacheEntity>

    @Query("SELECT * FROM song_cache WHERE eraTag = :eraTag ORDER BY cachedAt DESC")
    suspend fun getSongsByEra(eraTag: String): List<SongCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedSongs(songs: List<SongCacheEntity>)

    // --- Search History ---

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 10")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchHistory(entry: SearchHistoryEntity)

    // --- Favorites ---

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteSongEntity>>

    @Query("SELECT videoId FROM favorites")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getFavoritesSnapshot(): List<FavoriteSongEntity>

    @Query("SELECT COUNT(*) FROM favorites WHERE videoId = :videoId")
    suspend fun isFavorite(videoId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(item: FavoriteSongEntity)

    @Query("DELETE FROM favorites WHERE videoId = :videoId")
    suspend fun deleteFavorite(videoId: String)

    @Transaction
    suspend fun toggleFavorite(item: FavoriteSongEntity): Boolean {
        return if (isFavorite(item.videoId) > 0) {
            deleteFavorite(item.videoId)
            false
        } else {
            insertFavorite(item)
            true
        }
    }

    // --- Favorite Artists ---

    @Query("SELECT artistName FROM favorite_artists ORDER BY addedAt DESC")
    fun observeFavoriteArtistNames(): Flow<List<String>>

    @Query("SELECT artistName FROM favorite_artists ORDER BY addedAt DESC")
    suspend fun getFavoriteArtistNamesSnapshot(): List<String>

    @Query("SELECT * FROM favorite_artists ORDER BY addedAt DESC")
    suspend fun getFavoriteArtistsSnapshot(): List<FavoriteArtistEntity>

    @Query("SELECT COUNT(*) FROM favorite_artists WHERE artistName = :artistName")
    suspend fun isFavoriteArtist(artistName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteArtist(item: FavoriteArtistEntity)

    @Query("DELETE FROM favorite_artists WHERE artistName = :artistName")
    suspend fun deleteFavoriteArtist(artistName: String)

    @Transaction
    suspend fun toggleFavoriteArtist(item: FavoriteArtistEntity): Boolean {
        return if (isFavoriteArtist(item.artistName) > 0) {
            deleteFavoriteArtist(item.artistName)
            false
        } else {
            insertFavoriteArtist(item)
            true
        }
    }
}
