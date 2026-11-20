package com.karaoketv.app.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.karaoketv.app.BuildConfig
import com.karaoketv.app.ads.AdConfigRepository
import com.karaoketv.app.data.model.SongItem
import com.karaoketv.app.data.local.FavoriteArtistEntity
import com.karaoketv.app.data.local.FavoriteSongEntity
import com.karaoketv.app.data.local.FavoritesBackupManager
import com.karaoketv.app.data.local.KaraokeDao
import com.karaoketv.app.data.local.QueueItemEntity
import com.karaoketv.app.data.model.SearchFilter
import com.karaoketv.app.data.repository.YouTubeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

class LocalKaraokeServer(
    private val dao: KaraokeDao,
    private val repository: YouTubeRepository,
    private val onPlayTriggeredFromWeb: () -> Unit,
    private val onNextTriggeredFromWeb: (QueueItemEntity) -> Unit = {},
    private val port: Int = 8888
) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        scope.launch {
            runCatching {
                serverSocket = ServerSocket(port)
                while (running) {
                    val client = serverSocket?.accept() ?: break
                    launch { handleClient(client) }
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    fun getServerUrl(): String {
        val ip = getLocalIpAddress() ?: "127.0.0.1"
        return "http://$ip:$port"
    }

    private fun readHeaderLine(input: BufferedInputStream): String? {
        val baos = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (baos.size() == 0) null else baos.toString("UTF-8")
            }
            if (b == '\n'.code) break
            if (b != '\r'.code) baos.write(b)
        }
        return baos.toString("UTF-8")
    }

    private fun handleClient(socket: Socket) {
        socket.use { sock ->
            val input = BufferedInputStream(sock.getInputStream())
            val requestLine = readHeaderLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val fullPath = parts[1]

            var contentLength = 0
            while (true) {
                val line = readHeaderLine(input) ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val body = if (contentLength > 0) {
                val buf = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = input.read(buf, read, contentLength - read)
                    if (r == -1) break
                    read += r
                }
                String(buf, 0, read, Charsets.UTF_8)
            } else ""

            val path = fullPath.substringBefore("?")
            val queryParams = parseQueryParams(fullPath.substringAfter("?", ""))

            val (status, contentType, responseText) = when {
                method == "GET" && path == "/" -> {
                    Triple("200 OK", "text/html; charset=utf-8", MOBILE_WEB_APP_HTML)
                }
                method == "GET" && path == "/api/ad-config" -> {
                    Triple("200 OK", "application/json; charset=utf-8", gson.toJson(AdConfigRepository.currentConfig.remoteBanner))
                }
                method == "GET" && path == "/api/stats" -> {
                    val totalSongs = runBlocking { dao.countCachedSongs() }
                    Triple("200 OK", "application/json; charset=utf-8", """{"version":"${BuildConfig.VERSION_NAME}","totalSongs":$totalSongs}""")
                }
                method == "POST" && path == "/api/catalog/import" -> {
                    val listType = object : TypeToken<List<SongItem>>() {}.type
                    val songs: List<SongItem> = runCatching {
                        gson.fromJson<List<SongItem>>(body, listType)
                    }.getOrNull() ?: emptyList()
                    var imported = 0
                    var total = 0
                    runBlocking {
                        imported = repository.importSongsToCache(songs)
                        total = dao.countCachedSongs()
                    }
                    Triple("200 OK", "application/json; charset=utf-8", """{"ok":true,"imported":$imported,"total":$total}""")
                }
                method == "GET" && path == "/api/songs" -> {
                    val q = queryParams["q"]?.trim().orEmpty()
                    val artist = queryParams["artist"]?.trim().orEmpty()
                    val channel = queryParams["channel"]?.trim().orEmpty()
                    val style = queryParams["style"]?.trim().orEmpty()
                    val era = queryParams["era"]?.trim().orEmpty()
                    val songs = runBlocking {
                        when {
                            artist.isNotEmpty() -> repository.getLocalSongsByArtist(artist)
                            channel.isNotEmpty() -> repository.getLocalSongsByChannel(channel)
                            style.isNotEmpty() -> repository.getLocalSongsByStyle(style)
                            era.isNotEmpty() -> repository.getLocalSongsByEra(era)
                            q.isNotEmpty() -> {
                                val local = dao.searchLocalCache(q)
                                if (local.isNotEmpty()) {
                                    local.take(60).map {
                                        mapOf(
                                            "videoId" to it.videoId,
                                            "title" to it.title,
                                            "artistName" to it.artistName,
                                            "styleTag" to it.styleTag,
                                            "channelTitle" to it.channelTitle,
                                            "thumbnailUrl" to it.thumbnailUrl,
                                            "songCode" to it.songCode
                                        )
                                    }
                                } else {
                                    repository.searchSongs(q, SearchFilter.ALL, saveHistory = false)
                                        .getOrDefault(emptyList())
                                }
                            }
                            else -> repository.observeCatalogSongs().first().take(60)
                        }
                    }
                    Triple("200 OK", "application/json; charset=utf-8", gson.toJson(songs))
                }
                method == "GET" && path == "/api/artists" -> {
                    val artists = runBlocking { dao.observeArtistCounts().first() }
                    Triple("200 OK", "application/json; charset=utf-8", gson.toJson(artists))
                }
                method == "GET" && path == "/api/channels" -> {
                    val channels = runBlocking { dao.observeChannelCounts().first() }
                    Triple("200 OK", "application/json; charset=utf-8", gson.toJson(channels))
                }
                method == "GET" && path == "/api/styles" -> {
                    val styles = runBlocking { dao.observeStyleCounts().first() }
                    Triple("200 OK", "application/json; charset=utf-8", gson.toJson(styles))
                }
                method == "GET" && path == "/api/eras" -> {
                    val eras = runBlocking { dao.observeEraCounts().first() }
                    Triple("200 OK", "application/json; charset=utf-8", gson.toJson(eras))
                }
                method == "GET" && path == "/api/favorites" -> {
                    val favs = runBlocking { dao.getFavoritesSnapshot() }
                    Triple("200 OK", "application/json; charset=utf-8", gson.toJson(favs))
                }
                method == "POST" && path == "/api/favorites/toggle" -> {
                    val payload = runCatching { gson.fromJson(body, Map::class.java) }.getOrNull()
                    val videoId = payload?.get("videoId")?.toString().orEmpty()
                    var added = false
                    if (videoId.isNotEmpty()) {
                        runBlocking {
                            val cached = dao.getSongByVideoId(videoId)
                            added = dao.toggleFavorite(
                                FavoriteSongEntity(
                                    videoId = videoId,
                                    title = cached?.title ?: payload?.get("title")?.toString().orEmpty(),
                                    artistName = cached?.artistName ?: payload?.get("artistName")?.toString().orEmpty(),
                                    styleTag = cached?.styleTag ?: payload?.get("styleTag")?.toString().orEmpty(),
                                    thumbnailUrl = cached?.thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                    channelTitle = cached?.channelTitle ?: "",
                                    songCode = cached?.songCode ?: ""
                                )
                            )
                            FavoritesBackupManager.saveBackup(dao)
                        }
                    }
                    Triple("200 OK", "application/json; charset=utf-8", """{"ok":true,"added":$added}""")
                }
                method == "GET" && path == "/api/favorite-artists" -> {
                    val favArtists = runBlocking { dao.getFavoriteArtistNamesSnapshot() }
                    Triple("200 OK", "application/json; charset=utf-8", gson.toJson(favArtists))
                }
                method == "POST" && path == "/api/favorite-artists/toggle" -> {
                    val payload = runCatching { gson.fromJson(body, Map::class.java) }.getOrNull()
                    val artistName = payload?.get("artistName")?.toString().orEmpty()
                    val styleHint = payload?.get("styleHint")?.toString().orEmpty()
                    var added = false
                    if (artistName.isNotEmpty()) {
                        runBlocking {
                            added = dao.toggleFavoriteArtist(
                                FavoriteArtistEntity(artistName = artistName, styleHint = styleHint)
                            )
                            FavoritesBackupManager.saveBackup(dao)
                        }
                    }
                    Triple("200 OK", "application/json; charset=utf-8", """{"ok":true,"added":$added}""")
                }
                method == "GET" && path == "/api/queue" -> {
                    val queue = runBlocking { dao.getActiveQueueSnapshot() }
                    Triple("200 OK", "application/json; charset=utf-8", gson.toJson(queue))
                }
                method == "POST" && (path == "/api/queue/add" || path == "/api/queue/play") -> {
                    val payload = runCatching { gson.fromJson(body, Map::class.java) }.getOrNull()
                    val videoId = payload?.get("videoId")?.toString().orEmpty()
                    if (videoId.isNotEmpty()) {
                        runBlocking {
                            val cached = dao.getSongByVideoId(videoId)
                            val entity = QueueItemEntity(
                                videoId = videoId,
                                title = cached?.title ?: payload?.get("title")?.toString().orEmpty(),
                                artistName = cached?.artistName ?: payload?.get("artistName")?.toString().orEmpty(),
                                styleTag = cached?.styleTag ?: payload?.get("styleTag")?.toString().orEmpty(),
                                thumbnailUrl = cached?.thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                position = 0
                            )
                            val current = dao.observeCurrentPlaying().first()
                            if (path == "/api/queue/play" || current == null) {
                                dao.playImmediately(entity)
                                onPlayTriggeredFromWeb()
                            } else {
                                dao.enqueueAtEnd(entity)
                            }
                        }
                    }
                    Triple("200 OK", "application/json; charset=utf-8", """{"ok":true}""")
                }
                method == "POST" && path == "/api/queue/play-item" -> {
                    val payload = runCatching { gson.fromJson(body, Map::class.java) }.getOrNull()
                    val id = (payload?.get("id") as? Number)?.toLong()
                    if (id != null) {
                        runBlocking {
                            dao.playQueueItemNow(id)
                            onPlayTriggeredFromWeb()
                        }
                    }
                    Triple("200 OK", "application/json; charset=utf-8", """{"ok":true}""")
                }
                method == "POST" && path == "/api/queue/next" -> {
                    val next = runBlocking { dao.advanceToNextSong() }
                    if (next != null) {
                        onNextTriggeredFromWeb(next)
                    }
                    Triple("200 OK", "application/json; charset=utf-8", """{"ok":true}""")
                }
                method == "POST" && path == "/api/queue/remove" -> {
                    val payload = runCatching { gson.fromJson(body, Map::class.java) }.getOrNull()
                    val id = (payload?.get("id") as? Number)?.toLong()
                    if (id != null) {
                        runBlocking { dao.removeFromQueue(id) }
                    }
                    Triple("200 OK", "application/json; charset=utf-8", """{"ok":true}""")
                }
                method == "POST" && path == "/api/queue/clear" -> {
                    runBlocking { dao.clearWaitingQueue() }
                    Triple("200 OK", "application/json; charset=utf-8", """{"ok":true}""")
                }
                else -> Triple("404 Not Found", "text/plain; charset=utf-8", "Not Found")
            }

            val bytes = responseText.toByteArray(Charsets.UTF_8)
            val header = buildString {
                append("HTTP/1.1 $status\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Connection: close\r\n\r\n")
            }
            val out = sock.getOutputStream()
            out.write(header.toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.flush()
        }
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { pair ->
            val idx = pair.indexOf("=")
            if (idx <= 0) null
            else {
                val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                k to v
            }
        }.toMap()
    }

    private fun getLocalIpAddress(): String? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
        for (intf in interfaces) {
            if (!intf.isUp || intf.isLoopback) continue
            for (addr in intf.inetAddresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress
                }
            }
        }
        return null
    }
}

