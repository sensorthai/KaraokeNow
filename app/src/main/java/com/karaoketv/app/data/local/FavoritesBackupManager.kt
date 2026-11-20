package com.karaoketv.app.data.local

import android.os.Environment
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class FavoritesBackupPayload(
    val favoriteSongs: List<FavoriteSongEntity> = emptyList(),
    val favoriteArtists: List<FavoriteArtistEntity> = emptyList()
)

object FavoritesBackupManager {
    private val gson = Gson()

    private fun backupFile(): File {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir = File(docs, "KaraokeNow")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "favorites_backup.json")
    }

    suspend fun saveBackup(dao: KaraokeDao) = withContext(Dispatchers.IO) {
        runCatching {
            val payload = FavoritesBackupPayload(
                favoriteSongs = dao.getFavoritesSnapshot(),
                favoriteArtists = dao.getFavoriteArtistsSnapshot()
            )
            backupFile().writeText(gson.toJson(payload), Charsets.UTF_8)
        }
    }

    suspend fun restoreIfEmpty(dao: KaraokeDao) = withContext(Dispatchers.IO) {
        runCatching {
            val file = backupFile()
            if (!file.exists()) return@runCatching
            val payload = gson.fromJson(file.readText(Charsets.UTF_8), FavoritesBackupPayload::class.java)
                ?: return@runCatching
            if (dao.getFavoritesSnapshot().isEmpty() && payload.favoriteSongs.isNotEmpty()) {
                payload.favoriteSongs.forEach { dao.insertFavorite(it) }
            }
            if (dao.getFavoriteArtistNamesSnapshot().isEmpty() && payload.favoriteArtists.isNotEmpty()) {
                payload.favoriteArtists.forEach { dao.insertFavoriteArtist(it) }
            }
        }
    }
}
