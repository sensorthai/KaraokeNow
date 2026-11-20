package com.karaoketv.app.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.karaoketv.app.BuildConfig
import com.karaoketv.app.data.local.KaraokeDao
import com.karaoketv.app.data.local.SearchHistoryEntity
import com.karaoketv.app.data.local.SongCacheEntity
import com.karaoketv.app.data.model.ArtistProfile
import com.karaoketv.app.data.model.ChannelProfile
import com.karaoketv.app.data.model.MusicStyle
import com.karaoketv.app.data.model.SearchFilter
import com.karaoketv.app.data.model.SongItem
import com.karaoketv.app.data.remote.YouTubeApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class YouTubeRepository(
    private val dao: KaraokeDao,
    private val api: YouTubeApiService = YouTubeApiService.create(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val cacheTtlMillis = TimeUnit.HOURS.toMillis(24)

    val musicStyles = listOf(
        MusicStyle("pop", "สตริง / ป๊อป", "เพลงสตริง ป๊อป คาราโอเกะ", 0xFF6366F1),
        MusicStyle("rock", "ร็อก", "เพลงร็อกไทย คาราโอเกะ", 0xFFEF4444),
        MusicStyle("lukthung", "ลูกทุ่ง / หมอลำ", "เพลงลูกทุ่ง คาราโอเกะ", 0xFFF59E0B),
        MusicStyle("90s", "ยุค 90s", "เพลงฮิตยุค 90 คาราโอเกะ", 0xFFEC4899),
        MusicStyle("phuea_chiwit", "เพื่อชีวิต", "เพลงเพื่อชีวิต คาราโอเกะ", 0xFF10B981),
        MusicStyle("inter", "สากล (Inter)", "English Pop Karaoke", 0xFF0EA5E9)
    )

    val featuredArtists = listOf(
        ArtistProfile("Bodyslam", "ร็อก", 0xFFEF4444),
        ArtistProfile("Three Man Down", "ป๊อป / ร็อก", 0xFF6366F1),
        ArtistProfile("ต่าย อรทัย", "ลูกทุ่ง", 0xFFF59E0B),
        ArtistProfile("พงษ์สิทธิ์ คำภีร์", "เพื่อชีวิต", 0xFF10B981),
        ArtistProfile("Potato", "ร็อก", 0xFF8B5CF6),
        ArtistProfile("Bruno Mars", "สากล", 0xFF0EA5E9)
    )

    fun observeCatalogSongs(): Flow<List<SongItem>> =
        dao.observeAllCachedSongs().map { entities ->
            if (entities.isEmpty()) demoCatalog else entities.map { it.toDomain() }
        }

    fun observeDynamicArtists(): Flow<List<ArtistProfile>> {
        val palette = listOf(0xFFEF4444, 0xFF6366F1, 0xFFF59E0B, 0xFF10B981, 0xFF8B5CF6, 0xFF0EA5E9)
        return dao.observeArtistCounts().map { rows ->
            if (rows.isEmpty()) featuredArtists
            else rows.mapIndexed { idx, row ->
                ArtistProfile(
                    name = row.name,
                    styleHint = row.styleHint,
                    avatarColorHex = palette[idx % palette.size],
                    songCount = row.songCount
                )
            }
        }
    }

    fun observeDynamicStyles(): Flow<List<MusicStyle>> {
        val palette = listOf(0xFF6366F1, 0xFFEF4444, 0xFFF59E0B, 0xFFEC4899, 0xFF10B981, 0xFF0EA5E9, 0xFF8B5CF6)
        return dao.observeStyleCounts().map { rows ->
            if (rows.isEmpty()) musicStyles
            else rows.mapIndexed { idx, row ->
                MusicStyle(
                    id = row.name.lowercase().replace(" ", "_"),
                    name = row.name,
                    queryKeyword = "${row.name} คาราโอเกะ",
                    accentColorHex = palette[idx % palette.size],
                    songCount = row.songCount
                )
            }
        }
    }

    fun observeDynamicEras(): Flow<List<MusicStyle>> {
        val palette = listOf(0xFFEC4899, 0xFFF59E0B, 0xFF10B981, 0xFF6366F1, 0xFF0EA5E9)
        return dao.observeEraCounts().map { rows ->
            rows.mapIndexed { idx, row ->
                MusicStyle(
                    id = row.name.lowercase().replace(" ", "_"),
                    name = row.name,
                    queryKeyword = row.name,
                    accentColorHex = palette[idx % palette.size],
                    songCount = row.songCount
                )
            }
        }
    }

    fun observeDynamicChannels(): Flow<List<ChannelProfile>> {
        val palette = listOf(0xFFEC4899, 0xFF6366F1, 0xFFF59E0B, 0xFF10B981, 0xFFEF4444, 0xFF0EA5E9)
        return dao.observeChannelCounts().map { rows ->
            rows.mapIndexed { idx, row ->
                ChannelProfile(
                    name = row.name,
                    styleHint = row.styleHint,
                    accentColorHex = palette[idx % palette.size],
                    songCount = row.songCount
                )
            }
        }
    }

    suspend fun getLocalSongsByArtist(artistName: String): List<SongItem> =
        dao.getSongsByArtist(artistName).map { it.toDomain() }

    suspend fun getLocalSongsByChannel(channelTitle: String): List<SongItem> =
        dao.getSongsByChannel(channelTitle).map { it.toDomain() }

    suspend fun getLocalSongsByStyle(styleTag: String): List<SongItem> =
        dao.getSongsByStyle(styleTag).map { it.toDomain() }

    suspend fun getLocalSongsByEra(eraTag: String): List<SongItem> =
        dao.getSongsByEra(eraTag).map { it.toDomain() }

    suspend fun hasCachedCatalog(): Boolean = dao.countCachedSongs() >= 20831

    suspend fun seedBundledCatalogIfNeeded(context: Context): Int = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("karaoke_prefs", Context.MODE_PRIVATE)
        val seedVersion = prefs.getInt("seed_version", 0)
        val existingCount = dao.countCachedSongs()
        if (existingCount >= 20831 && seedVersion >= 17) return@withContext existingCount

        runCatching {
            val json = context.assets.open("karaoke_seed.json")
                .bufferedReader()
                .use { it.readText() }
            val listType = object : TypeToken<List<SongItem>>() {}.type
            val songs: List<SongItem> = Gson().fromJson(json, listType)
            var nextCode = (dao.getMaxSongCodeNumber() ?: 0)
            val existingCodes = dao.getAllVideoIdCodes().associate { it.videoId to it.songCode }
            val entities = songs.map { item ->
                val existingCode = existingCodes[item.videoId]
                val code = existingCode?.takeIf { it.isNotBlank() }
                    ?: item.songCode.takeIf { it.isNotBlank() }
                    ?: run {
                        nextCode += 1
                        String.format("%05d", nextCode)
                    }
                item.copy(songCode = code).toCacheEntity("SEED:${item.styleTag.lowercase()}")
            }
            entities.chunked(500).forEach { batch ->
                dao.insertCachedSongs(batch)
            }
            prefs.edit().putInt("seed_version", 17).apply()
            importExternalCustomSongsIfPresent()
            dao.countCachedSongs()
        }.getOrDefault(existingCount)
    }

    suspend fun importSongsToCache(songs: List<SongItem>): Int = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext 0
        var nextCode = dao.getMaxSongCodeNumber() ?: 0
        val existingCodes = dao.getAllVideoIdCodes().associate { it.videoId to it.songCode }
        val entities = songs.map { item ->
            val existingCode = existingCodes[item.videoId]
            val code = existingCode?.takeIf { it.isNotBlank() }
                ?: item.songCode.takeIf { it.isNotBlank() }
                ?: run {
                    nextCode += 1
                    String.format("%05d", nextCode)
                }
            val thumb = item.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg" }
            item.copy(songCode = code, thumbnailUrl = thumb)
                .toCacheEntity("IMPORT:${item.styleTag.ifBlank { "สตริง / ป๊อป" }.lowercase()}")
        }
        entities.chunked(500).forEach { batch ->
            dao.insertCachedSongs(batch)
        }
        entities.size
    }

    suspend fun importExternalCustomSongsIfPresent(): Int = withContext(Dispatchers.IO) {
        runCatching {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val file = File(File(docsDir, "KaraokeNow"), "custom_songs.json")
            if (!file.exists() || !file.isFile) return@withContext 0
            val json = file.readText(Charsets.UTF_8)
            val listType = object : TypeToken<List<SongItem>>() {}.type
            val songs: List<SongItem> = Gson().fromJson(json, listType) ?: return@withContext 0
            importSongsToCache(songs)
        }.getOrDefault(0)
    }

    suspend fun syncCatalogFromYouTube(): Result<Int> = runCatching {
        val syncQueries = listOf(
            Triple("เพลงฮิต คาราโอเกะ 2026", SearchFilter.ALL, "ฮิต 2026"),
            Triple("เพลงฮิต คาราโอเกะ 2025", SearchFilter.ALL, "ฮิต 2025"),
            Triple("เพลงฮิต คาราโอเกะ 2024", SearchFilter.ALL, "ฮิต 2024"),
            Triple("เพลงฮิต คาราโอเกะ 2023", SearchFilter.ALL, "ฮิต 2023"),
            Triple("เพลงฮิต คาราโอเกะ 2022", SearchFilter.ALL, "ฮิต 2022"),
            Triple("เพลงลูกทุ่ง คาราโอเกะ ฮิต 2022 2023 2024 2025", SearchFilter.STYLE, "ลูกทุ่ง / หมอลำ"),
            Triple("เพลงร็อกไทย คาราโอเกะ 2022 2023 2024 2025", SearchFilter.STYLE, "ร็อก"),
            Triple("เพลงสตริง คาราโอเกะ 2022 2023 2024 2025", SearchFilter.STYLE, "สตริง / ป๊อป"),
            Triple("เพลงเพื่อชีวิต คาราโอเกะ", SearchFilter.STYLE, "เพื่อชีวิต"),
            Triple("English Pop Karaoke 2022 2023 2024 2025", SearchFilter.STYLE, "สากล (Inter)")
        )

        val counts = coroutineScope {
            syncQueries.map { (query, filter, styleTag) ->
                async(Dispatchers.IO) {
                    val result = fetchSongsFromYouTube(query, filter, styleTag)
                    result.onFailure { e ->
                        Log.e("YouTubeSync", "Failed query=$query", e)
                    }
                    val songs = result.getOrDefault(emptyList()).take(20)
                    Log.d("YouTubeSync", "Fetched ${songs.size} songs for $query")
                    if (songs.isNotEmpty()) {
                        val searchKey = "SYNC:${styleTag.lowercase()}"
                        val withCodes = assignUniqueSongCodes(songs)
                        dao.insertCachedSongs(withCodes.map { it.toCacheEntity(searchKey) })
                    }
                    songs.size
                }
            }.awaitAll()
        }
        counts.sum()
    }

    suspend fun searchSongs(
        rawQuery: String,
        filter: SearchFilter,
        saveHistory: Boolean = true
    ): Result<List<SongItem>> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            val local = dao.searchLocalCache("")
            return Result.success(if (local.isNotEmpty()) local.map { it.toDomain() } else demoCatalog)
        }

        if (saveHistory) {
            dao.upsertSearchHistory(
                SearchHistoryEntity(query = trimmed, filterType = filter.name)
            )
        }

        val searchKey = "${filter.name}:${trimmed.lowercase()}"
        val minCacheTime = System.currentTimeMillis() - cacheTtlMillis
        val cached = dao.getCachedSongs(searchKey, minCacheTime)
        if (cached.isNotEmpty()) {
            return Result.success(cached.map { it.toDomain() })
        }

        val formattedQuery = buildYouTubeQuery(trimmed, filter)
        val styleLabel = if (filter == SearchFilter.STYLE) trimmed else "Karaoke"

        return fetchSongsFromYouTube(formattedQuery, filter, styleLabel)
            .map { songs ->
                if (songs.isNotEmpty()) {
                    val withCodes = assignUniqueSongCodes(songs)
                    dao.insertCachedSongs(withCodes.map { it.toCacheEntity(searchKey) })
                    withCodes
                } else {
                    fallbackLocalOrDemo(trimmed, filter)
                }
            }
            .recoverCatching {
                fallbackLocalOrDemo(trimmed, filter)
            }
    }

    private suspend fun fetchSongsFromYouTube(
        formattedQuery: String,
        filter: SearchFilter,
        styleTag: String
    ): Result<List<SongItem>> {
        val apiKey = BuildConfig.YOUTUBE_API_KEY
        if (apiKey.isNotBlank()) {
            val apiResult = runCatching {
                val response = api.searchKaraokeVideos(
                    query = formattedQuery,
                    apiKey = apiKey
                )
                response.items.mapNotNull { item ->
                    val videoId = item.id.videoId ?: return@mapNotNull null
                    val thumb = item.snippet.thumbnails?.high?.url
                        ?: item.snippet.thumbnails?.medium?.url
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    SongItem(
                        videoId = videoId,
                        title = item.snippet.title,
                        artistName = item.snippet.channelTitle,
                        styleTag = styleTag,
                        thumbnailUrl = thumb,
                        channelTitle = item.snippet.channelTitle
                    )
                }
            }
            if (apiResult.isSuccess && apiResult.getOrNull()?.isNotEmpty() == true) {
                return apiResult
            }
        }

        return runCatching {
            fetchYouTubeWebSearch(formattedQuery, styleTag)
        }
    }

    private suspend fun fetchYouTubeWebSearch(
        query: String,
        styleTag: String
    ): List<SongItem> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.youtube.com/results?search_query=$encoded"
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            .header("Accept-Language", "th-TH,th;q=0.9,en;q=0.8")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val html = response.body?.string() ?: return@withContext emptyList()
            val jsonText = extractYtInitialData(html) ?: return@withContext emptyList()
            val root = JsonParser.parseString(jsonText)
            val collected = mutableListOf<SongItem>()
            collectVideoRenderers(root, styleTag, collected)
            collected.distinctBy { it.videoId }.take(25)
        }
    }

    private fun extractYtInitialData(html: String): String? {
        val marker = "var ytInitialData = "
        val startIdx = html.indexOf(marker)
        if (startIdx == -1) return null
        val jsonStart = startIdx + marker.length
        val endIdx = html.indexOf(";</script>", jsonStart)
        if (endIdx == -1) return null
        return html.substring(jsonStart, endIdx)
    }

    private fun collectVideoRenderers(
        element: JsonElement,
        styleTag: String,
        out: MutableList<SongItem>
    ) {
        if (out.size >= 25) return
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                if (obj.has("videoRenderer")) {
                    parseVideoRenderer(obj.getAsJsonObject("videoRenderer"), styleTag)?.let {
                        out.add(it)
                    }
                    return
                }
                for (entry in obj.entrySet()) {
                    collectVideoRenderers(entry.value, styleTag, out)
                }
            }
            element.isJsonArray -> {
                for (child in element.asJsonArray) {
                    collectVideoRenderers(child, styleTag, out)
                }
            }
        }
    }

    private fun parseVideoRenderer(renderer: JsonObject, styleTag: String): SongItem? {
        val videoId = renderer.get("videoId")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val title = renderer.getAsJsonObject("title")
            ?.getAsJsonArray("runs")
            ?.firstOrNull()
            ?.asJsonObject
            ?.get("text")
            ?.asString
            ?: return null
        val channel = renderer.getAsJsonObject("ownerText")
            ?.getAsJsonArray("runs")
            ?.firstOrNull()
            ?.asJsonObject
            ?.get("text")
            ?.asString
            ?: "YouTube Karaoke"
        return SongItem(
            videoId = videoId,
            title = title,
            artistName = channel,
            styleTag = styleTag,
            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            channelTitle = channel
        )
    }

    private fun buildYouTubeQuery(query: String, filter: SearchFilter): String {
        val hasKaraokeWord = query.contains("คาราโอเกะ", ignoreCase = true) ||
            query.contains("karaoke", ignoreCase = true)
        val suffix = if (hasKaraokeWord) "" else " คาราโอเกะ"
        return when (filter) {
            SearchFilter.ALL -> "$query$suffix"
            SearchFilter.TITLE -> "$query$suffix"
            SearchFilter.ARTIST -> "$query เพลงฮิต$suffix"
            SearchFilter.STYLE -> "$query$suffix"
        }
    }

    private suspend fun fallbackLocalOrDemo(query: String, filter: SearchFilter): List<SongItem> {
        val local = dao.searchLocalCache(query).map { it.toDomain() }
        if (local.isNotEmpty()) return local
        val q = query.lowercase()
        val matched = demoCatalog.filter { song ->
            when (filter) {
                SearchFilter.ALL ->
                    song.title.lowercase().contains(q) ||
                        song.artistName.lowercase().contains(q) ||
                        song.styleTag.lowercase().contains(q)
                SearchFilter.TITLE -> song.title.lowercase().contains(q)
                SearchFilter.ARTIST -> song.artistName.lowercase().contains(q)
                SearchFilter.STYLE -> song.styleTag.lowercase().contains(q)
            }
        }
        return matched.ifEmpty { demoCatalog }
    }

    private suspend fun assignUniqueSongCodes(songs: List<SongItem>): List<SongItem> {
        var nextCode = dao.getMaxSongCodeNumber() ?: 0
        return songs.map { item ->
            val existing = dao.getSongByVideoId(item.videoId)
            val code = existing?.songCode?.takeIf { it.isNotBlank() }
                ?: item.songCode.takeIf { it.isNotBlank() }
                ?: run {
                    nextCode += 1
                    String.format("%05d", nextCode)
                }
            item.copy(songCode = code)
        }
    }

    private fun SongCacheEntity.toDomain() = SongItem(
        videoId = videoId,
        title = title,
        artistName = artistName,
        styleTag = styleTag,
        thumbnailUrl = thumbnailUrl,
        channelTitle = channelTitle,
        songCode = songCode,
        eraTag = eraTag
    )

    private fun SongItem.toCacheEntity(searchKey: String) = SongCacheEntity(
        videoId = videoId,
        title = title,
        artistName = artistName,
        styleTag = styleTag,
        thumbnailUrl = thumbnailUrl,
        channelTitle = channelTitle,
        searchKey = searchKey,
        songCode = songCode,
        eraTag = eraTag
    )

    companion object {
        val demoCatalog = listOf(
            SongItem("J_9B4mfqe2s", "แสงสุดท้าย (Karaoke)", "Bodyslam", "ร็อก", "https://i.ytimg.com/vi/J_9B4mfqe2s/hqdefault.jpg", "GMM Karaoke"),
            SongItem("1MK1E3aelHQ", "ฝนตกไหม (Karaoke)", "Three Man Down", "สตริง / ป๊อป", "https://i.ytimg.com/vi/1MK1E3aelHQ/hqdefault.jpg", "GeneLab Karaoke"),
            SongItem("fJ9rUzIMcZQ", "ดอกหญ้าในป่าปูน (Karaoke)", "ต่าย อรทัย", "ลูกทุ่ง / หมอลำ", "https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg", "Grammy Gold Karaoke"),
            SongItem("9bZkp7q19f0", "เสมอ (Karaoke)", "พงษ์สิทธิ์ คำภีร์", "เพื่อชีวิต", "https://i.ytimg.com/vi/9bZkp7q19f0/hqdefault.jpg", "Warner Music Karaoke"),
            SongItem("JGwWNGJdvx8", "ทิ้งไว้กลางทาง (Karaoke)", "Potato", "ร็อก", "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg", "GMM Karaoke"),
            SongItem("OPf0YbXqDm0", "Uptown Funk (Karaoke)", "Bruno Mars", "สากล (Inter)", "https://i.ytimg.com/vi/OPf0YbXqDm0/hqdefault.jpg", "Sing King Karaoke"),
            SongItem("CevxZvSJLk8", "ซมซาน (Karaoke)", "Loso", "ยุค 90s", "https://i.ytimg.com/vi/CevxZvSJLk8/hqdefault.jpg", "GMM Karaoke"),
            SongItem("RgKAFK5djSk", "วัดปะหล่ะ? (Karaoke)", "4EVE", "สตริง / ป๊อป", "https://i.ytimg.com/vi/RgKAFK5djSk/hqdefault.jpg", "XOXO Karaoke")
        )
    }
}