private val MOBILE_WEB_APP_HTML = """
<!DOCTYPE html>
<html lang="th">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Karaoke Now รีโมตมือถือ</title>
<style>
  * { box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
  body { margin: 0; background: #EEF1F5; color: #334155; padding-bottom: 84px; font-size: 18px; }
  header { background: #F6F8FB; padding: 16px; border-bottom: 1px solid #E2E8F0; position: sticky; top: 0; z-index: 10; box-shadow: 0 6px 18px rgba(163, 177, 198, 0.28); }
  h1 { margin: 0; font-size: 22px; color: #1E293B; }
  .sub { font-size: 15px; color: #D97706; margin-top: 4px; font-weight: 700; }
  .tabs { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; padding: 14px; background: #EEF1F5; }
  .tab { background: #F6F8FB; color: #475569; border: 1px solid rgba(255,255,255,0.92); padding: 14px 10px; border-radius: 16px; font-size: 16px; font-weight: 700; cursor: pointer; text-align: center; box-shadow: 5px 5px 12px #D1D9E6, -5px -5px 12px #FFFFFF; }
  .tab.active { background: linear-gradient(135deg, #FFB300, #F59E0B); border: none; color: #FFFFFF; box-shadow: 0 6px 16px rgba(245, 158, 11, 0.38); }
  .search-box { display: flex; gap: 10px; padding: 12px 14px; flex-wrap: wrap; }
  input { flex: 1; min-width: 180px; padding: 14px 18px; border-radius: 999px; border: 1px solid rgba(255,255,255,0.65); background: #E8ECF2; color: #1E293B; font-size: 17px; box-shadow: inset 4px 4px 8px #D1D9E6, inset -4px -4px 8px #FFFFFF; outline: none; }
  button.primary { background: linear-gradient(135deg, #FFB300, #F59E0B); color: #FFFFFF; border: none; padding: 14px 20px; border-radius: 999px; font-size: 17px; font-weight: 700; cursor: pointer; min-height: 50px; box-shadow: 0 5px 14px rgba(245, 158, 11, 0.35); }
  button.play { background: linear-gradient(135deg, #FFB300, #EA580C); color: #FFFFFF; border: none; padding: 14px 18px; border-radius: 999px; font-size: 17px; font-weight: 700; cursor: pointer; min-height: 50px; box-shadow: 0 5px 14px rgba(234, 88, 12, 0.3); }
  button.fav { background: #F6F8FB; color: #475569; border: 1px solid rgba(255,255,255,0.92); padding: 14px 16px; border-radius: 999px; font-size: 17px; font-weight: 700; cursor: pointer; min-height: 50px; box-shadow: 4px 4px 10px #D1D9E6, -4px -4px 10px #FFFFFF; }
  button.fav.on { background: linear-gradient(135deg, #FB7185, #E11D48); border: none; color: #FFFFFF; box-shadow: 0 5px 14px rgba(225, 29, 72, 0.3); }
  button.secondary { background: #F6F8FB; color: #475569; border: 1px solid rgba(255,255,255,0.92); padding: 14px 16px; border-radius: 999px; font-size: 16px; font-weight: 700; cursor: pointer; min-height: 50px; box-shadow: 4px 4px 10px #D1D9E6, -4px -4px 10px #FFFFFF; }
  .list { padding: 4px 14px; display: flex; flex-direction: column; gap: 14px; }
  .card { background: #F6F8FB; padding: 16px; border-radius: 20px; border: 1px solid rgba(255,255,255,0.92); box-shadow: 6px 6px 14px #D1D9E6, -6px -6px 14px #FFFFFF; }
  .card-top { display: flex; gap: 12px; align-items: center; }
  .card img { width: 108px; height: 72px; object-fit: cover; border-radius: 12px; background: #E2E8F0; flex-shrink: 0; }
  .info { flex: 1; min-width: 0; }
  .code-badge { display: inline-block; background: #F59E0B; color: #fff; font-size: 13px; font-weight: 700; padding: 3px 10px; border-radius: 999px; margin-bottom: 4px; }
  .title { font-size: 18px; font-weight: 700; line-height: 1.3; color: #1E293B; }
  .meta { font-size: 15px; color: #64748B; margin-top: 4px; }
  .actions { display: flex; gap: 10px; margin-top: 12px; }
  .actions button { flex: 1; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 14px; padding: 14px; }
  .tile { background: #F6F8FB; padding: 18px 14px; border-radius: 20px; border: 1px solid rgba(255,255,255,0.92); cursor: pointer; box-shadow: 6px 6px 14px #D1D9E6, -6px -6px 14px #FFFFFF; }
  .tile-title { font-weight: 700; font-size: 18px; color: #1E293B; }
  .tile-sub { font-size: 15px; color: #D97706; margin-top: 6px; font-weight: 600; }
  .filter-banner { margin: 0 14px 12px; padding: 12px 16px; background: #F6F8FB; border-radius: 16px; display: flex; justify-content: space-between; align-items: center; font-size: 16px; font-weight: 700; color: #D97706; box-shadow: 4px 4px 10px #D1D9E6, -4px -4px 10px #FFFFFF; }
  .keypad-wrap { padding: 14px; }
  .code-display { background: #E8ECF2; border: 1px solid rgba(255,255,255,0.7); border-radius: 20px; padding: 16px; text-align: center; font-size: 36px; font-weight: 800; letter-spacing: 6px; color: #D97706; min-height: 74px; margin-bottom: 14px; box-shadow: inset 4px 4px 8px #D1D9E6, inset -4px -4px 8px #FFFFFF; }
  .keypad { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 14px; }
  .key-btn { background: #F6F8FB; color: #1E293B; border: 1px solid rgba(255,255,255,0.92); border-radius: 18px; padding: 18px 0; font-size: 28px; font-weight: 800; cursor: pointer; box-shadow: 5px 5px 12px #D1D9E6, -5px -5px 12px #FFFFFF; }
  .key-btn:active { background: linear-gradient(135deg, #FFB300, #F59E0B); color: #FFFFFF; }
  .toast { position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%); background: #1E293B; color: #FFFFFF; padding: 14px 22px; border-radius: 999px; font-size: 17px; font-weight: 700; display: none; z-index: 99; box-shadow: 0 8px 24px rgba(30,41,59,0.28); }
  .ad-banner { margin: 12px 14px 0 14px; padding: 14px 16px; border-radius: 20px; background: #F6F8FB; border: 1px solid rgba(245, 158, 11, 0.35); display: none; align-items: center; gap: 12px; box-shadow: 6px 6px 14px #D1D9E6, -6px -6px 14px #FFFFFF; }
  .ad-badge { background: linear-gradient(135deg, #FFB300, #F59E0B); color: #FFFFFF; font-size: 11px; font-weight: 800; padding: 3px 9px; border-radius: 999px; flex-shrink: 0; }
  .ad-body { flex: 1; min-width: 0; }
  .ad-title { font-size: 15px; font-weight: 700; color: #1E293B; }
  .ad-sub { font-size: 13px; color: #64748B; margin-top: 2px; }
  .ad-cta { background: linear-gradient(135deg, #FFB300, #F59E0B); color: #FFFFFF; text-decoration: none; padding: 8px 14px; border-radius: 999px; font-size: 13px; font-weight: 700; white-space: nowrap; box-shadow: 0 4px 10px rgba(245, 158, 11, 0.3); }
  .ad-close { background: transparent; border: none; color: #64748B; font-size: 18px; cursor: pointer; padding: 4px 6px; }
</style>
</head>
<body>
<header>
  <h1>🎤 Karaoke Now รีโมตมือถือ <span id="appVersion" style="font-size:13px;color:#64748B;font-weight:700;background:#E8ECF2;padding:3px 9px;border-radius:999px;vertical-align:middle;">v${BuildConfig.VERSION_NAME}</span></h1>
  <div class="sub" id="nowPlaying">เชื่อมต่อกับทีวีแล้ว</div>
  <div id="catalogStats" style="font-size:13px;color:#475569;margin-top:4px;font-weight:600;">คลังเพลงในระบบ: กำลังโหลด...</div>
</header>
<div id="remoteAdBanner" class="ad-banner">
  <span class="ad-badge" id="adBadge">AD</span>
  <img id="adImage" src="" alt="Sponsor" style="display:none;max-height:64px;max-width:160px;border-radius:10px;object-fit:cover;flex-shrink:0;">
  <div class="ad-body" id="adTextWrap">
    <div class="ad-title" id="adTitle"></div>
    <div class="ad-sub" id="adSub"></div>
  </div>
  <a class="ad-cta" id="adCta" href="#" target="_blank" rel="noopener">ดูเพิ่มเติม</a>
  <button class="ad-close" onclick="document.getElementById('remoteAdBanner').style.display='none'" aria-label="ปิดโฆษณา">✕</button>
</div>
<div class="tabs">
  <button class="tab active" id="tab-search" onclick="switchTab('search', this)">🔍 ค้นหาเพลง</button>
  <button class="tab" id="tab-code" onclick="switchTab('code', this)">🔢 กดรหัสเพลง</button>
  <button class="tab" id="tab-queue" onclick="switchTab('queue', this)">📋 คิวเพลง (<span id="qCount">0</span>)</button>
  <button class="tab" id="tab-eras" onclick="switchTab('eras', this)">🕰️ ยุคเพลง</button>
  <button class="tab" id="tab-artists" onclick="switchTab('artists', this)">🎤 นักร้อง</button>
  <button class="tab" id="tab-styles" onclick="switchTab('styles', this)">🎼 สไตล์เพลง</button>
  <button class="tab" style="grid-column: span 2;" id="tab-favorites" onclick="switchTab('favorites', this)">❤️ เพลงโปรด (<span id="fCount">0</span>)</button>
</div>
<div id="searchBar" class="search-box">
  <input id="qInput" type="search" placeholder="พิมพ์ชื่อเพลง, นักร้อง หรือรหัสเพลง (เช่น 00001)..." onkeydown="if(event.key==='Enter')searchSongs()">
  <button class="primary" onclick="searchSongs()">🔍 ค้นหา</button>
  <button class="secondary" onclick="clearSearch()">✕ ล้าง</button>
</div>
<div id="content"></div>
<footer style="text-align:center;padding:24px 16px 12px;font-size:13px;color:#64748B;line-height:1.6;">
  พัฒนาโดย <strong style="color:#334155;">บริษัท ไวร์เลส โซลูชั่น เอเชีย จำกัด</strong><br>
  <a href="https://www.wirelesssolution.asia" target="_blank" rel="noopener" style="color:#D97706;text-decoration:none;font-weight:600;">https://www.wirelesssolution.asia</a>
</footer>
<div id="toast" class="toast"></div>

<script>
let currentTab = 'search';
let favIds = new Set();
let favArtists = new Set();
let onlyFavArtists = false;
let artistFilterQuery = '';
let cachedArtists = [];

function toast(msg) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.style.display = 'block';
  setTimeout(() => el.style.display = 'none', 2400);
}
function switchTab(tab, btn, skipFetch = false) {
  currentTab = tab;
  document.querySelectorAll('.tab').forEach(b => b.classList.remove('active'));
  if (btn) btn.classList.add('active');
  document.getElementById('searchBar').style.display = tab === 'search' ? 'flex' : 'none';
  if (skipFetch) return;
  if (tab === 'search') searchSongs();
  else if (tab === 'code') renderCodeKeypad();
  else if (tab === 'eras') loadTiles('/api/eras', 'era');
  else if (tab === 'artists') loadTiles('/api/artists', 'artist');
  else if (tab === 'channels') loadTiles('/api/channels', 'channel');
  else if (tab === 'styles') loadTiles('/api/styles', 'style');
  else if (tab === 'favorites') loadFavorites();
  else if (tab === 'queue') loadQueue();
}
let codeInput = '';
function renderCodeKeypad() {
  const keys = ['1','2','3','4','5','6','7','8','9','✕ ล้าง','0','⌫ ลบ'];
  const btns = keys.map(k => {
    if (k === '✕ ล้าง') return `<button class="key-btn" style="background:#7F1D1D;" onclick="codeInput='';updateCodeView()">${'$'}{k}</button>`;
    if (k === '⌫ ลบ') return `<button class="key-btn" style="background:#334155;" onclick="codeInput=codeInput.slice(0,-1);updateCodeView()">${'$'}{k}</button>`;
    return `<button class="key-btn" onclick="pressDigit('${'$'}{k}')">${'$'}{k}</button>`;
  }).join('');
  document.getElementById('content').innerHTML = `
    <div class="keypad-wrap">
      <div class="code-display" id="codeDisplay">${'$'}{codeInput || 'กดรหัสเพลง'}</div>
      <div class="keypad">${'$'}{btns}</div>
      <button class="primary" style="width:100%;font-size:22px;padding:16px;" onclick="searchByCode()">🔍 ค้นหาด้วยรหัสเพลง</button>
    </div>
    <div id="codeResults"></div>`;
}
function pressDigit(d) {
  if (codeInput.length < 6) {
    codeInput += d;
    updateCodeView();
  }
}
function updateCodeView() {
  const el = document.getElementById('codeDisplay');
  if (el) el.textContent = codeInput || 'กดรหัสเพลง';
}
async function searchByCode() {
  if (!codeInput) { toast('กรุณากดรหัสเพลงก่อน'); return; }
  const res = await fetch('/api/songs?q=' + encodeURIComponent(codeInput));
  const songs = await res.json();
  const target = document.getElementById('codeResults');
  if (!target) return;
  if (!songs.length) {
    target.innerHTML = '<div style="padding:20px;text-align:center;font-size:18px;color:#CBD5E1;">ไม่พบรหัสเพลง #' + esc(codeInput) + '</div>';
    return;
  }
  target.innerHTML = '<div class="list">' + songs.map(s => {
    const isFav = favIds.has(s.videoId);
    const codeHtml = s.songCode ? `<div class="code-badge">#${'$'}{esc(s.songCode)}</div>` : '';
    return `
    <div class="card">
      <div class="card-top">
        <img src="${'$'}{s.thumbnailUrl}" loading="lazy">
        <div class="info">
          ${'$'}{codeHtml}
          <div class="title">${'$'}{esc(s.title)}</div>
          <div class="meta">${'$'}{esc(s.artistName)} • ${'$'}{esc(s.styleTag)}</div>
        </div>
      </div>
      <div class="actions">
        <button class="primary" onclick="queueAction('/api/queue/add', '${'$'}{s.videoId}', 'เพิ่มเข้าคิวแล้ว')">+ เพิ่มคิว</button>
        <button class="play" onclick="queueAction('/api/queue/play', '${'$'}{s.videoId}', 'กำลังเปิดเล่นบนทีวี')">▶ ร้องเลย</button>
        <button class="fav ${'$'}{isFav ? 'on' : ''}" onclick="toggleFav('${'$'}{s.videoId}', this)">${'$'}{isFav ? '♥ โปรด' : '♡ โปรด'}</button>
      </div>
    </div>`;
  }).join('') + '</div>';
}
function clearSearch() {
  document.getElementById('qInput').value = '';
  searchSongs();
}
async function searchSongs(params = '', bannerLabel = '') {
  const q = document.getElementById('qInput').value.trim();
  const url = params ? '/api/songs?' + params : '/api/songs?q=' + encodeURIComponent(q);
  const res = await fetch(url);
  const songs = await res.json();
  renderSongs(songs, bannerLabel);
}
function renderSongs(songs, bannerLabel = '') {
  const banner = bannerLabel ? `<div class="filter-banner"><span>กำลังดู: ${'$'}{esc(bannerLabel)} (${'$'}{songs.length} เพลง)</span><button class="secondary" style="min-height:36px;padding:6px 12px;" onclick="clearSearch()">ดูทั้งหมด</button></div>` : '';
  if (!songs.length) {
    document.getElementById('content').innerHTML = banner + '<div style="padding:24px;text-align:center;font-size:18px;color:#CBD5E1;">ไม่พบเพลงที่ค้นหา</div>';
    return;
  }
  const html = banner + '<div class="list">' + songs.map(s => {
    const isFav = favIds.has(s.videoId);
    const codeHtml = s.songCode ? `<div class="code-badge">#${'$'}{esc(s.songCode)}</div>` : '';
    return `
    <div class="card">
      <div class="card-top">
        <img src="${'$'}{s.thumbnailUrl}" loading="lazy">
        <div class="info">
          ${'$'}{codeHtml}
          <div class="title">${'$'}{esc(s.title)}</div>
          <div class="meta">${'$'}{esc(s.artistName)} • ${'$'}{esc(s.styleTag)}</div>
        </div>
      </div>
      <div class="actions">
        <button class="primary" onclick="queueAction('/api/queue/add', '${'$'}{s.videoId}', 'เพิ่มเข้าคิวแล้ว')">+ เพิ่มคิว</button>
        <button class="play" onclick="queueAction('/api/queue/play', '${'$'}{s.videoId}', 'กำลังเปิดเล่นบนทีวี')">▶ ร้องเลย</button>
        <button class="fav ${'$'}{isFav ? 'on' : ''}" onclick="toggleFav('${'$'}{s.videoId}', this)">${'$'}{isFav ? '♥ โปรด' : '♡ โปรด'}</button>
      </div>
    </div>`;
  }).join('') + '</div>';
  document.getElementById('content').innerHTML = html;
}
async function loadTiles(endpoint, paramName) {
  let items = (paramName === 'artist' && cachedArtists.length) ? cachedArtists : null;
  if (!items) {
    const res = await fetch(endpoint);
    items = await res.json();
    if (paramName === 'artist') cachedArtists = items;
  }
  let headerBar = '';
  if (paramName === 'artist') {
    let list = items.slice();
    list.sort((a, b) => (favArtists.has(b.name) ? 1 : 0) - (favArtists.has(a.name) ? 1 : 0));
    if (onlyFavArtists) {
      list = list.filter(it => favArtists.has(it.name));
    }
    const q = artistFilterQuery.trim().toLowerCase();
    if (q) {
      list = list.filter(it => (it.name || '').toLowerCase().includes(q));
    }
    items = list.slice(0, 120);
    headerBar = `<div style="padding:12px 12px 0;">
      <div style="display:flex;gap:8px;margin-bottom:10px;">
        <input id="artistFilterInput" type="search" value="${'$'}{esc(artistFilterQuery)}" placeholder="🔍 พิมพ์ชื่อนักร้องเพื่อกรอง..." oninput="artistFilterQuery=this.value;renderArtistListOnly()">
        <button class="secondary" onclick="artistFilterQuery='';loadTiles('/api/artists','artist')">✕ ล้าง</button>
      </div>
      <div style="display:flex;gap:8px;">
        <button class="${'$'}{!onlyFavArtists ? 'primary' : 'secondary'}" style="flex:1" onclick="onlyFavArtists=false;loadTiles('/api/artists','artist')">ทั้งหมด</button>
        <button class="${'$'}{onlyFavArtists ? 'play' : 'secondary'}" style="flex:1" onclick="onlyFavArtists=true;loadTiles('/api/artists','artist')">★ ศิลปินโปรด (${'$'}{favArtists.size})</button>
      </div>
    </div>`;
  }
  if (paramName === 'artist' && !items.length) {
    document.getElementById('content').innerHTML = headerBar + '<div id="artistGridArea" style="padding:24px;text-align:center;font-size:18px;color:#CBD5E1;">ไม่พบรายชื่อนักร้องที่ตรงกับคำค้นหา</div>';
    return;
  }
  const gridHtml = '<div id="artistGridArea" class="grid">' + items.map(it => {
    const isFavArt = paramName === 'artist' && favArtists.has(it.name);
    const favBtn = paramName === 'artist'
      ? `<button class="fav ${'$'}{isFavArt ? 'on' : ''}" style="margin-top:8px;width:100%;min-height:40px;padding:8px;font-size:15px;" data-name="${'$'}{esc(it.name)}" data-style="${'$'}{esc(it.styleHint || '')}" onclick="event.stopPropagation();toggleFavArtist(this)">${'$'}{isFavArt ? '★ นักร้องโปรด' : '☆ โปรด'}</button>`
      : '';
    return `
    <div class="tile" data-param="${'$'}{paramName}" data-val="${'$'}{esc(it.name)}" onclick="openFiltered(this)">
      <div class="tile-title">${'$'}{isFavArt ? '★ ' : ''}${'$'}{esc(it.name)}</div>
      <div class="tile-sub">${'$'}{it.songCount} เพลง</div>
      ${'$'}{favBtn}
    </div>`;
  }).join('') + '</div>';
  document.getElementById('content').innerHTML = headerBar + gridHtml;
}
function renderArtistListOnly() {
  let list = cachedArtists.slice();
  list.sort((a, b) => (favArtists.has(b.name) ? 1 : 0) - (favArtists.has(a.name) ? 1 : 0));
  if (onlyFavArtists) list = list.filter(it => favArtists.has(it.name));
  const q = artistFilterQuery.trim().toLowerCase();
  if (q) list = list.filter(it => (it.name || '').toLowerCase().includes(q));
  const area = document.getElementById('artistGridArea');
  if (!area) return;
  if (!list.length) {
    area.className = '';
    area.innerHTML = '<div style="padding:24px;text-align:center;font-size:18px;color:#CBD5E1;">ไม่พบรายชื่อนักร้องที่ตรงกับคำค้นหา</div>';
    return;
  }
  area.className = 'grid';
  area.innerHTML = list.slice(0, 120).map(it => {
    const isFavArt = favArtists.has(it.name);
    return `
    <div class="tile" data-param="artist" data-val="${'$'}{esc(it.name)}" onclick="openFiltered(this)">
      <div class="tile-title">${'$'}{isFavArt ? '★ ' : ''}${'$'}{esc(it.name)}</div>
      <div class="tile-sub">${'$'}{it.songCount} เพลง</div>
      <button class="fav ${'$'}{isFavArt ? 'on' : ''}" style="margin-top:8px;width:100%;min-height:40px;padding:8px;font-size:15px;" data-name="${'$'}{esc(it.name)}" data-style="${'$'}{esc(it.styleHint || '')}" onclick="event.stopPropagation();toggleFavArtist(this)">${'$'}{isFavArt ? '★ นักร้องโปรด' : '☆ โปรด'}</button>
    </div>`;
  }).join('');
}
async function toggleFavArtist(btn) {
  const artistName = btn.getAttribute('data-name');
  const styleHint = btn.getAttribute('data-style');
  const res = await fetch('/api/favorite-artists/toggle', {
    method: 'POST',
    headers: {'Content-Type':'application/json'},
    body: JSON.stringify({artistName, styleHint})
  });
  const data = await res.json();
  if (data.added) { favArtists.add(artistName); toast('บันทึกนักร้องโปรดแล้ว'); }
  else { favArtists.delete(artistName); toast('นำออกจากนักร้องโปรดแล้ว'); }
  loadTiles('/api/artists', 'artist');
}
function openFiltered(el) {
  const paramName = el.getAttribute('data-param');
  const rawVal = el.getAttribute('data-val');
  switchTab('search', document.getElementById('tab-search'), true);
  searchSongs(paramName + '=' + encodeURIComponent(rawVal), rawVal);
}
async function loadFavorites() {
  const res = await fetch('/api/favorites');
  const songs = await res.json();
  favIds = new Set(songs.map(s => s.videoId));
  document.getElementById('fCount').textContent = songs.length;
  renderSongs(songs, 'เพลงโปรดของคุณ');
}
async function toggleFav(videoId, btn) {
  const res = await fetch('/api/favorites/toggle', { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify({videoId}) });
  const data = await res.json();
  if (data.added) {
    favIds.add(videoId);
    if (btn) { btn.classList.add('on'); btn.textContent = '♥ โปรด'; }
    toast('บันทึกเป็นเพลงโปรดแล้ว');
  } else {
    favIds.delete(videoId);
    if (btn) { btn.classList.remove('on'); btn.textContent = '♡ โปรด'; }
    toast('นำออกจากเพลงโปรดแล้ว');
  }
  document.getElementById('fCount').textContent = favIds.size;
  if (currentTab === 'favorites') loadFavorites();
}
async function loadQueue() {
  const res = await fetch('/api/queue');
  const items = await res.json();
  document.getElementById('qCount').textContent = items.length;
  const playing = items.find(i => i.status === 'PLAYING');
  document.getElementById('nowPlaying').textContent = playing ? '▶ กำลังเล่น: ' + playing.title : 'พร้อมรับคิวเพลง';
  if (currentTab !== 'queue') return;
  const controls = `<div style="padding:12px;display:flex;gap:8px;">
    <button class="play" style="flex:1" onclick="postSimple('/api/queue/next','ข้ามไปเพลงถัดไป')">⏭ เพลงถัดไป</button>
    <button class="secondary" style="flex:1" onclick="postSimple('/api/queue/clear','ล้างคิวที่รอแล้ว')">🗑 ล้างคิวที่รอ</button>
  </div>`;
  const list = '<div class="list">' + items.map((it, idx) => `
    <div class="card">
      <div class="title">${'$'}{it.status === 'PLAYING' ? '▶ ' : (idx+1)+'. '}${'$'}{esc(it.title)}</div>
      <div class="meta">${'$'}{esc(it.artistName)} • ${'$'}{esc(it.styleTag)}</div>
      <div class="actions">
        ${'$'}{it.status !== 'PLAYING' ? `<button class="play" onclick="postId('/api/queue/play-item', ${'$'}{it.id}, 'กำลังเล่น')">▶ เล่นเลย</button>` : ''}
        <button class="fav ${'$'}{favIds.has(it.videoId)?'on':''}" onclick="toggleFavFromQueue('${'$'}{it.videoId}', '${'$'}{esc(it.title)}', '${'$'}{esc(it.artistName)}', '${'$'}{esc(it.styleTag)}')">${'$'}{favIds.has(it.videoId)?'❤️ โปรดแล้ว':'🤍 โปรด'}</button>
        <button class="secondary" onclick="postId('/api/queue/remove', ${'$'}{it.id}, 'ลบออกจากคิวแล้ว')">✕ ลบ</button>
      </div>
    </div>`).join('') + '</div>';
  document.getElementById('content').innerHTML = controls + list;
}
async function toggleFavFromQueue(videoId, title, artistName, styleTag) {
  const res = await fetch('/api/favorites/toggle', {
    method: 'POST',
    headers: {'Content-Type':'application/json'},
    body: JSON.stringify({videoId, title, artistName, styleTag})
  });
  const data = await res.json();
  toast(data.added ? 'เพิ่มเพลงโปรดแล้ว ❤️' : 'ลบออกจากเพลงโปรดแล้ว');
  await refreshBadges();
  loadQueue();
}
async function queueAction(endpoint, videoId, msg) {
  await fetch(endpoint, { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify({videoId}) });
  toast(msg);
  refreshBadges();
}
async function postId(endpoint, id, msg) {
  await fetch(endpoint, { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify({id}) });
  toast(msg);
  loadQueue();
}
async function postSimple(endpoint, msg) {
  await fetch(endpoint, { method: 'POST' });
  toast(msg);
  loadQueue();
}
async function refreshBadges() {
  const [qRes, fRes, faRes, stRes] = await Promise.all([fetch('/api/queue'), fetch('/api/favorites'), fetch('/api/favorite-artists'), fetch('/api/stats')]);
  const items = await qRes.json();
  const favs = await fRes.json();
  const fa = await faRes.json();
  const stats = await stRes.json();
  favIds = new Set(favs.map(s => s.videoId));
  favArtists = new Set(fa);
  document.getElementById('qCount').textContent = items.length;
  document.getElementById('fCount').textContent = favs.length;
  if (stats && typeof stats.totalSongs === 'number') {
    document.getElementById('catalogStats').textContent = 'คลังเพลงในระบบ: ' + stats.totalSongs.toLocaleString() + ' เพลง • v' + (stats.version || '${BuildConfig.VERSION_NAME}');
  }
  const playing = items.find(i => i.status === 'PLAYING');
  document.getElementById('nowPlaying').textContent = playing ? '▶ กำลังเล่น: ' + playing.title : 'พร้อมรับคิวเพลง';
}
function esc(s) { return (s||'').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
async function loadRemoteAd() {
  try {
    const res = await fetch('/api/ad-config');
    const ad = await res.json();
    if (!ad || !ad.enabled) return;
    const mode = (ad.displayMode || 'both').toLowerCase();
    const hasImg = mode !== 'text' && !!ad.imageUrl;
    const showText = mode !== 'image' || !hasImg;
    document.getElementById('adBadge').textContent = ad.badge || 'AD';
    const imgEl = document.getElementById('adImage');
    if (hasImg) {
      imgEl.src = ad.imageUrl;
      imgEl.style.display = 'block';
      imgEl.style.maxWidth = mode === 'image' ? '100%' : '140px';
      imgEl.style.flex = mode === 'image' ? '1' : '0 0 auto';
    } else {
      imgEl.style.display = 'none';
    }
    const textWrap = document.getElementById('adTextWrap');
    textWrap.style.display = showText ? 'block' : 'none';
    document.getElementById('adTitle').textContent = ad.title || '';
    document.getElementById('adSub').textContent = ad.subtitle || '';
    const cta = document.getElementById('adCta');
    cta.textContent = ad.ctaText || 'ดูเพิ่มเติม';
    cta.href = ad.targetUrl || '#';
    document.getElementById('remoteAdBanner').style.display = 'flex';
  } catch (e) {}
}
loadRemoteAd();
refreshBadges().then(() => searchSongs());
setInterval(refreshBadges, 4000);
</script>
</body>
</html>
""".trimIndent()
