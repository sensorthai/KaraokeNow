package com.karaoketv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.karaoketv.app.ads.AdConfigRepository
import com.karaoketv.app.data.local.QueueStatus
import com.karaoketv.app.data.model.SearchFilter
import com.karaoketv.app.ui.KaraokeViewModel
import com.karaoketv.app.ui.TvDestination
import com.karaoketv.app.ui.components.AppOpenAdOverlay
import com.karaoketv.app.ui.components.BetweenSongsAdOverlay
import com.karaoketv.app.ui.screens.ArtistsScreen
import com.karaoketv.app.ui.screens.ErasScreen
import com.karaoketv.app.ui.screens.FavoritesScreen
import com.karaoketv.app.ui.screens.HomeScreen
import com.karaoketv.app.ui.screens.PlayerScreen
import com.karaoketv.app.ui.screens.QrRemoteScreen
import com.karaoketv.app.ui.screens.QueueScreen
import com.karaoketv.app.ui.screens.SearchScreen
import com.karaoketv.app.ui.screens.SongCatalogScreen
import com.karaoketv.app.ui.screens.StylesScreen
import com.karaoketv.app.ui.theme.KaraokeColors
import com.karaoketv.app.ui.theme.KaraokeTvTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KaraokeTvTheme {
                KaraokeTvApp()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KaraokeTvApp(viewModel: KaraokeViewModel = viewModel()) {
    var currentDestination by remember { mutableStateOf(TvDestination.HOME) }
    var adConfig by remember { mutableStateOf(AdConfigRepository.currentConfig) }
    var showAppOpenAd by remember { mutableStateOf(adConfig.appOpen.enabled) }

    LaunchedEffect(Unit) {
        adConfig = AdConfigRepository.refreshIfConfigured()
    }

    val activeQueue by viewModel.activeQueue.collectAsState()
    val currentPlaying by viewModel.currentPlaying.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val catalogSongs by viewModel.catalogSongs.collectAsState()
    val dynamicArtists by viewModel.featuredArtists.collectAsState()
    val dynamicChannels by viewModel.dynamicChannels.collectAsState()
    val dynamicStyles by viewModel.musicStyles.collectAsState()
    val dynamicEras by viewModel.dynamicEras.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val favoriteArtistNames by viewModel.favoriteArtistNames.collectAsState()
    val isSyncingCatalog by viewModel.isSyncingCatalog.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()
    val isPlayerFullscreen by viewModel.isPlayerFullscreen.collectAsState()
    val pendingNextSongAd by viewModel.pendingNextSongAd.collectAsState()

    val waitingCount = activeQueue.count { it.status == QueueStatus.WAITING }

    LaunchedEffect(statusBanner) {
        if (statusBanner != null) {
            delay(3_000)
            viewModel.dismissBanner()
        }
    }

    if (isPlayerFullscreen && currentPlaying != null) {
        BackHandler {
            viewModel.dismissBetweenSongsAd()
            viewModel.closePlayerFullscreen()
        }
        val betweenSong = pendingNextSongAd
        if (betweenSong != null && adConfig.betweenSongs.enabled) {
            BetweenSongsAdOverlay(
                nextSong = betweenSong,
                ad = adConfig.betweenSongs,
                onFinish = { viewModel.dismissBetweenSongsAd() }
            )
        } else {
            PlayerScreen(
                currentSong = currentPlaying!!,
                waitingCount = waitingCount,
                onSongEnded = { viewModel.onSongEnded() },
                onSkipNext = { viewModel.skipToNext() },
                onBackToMenu = { viewModel.closePlayerFullscreen() }
            )
        }
        return
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(KaraokeColors.Background)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            NavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    val isExpanded = drawerState.currentValue == DrawerValue.Open
                    val drawerScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(Color(0xFFF8FAFC))
                            .verticalScroll(drawerScrollState)
                            .padding(horizontal = if (isExpanded) 14.dp else 12.dp, vertical = 16.dp),
                        horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher),
                                contentDescription = "Karaoke Now",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        TvDestination.entries.forEach { destination ->
                            val badge = if (destination == TvDestination.QUEUE && activeQueue.isNotEmpty()) {
                                " (${activeQueue.size})"
                            } else ""
                            val isSelected = currentDestination == destination

                            NavigationDrawerItem(
                                selected = isSelected,
                                onClick = { currentDestination = destination },
                                colors = NavigationDrawerItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    contentColor = KaraokeColors.TextPrimary,
                                    selectedContainerColor = KaraokeColors.Primary.copy(alpha = 0.16f),
                                    selectedContentColor = Color(0xFFB45309),
                                    focusedContainerColor = KaraokeColors.Primary,
                                    focusedContentColor = Color.White,
                                    focusedSelectedContainerColor = KaraokeColors.Primary,
                                    focusedSelectedContentColor = Color.White
                                ),
                                leadingContent = {
                                    Icon(
                                        imageVector = destination.icon(),
                                        contentDescription = destination.label,
                                        tint = if (isSelected) KaraokeColors.Primary else KaraokeColors.TextSecondary
                                    )
                                }
                            ) {
                                Text(
                                    text = "${destination.label}$badge",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f, fill = true))
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            color = KaraokeColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                }
            ) {
                // Main Content Area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(KaraokeColors.Background)
                ) {
                    when (currentDestination) {
                        TvDestination.HOME -> HomeScreen(
                            currentPlaying = currentPlaying,
                            queueCount = waitingCount,
                            popularSongs = catalogSongs.take(40),
                            styles = dynamicStyles,
                            artists = dynamicArtists,
                            favoriteIds = favoriteIds,
                            onPlayNow = { viewModel.playNow(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onSelectStyle = { style ->
                                viewModel.browseByStyle(style.name)
                                currentDestination = TvDestination.SEARCH
                            },
                            onSelectArtist = { artist ->
                                viewModel.browseByArtist(artist.name)
                                currentDestination = TvDestination.SEARCH
                            },
                            onOpenPlayer = { viewModel.openPlayerFullscreen() }
                        )

                        TvDestination.SEARCH -> SearchScreen(
                            query = searchQuery,
                            selectedFilter = selectedFilter,
                            results = searchResults,
                            history = searchHistory,
                            isSearching = isSearching,
                            favoriteIds = favoriteIds,
                            onQueryChange = { viewModel.updateSearchQuery(it) },
                            onFilterSelect = { viewModel.selectFilter(it) },
                            onSubmitSearch = { q, f -> viewModel.performSearch(q, f) },
                            onPlayNow = { viewModel.playNow(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) }
                        )

                        TvDestination.CATALOG -> SongCatalogScreen(
                            songs = catalogSongs,
                            isSyncing = isSyncingCatalog,
                            favoriteIds = favoriteIds,
                            onSyncFromYouTube = { viewModel.syncCatalogFromYouTube() },
                            onPlayNow = { viewModel.playNow(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) }
                        )

                        TvDestination.ARTISTS -> ArtistsScreen(
                            artists = dynamicArtists,
                            favoriteArtistNames = favoriteArtistNames,
                            onSelectArtist = { artist ->
                                viewModel.browseByArtist(artist.name)
                                currentDestination = TvDestination.SEARCH
                            },
                            onToggleFavoriteArtist = { viewModel.toggleFavoriteArtist(it) }
                        )

                        TvDestination.STYLES -> StylesScreen(
                            styles = dynamicStyles,
                            onSelectStyle = { style ->
                                viewModel.browseByStyle(style.name)
                                currentDestination = TvDestination.SEARCH
                            }
                        )

                        TvDestination.ERAS -> ErasScreen(
                            eras = dynamicEras,
                            onSelectEra = { era ->
                                viewModel.browseByEra(era.name)
                                currentDestination = TvDestination.SEARCH
                            }
                        )

                        TvDestination.FAVORITES -> FavoritesScreen(
                            favorites = favorites,
                            onPlayNow = { viewModel.playNow(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) }
                        )

                        TvDestination.QUEUE -> QueueScreen(
                            queueItems = activeQueue,
                            favorites = favorites.map { it.videoId }.toSet(),
                            onPlayQueueItem = { viewModel.playQueueItem(it) },
                            onRemoveQueueItem = { viewModel.removeFromQueue(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onSkipToNext = { viewModel.skipToNext() },
                            onClearWaiting = { viewModel.clearWaitingQueue() },
                            onOpenPlayer = { viewModel.openPlayerFullscreen() }
                        )

                        TvDestination.QR_REMOTE -> {
                            val currentServerUrl by viewModel.remoteServerUrl.collectAsState()
                            QrRemoteScreen(
                                serverUrl = currentServerUrl,
                                mdnsUrl = viewModel.mdnsUrl,
                                queueCount = activeQueue.size,
                                totalSongsCount = catalogSongs.size
                            )
                        }
                    }
                }
            }

            // Status Toast Banner
            statusBanner?.let { message ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(KaraokeColors.Primary)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = message,
                        color = KaraokeColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (showAppOpenAd && adConfig.appOpen.enabled) {
                AppOpenAdOverlay(
                    ad = adConfig.appOpen,
                    onDismiss = { showAppOpenAd = false }
                )
            }
        }
    }
}

private fun TvDestination.icon(): ImageVector = when (this) {
    TvDestination.HOME -> Icons.Default.Home
    TvDestination.SEARCH -> Icons.Default.Search
    TvDestination.CATALOG -> Icons.Default.LibraryMusic
    TvDestination.ARTISTS -> Icons.Default.Mic
    TvDestination.STYLES -> Icons.Default.Category
    TvDestination.ERAS -> Icons.Default.History
    TvDestination.FAVORITES -> Icons.Default.Favorite
    TvDestination.QUEUE -> Icons.Default.QueueMusic
    TvDestination.QR_REMOTE -> Icons.Default.QrCode2
}
