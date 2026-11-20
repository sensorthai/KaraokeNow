package com.karaoketv.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karaoketv.app.data.local.FavoriteArtistEntity
import com.karaoketv.app.data.local.FavoriteSongEntity
import com.karaoketv.app.data.local.FavoritesBackupManager
import com.karaoketv.app.ads.AdConfigRepository
import com.karaoketv.app.data.local.KaraokeDatabase
import com.karaoketv.app.data.local.QueueItemEntity
import com.karaoketv.app.data.local.SearchHistoryEntity
import com.karaoketv.app.data.model.ArtistProfile
import com.karaoketv.app.data.model.ChannelProfile
import com.karaoketv.app.data.model.MusicStyle
import com.karaoketv.app.data.model.SearchFilter
import com.karaoketv.app.data.model.SongItem
import com.karaoketv.app.data.repository.YouTubeRepository
import com.karaoketv.app.remote.KaraokeServerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TvDestination(val label: String) {
    HOME("หน้าแรก"),
    SEARCH("ค้นหา"),
    CATALOG("รายชื่อเพลง"),
    ARTISTS("นักร้อง"),
    STYLES("สไตล์เพลง"),
    ERAS("ยุคเพลง"),
    FAVORITES("รายการโปรด"),
    QUEUE("คิวเพลง"),
    QR_REMOTE("สแกน QR")
}

class KaraokeViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = KaraokeDatabase.getInstance(application).karaokeDao()
    private val repository = YouTubeRepository(dao)

    val remoteServerUrl: StateFlow<String> = KaraokeServerService.serverUrlState

    val mdnsUrl: String = "http://karaokenow.local:8888"

    val activeQueue: StateFlow<List<QueueItemEntity>> = dao.observeActiveQueue()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentPlaying: StateFlow<QueueItemEntity?> = dao.observeCurrentPlaying()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = dao.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val catalogSongs: StateFlow<List<SongItem>> = repository.observeCatalogSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YouTubeRepository.demoCatalog)

    private val _isSyncingCatalog = MutableStateFlow(false)
    val isSyncingCatalog: StateFlow<Boolean> = _isSyncingCatalog.asStateFlow()

    init {
        KaraokeServerService.start(application)
        viewModelScope.launch {
            KaraokeServerService.playTriggeredFromWeb.collect {
                _isPlayerFullscreen.value = true
            }
        }
        viewModelScope.launch {
            KaraokeServerService.nextSongTriggeredFromWeb.collect { next ->
                if (AdConfigRepository.currentConfig.betweenSongs.enabled) {
                    _pendingNextSongAd.value = next
                }
                _isPlayerFullscreen.value = true
            }
        }
        viewModelScope.launch {
            FavoritesBackupManager.restoreIfEmpty(dao)
            val count = repository.seedBundledCatalogIfNeeded(getApplication())
            if (count == 0) {
                syncCatalogFromYouTube()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun syncCatalogFromYouTube() {
        if (_isSyncingCatalog.value) return
        viewModelScope.launch {
            _isSyncingCatalog.value = true
            repository.syncCatalogFromYouTube()
                .onSuccess { count ->
                    _statusBanner.value = if (count > 0) {
                        "อัปเดตเพลงจาก YouTube เข้า SQLite แล้ว $count เพลง"
                    } else {
                        "ไม่พบเพลงใหม่จาก YouTube"
                    }
                }
                .onFailure {
                    _statusBanner.value = "เชื่อมต่อ YouTube ไม่สำเร็จ ใช้ข้อมูลใน SQLite แทน"
                }
            _isSyncingCatalog.value = false
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(SearchFilter.ALL)
    val selectedFilter: StateFlow<SearchFilter> = _selectedFilter.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SongItem>>(YouTubeRepository.demoCatalog)
    val searchResults: StateFlow<List<SongItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isPlayerFullscreen = MutableStateFlow(false)
    val isPlayerFullscreen: StateFlow<Boolean> = _isPlayerFullscreen.asStateFlow()

    val musicStyles: StateFlow<List<MusicStyle>> = repository.observeDynamicStyles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.musicStyles)

    val dynamicEras: StateFlow<List<MusicStyle>> = repository.observeDynamicEras()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val featuredArtists: StateFlow<List<ArtistProfile>> = repository.observeDynamicArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.featuredArtists)

    val dynamicChannels: StateFlow<List<ChannelProfile>> = repository.observeDynamicChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<SongItem>> = dao.observeFavorites()
        .map { list ->
            list.map {
                SongItem(
                    videoId = it.videoId,
                    title = it.title,
                    artistName = it.artistName,
                    styleTag = it.styleTag,
                    thumbnailUrl = it.thumbnailUrl,
                    channelTitle = it.channelTitle
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = dao.observeFavoriteIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val favoriteArtistNames: StateFlow<Set<String>> = dao.observeFavoriteArtistNames()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleFavoriteArtist(artist: ArtistProfile) {
        viewModelScope.launch {
            val added = dao.toggleFavoriteArtist(
                FavoriteArtistEntity(
                    artistName = artist.name,
                    styleHint = artist.styleHint
                )
            )
            FavoritesBackupManager.saveBackup(dao)
            _statusBanner.value = if (added) "บันทึกนักร้องโปรดแล้ว: ${artist.name}" else "นำออกจากนักร้องโปรดแล้ว"
        }
    }

    fun toggleFavorite(song: SongItem) {
        viewModelScope.launch {
            val added = dao.toggleFavorite(
                FavoriteSongEntity(
                    videoId = song.videoId,
                    title = song.title,
                    artistName = song.artistName,
                    styleTag = song.styleTag,
                    thumbnailUrl = song.thumbnailUrl,
                    channelTitle = song.channelTitle,
                    songCode = song.songCode
                )
            )
            FavoritesBackupManager.saveBackup(dao)
            _statusBanner.value = if (added) "เพิ่มในรายการโปรดแล้ว: ${song.title}" else "นำออกจากรายการโปรดแล้ว"
        }
    }

    fun browseByArtist(artistName: String) {
        _searchQuery.value = artistName
        _selectedFilter.value = SearchFilter.ARTIST
        viewModelScope.launch {
            val local = repository.getLocalSongsByArtist(artistName)
            if (local.isNotEmpty()) {
                _searchResults.value = local
            } else {
                performSearch(artistName, SearchFilter.ARTIST)
            }
        }
    }

    fun browseByChannel(channelTitle: String) {
        _searchQuery.value = channelTitle
        _selectedFilter.value = SearchFilter.ALL
        viewModelScope.launch {
            val local = repository.getLocalSongsByChannel(channelTitle)
            if (local.isNotEmpty()) {
                _searchResults.value = local
            } else {
                performSearch(channelTitle, SearchFilter.ALL)
            }
        }
    }

    fun browseByStyle(styleTag: String) {
        _searchQuery.value = styleTag
        _selectedFilter.value = SearchFilter.STYLE
        viewModelScope.launch {
            val local = repository.getLocalSongsByStyle(styleTag)
            if (local.isNotEmpty()) {
                _searchResults.value = local
            } else {
                performSearch(styleTag, SearchFilter.STYLE)
            }
        }
    }

    fun browseByEra(eraTag: String) {
        _searchQuery.value = eraTag
        _selectedFilter.value = SearchFilter.STYLE
        viewModelScope.launch {
            val local = repository.getLocalSongsByEra(eraTag)
            if (local.isNotEmpty()) {
                _searchResults.value = local
            } else {
                performSearch(eraTag, SearchFilter.STYLE)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFilter(filter: SearchFilter) {
        _selectedFilter.value = filter
        performSearch(_searchQuery.value, filter, saveHistory = false)
    }

    fun performSearch(
        query: String = _searchQuery.value,
        filter: SearchFilter = _selectedFilter.value,
        saveHistory: Boolean = true
    ) {
        _searchQuery.value = query
        _selectedFilter.value = filter
        viewModelScope.launch {
            _isSearching.value = true
            repository.searchSongs(query, filter, saveHistory).onSuccess { songs ->
                _searchResults.value = songs
            }
            _isSearching.value = false
        }
    }

    fun addToQueue(song: SongItem) {
        viewModelScope.launch {
            val current = currentPlaying.value
            val entity = song.toQueueEntity()
            if (current == null) {
                dao.playImmediately(entity)
                _statusBanner.value = "กำลังเล่น: ${song.title}"
            } else {
                dao.enqueueAtEnd(entity)
                _statusBanner.value = "เพิ่มเข้าคิวแล้ว: ${song.title}"
            }
        }
    }

    fun playNow(song: SongItem) {
        viewModelScope.launch {
            dao.playImmediately(song.toQueueEntity())
            _isPlayerFullscreen.value = true
            _statusBanner.value = "กำลังเล่น: ${song.title}"
        }
    }

    fun playQueueItem(itemId: Long) {
        viewModelScope.launch {
            dao.playQueueItemNow(itemId)
            _isPlayerFullscreen.value = true
        }
    }

    private val _pendingNextSongAd = MutableStateFlow<QueueItemEntity?>(null)
    val pendingNextSongAd: StateFlow<QueueItemEntity?> = _pendingNextSongAd.asStateFlow()

    fun onSongEnded() {
        viewModelScope.launch {
            val next = dao.advanceToNextSong()
            if (next == null) {
                _pendingNextSongAd.value = null
                _isPlayerFullscreen.value = false
                _statusBanner.value = "เล่นครบทุกเพลงในคิวแล้ว"
            } else {
                if (AdConfigRepository.currentConfig.betweenSongs.enabled) {
                    _pendingNextSongAd.value = next
                }
                _isPlayerFullscreen.value = true
            }
        }
    }

    fun dismissBetweenSongsAd() {
        _pendingNextSongAd.value = null
    }

    fun skipToNext() {
        onSongEnded()
    }

    fun removeFromQueue(itemId: Long) {
        viewModelScope.launch {
            dao.removeFromQueue(itemId)
        }
    }

    fun clearWaitingQueue() {
        viewModelScope.launch {
            dao.clearWaitingQueue()
            _statusBanner.value = "ล้างคิวเพลงที่รออยู่แล้ว"
        }
    }

    fun openPlayerFullscreen() {
        if (currentPlaying.value != null) {
            _isPlayerFullscreen.value = true
        }
    }

    fun closePlayerFullscreen() {
        _isPlayerFullscreen.value = false
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    private fun SongItem.toQueueEntity() = QueueItemEntity(
        videoId = videoId,
        title = title,
        artistName = artistName,
        styleTag = styleTag,
        thumbnailUrl = thumbnailUrl,
        position = 0
    )
}
