package com.karaoketv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.karaoketv.app.data.local.QueueItemEntity
import com.karaoketv.app.data.local.QueueStatus
import com.karaoketv.app.data.model.ArtistProfile
import com.karaoketv.app.data.model.ChannelProfile
import com.karaoketv.app.data.model.MusicStyle
import com.karaoketv.app.data.model.SongItem
import com.karaoketv.app.ui.components.SongCard
import com.karaoketv.app.ui.theme.KaraokeColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SongCatalogScreen(
    songs: List<SongItem>,
    isSyncing: Boolean,
    favoriteIds: Set<String> = emptySet(),
    onSyncFromYouTube: () -> Unit,
    onPlayNow: (SongItem) -> Unit,
    onAddToQueue: (SongItem) -> Unit,
    onToggleFavorite: (SongItem) -> Unit = {}
) {
    var filterText by remember { mutableStateOf("") }
    val filteredSongs = remember(songs, filterText) {
        val q = filterText.trim().lowercase()
        if (q.isEmpty()) songs
        else songs.filter {
            it.songCode.contains(q) ||
                it.title.lowercase().contains(q) ||
                it.artistName.lowercase().contains(q) ||
                it.styleTag.lowercase().contains(q) ||
                it.channelTitle.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "รายชื่อเพลงใน SQLite (${filteredSongs.size} / ${songs.size} เพลง)",
                color = KaraokeColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onSyncFromYouTube) {
                Text(if (isSyncing) "กำลังซิงค์จาก YouTube..." else "อัปเดตเพลงจาก YouTube")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            singleLine = true,
            placeholder = { Text("ค้นหาชื่อเพลง, นักร้อง, สไตล์ หรือช่อง ในรายชื่อเพลง...") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = KaraokeColors.TextPrimary,
                unfocusedTextColor = KaraokeColors.TextPrimary,
                focusedContainerColor = KaraokeColors.Surface,
                unfocusedContainerColor = KaraokeColors.Surface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 250.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filteredSongs, key = { it.videoId }) { song ->
                SongCard(
                    song = song,
                    onPlayNow = onPlayNow,
                    onAddToQueue = onAddToQueue,
                    isFavorite = song.videoId in favoriteIds,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }
    }
}

private enum class ArtistIndexGroup(val label: String, val test: (Char) -> Boolean) {
    ALL("ทั้งหมด", { true }),
    FAV("★ โปรด", { true }),
    TH_1("ก-จ", { it in 'ก'..'จ' }),
    TH_2("ฉ-น", { it in 'ฉ'..'น' }),
    TH_3("บ-ฟ", { it in 'บ'..'ฟ' }),
    TH_4("ม-ฮ", { it in 'ม'..'ฮ' }),
    EN_1("A-G", { it.uppercaseChar() in 'A'..'G' }),
    EN_2("H-P", { it.uppercaseChar() in 'H'..'P' }),
    EN_3("Q-Z", { it.uppercaseChar() in 'Q'..'Z' })
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ArtistsScreen(
    artists: List<ArtistProfile>,
    favoriteArtistNames: Set<String> = emptySet(),
    onSelectArtist: (ArtistProfile) -> Unit,
    onToggleFavoriteArtist: (ArtistProfile) -> Unit = {}
) {
    var filterText by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf(ArtistIndexGroup.ALL) }

    val filteredArtists = remember(artists, filterText, selectedGroup, favoriteArtistNames) {
        val q = filterText.trim().lowercase()
        artists
            .filter { artist ->
                val matchesText = q.isEmpty() || artist.name.lowercase().contains(q)
                val firstChar = artist.name.trimStart { it in "เแโใไ\"'(" }.firstOrNull()
                val matchesGroup = when (selectedGroup) {
                    ArtistIndexGroup.ALL -> true
                    ArtistIndexGroup.FAV -> artist.name in favoriteArtistNames
                    else -> firstChar != null && selectedGroup.test(firstChar)
                }
                matchesText && matchesGroup
            }
            .sortedByDescending { it.name in favoriteArtistNames }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Text(
            text = "เลือกตามนักร้อง / ศิลปิน (${filteredArtists.size} / ${artists.size} คน)",
            color = KaraokeColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            singleLine = true,
            placeholder = { Text("พิมพ์ชื่อนักร้องเพื่อกรองทันที...") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = KaraokeColors.TextPrimary,
                unfocusedTextColor = KaraokeColors.TextPrimary,
                focusedContainerColor = KaraokeColors.Surface,
                unfocusedContainerColor = KaraokeColors.Surface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ArtistIndexGroup.entries) { group ->
                val isGroupSelected = selectedGroup == group
                FilterChip(
                    selected = isGroupSelected,
                    onClick = { selectedGroup = group },
                    colors = FilterChipDefaults.colors(
                        containerColor = Color.White,
                        contentColor = KaraokeColors.TextPrimary,
                        selectedContainerColor = KaraokeColors.Primary,
                        selectedContentColor = Color.White,
                        focusedContainerColor = KaraokeColors.Primary.copy(alpha = 0.85f),
                        focusedContentColor = Color.White,
                        focusedSelectedContainerColor = Color(0xFFD97706),
                        focusedSelectedContentColor = Color.White
                    )
                ) {
                    Text(group.label, fontWeight = if (isGroupSelected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        if (filteredArtists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KaraokeColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedGroup == ArtistIndexGroup.FAV) {
                        "ยังไม่มีนักร้องโปรด — เลือก 'ทั้งหมด' แล้วกดปุ่ม ☆ โปรด บนการ์ดนักร้องเพื่อบันทึก"
                    } else {
                        "ไม่พบรายชื่อนักร้องที่ตรงกับคำค้นหา"
                    },
                    color = KaraokeColors.TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredArtists, key = { it.name }) { artist ->
                    val isFav = artist.name in favoriteArtistNames
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isFav) Color(0xFFFFFBEB) else Color.White)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = if (isFav) "★ ${artist.name}" else artist.name,
                            color = if (isFav) Color(0xFFB45309) else KaraokeColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (artist.songCount > 0) "${artist.songCount} เพลง • ${artist.styleHint}" else "สไตล์: ${artist.styleHint}",
                            color = KaraokeColors.TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onSelectArtist(artist) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.colors(
                                    containerColor = KaraokeColors.Primary,
                                    contentColor = Color.White,
                                    focusedContainerColor = Color(0xFFD97706),
                                    focusedContentColor = Color.White
                                )
                            ) {
                                Text("ดูเพลง", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onToggleFavoriteArtist(artist) },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isFav) Color(0xFFFEF3C7) else Color(0xFFF1F5F9),
                                    contentColor = if (isFav) Color(0xFFB45309) else KaraokeColors.TextPrimary,
                                    focusedContainerColor = KaraokeColors.Primary,
                                    focusedContentColor = Color.White
                                )
                            ) {
                                Text(if (isFav) "★ โปรด" else "☆ โปรด", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelsScreen(
    channels: List<ChannelProfile>,
    onSelectChannel: (ChannelProfile) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Text(
            text = "เลือกตามช่องคาราโอเกะ (${channels.size} ช่อง)",
            color = KaraokeColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(channels, key = { it.name }) { channel ->
                Card(
                    onClick = { onSelectChannel(channel) },
                    modifier = Modifier.height(110.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(channel.accentColorHex).copy(alpha = 0.25f))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = channel.name,
                            color = KaraokeColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${channel.songCount} เพลง • ${channel.styleHint}",
                            color = KaraokeColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StylesScreen(
    styles: List<MusicStyle>,
    onSelectStyle: (MusicStyle) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Text(
            text = "เลือกตามสไตล์เพลง",
            color = KaraokeColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(styles, key = { it.id }) { style ->
                Card(
                    onClick = { onSelectStyle(style) },
                    modifier = Modifier.height(110.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(style.accentColorHex).copy(alpha = 0.28f))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = style.name,
                            color = KaraokeColors.TextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (style.songCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${style.songCount} เพลง",
                                color = KaraokeColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ErasScreen(
    eras: List<MusicStyle>,
    onSelectEra: (MusicStyle) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Text(
            text = "เลือกตามยุคของเพลง (พ.ศ. 2489 – ปัจจุบัน)",
            color = KaraokeColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 260.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(eras, key = { it.name }) { era ->
                Card(
                    onClick = { onSelectEra(era) },
                    modifier = Modifier.height(110.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(era.accentColorHex).copy(alpha = 0.28f))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = era.name,
                            color = KaraokeColors.TextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (era.songCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${era.songCount} เพลง",
                                color = KaraokeColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favorites: List<SongItem>,
    onPlayNow: (SongItem) -> Unit,
    onAddToQueue: (SongItem) -> Unit,
    onToggleFavorite: (SongItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Text(
            text = "รายการเพลงโปรด (${favorites.size} เพลง)",
            color = KaraokeColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KaraokeColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ยังไม่มีเพลงโปรด — กดปุ่ม ♡ บนการ์ดเพลงเพื่อบันทึกไว้ร้องซ้ำ",
                    color = KaraokeColors.TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 250.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(favorites, key = { it.videoId }) { song ->
                    SongCard(
                        song = song,
                        onPlayNow = onPlayNow,
                        onAddToQueue = onAddToQueue,
                        isFavorite = true,
                        onToggleFavorite = onToggleFavorite
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QueueScreen(
    queueItems: List<QueueItemEntity>,
    favorites: Set<String>,
    onPlayQueueItem: (Long) -> Unit,
    onRemoveQueueItem: (Long) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onSkipToNext: () -> Unit,
    onClearWaiting: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "คิวเพลงในเครื่อง (SQLite: ${queueItems.size} เพลง)",
                color = KaraokeColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (queueItems.any { it.status == QueueStatus.PLAYING }) {
                    Button(onClick = onOpenPlayer) {
                        Text("เปิดหน้าจอเล่นเพลง")
                    }
                    OutlinedButton(onClick = onSkipToNext) {
                        Text("ข้ามไปเพลงถัดไป")
                    }
                }
                if (queueItems.any { it.status == QueueStatus.WAITING }) {
                    OutlinedButton(onClick = onClearWaiting) {
                        Text("ล้างคิวที่รอ")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (queueItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KaraokeColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ยังไม่มีเพลงในคิว — ค้นหาและกด '+ คิว' เพื่อเพิ่มเพลง",
                    color = KaraokeColors.TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(queueItems, key = { _, item -> item.id }) { index, item ->
                    val isPlaying = item.status == QueueStatus.PLAYING
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isPlaying) KaraokeColors.Primary.copy(alpha = 0.22f)
                                else KaraokeColors.Surface
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPlaying) "▶ กำลังเล่น: ${item.title}" else "${index + 1}. ${item.title}",
                                color = KaraokeColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${item.artistName} • ${item.styleTag}",
                                color = KaraokeColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val isFav = favorites.contains(item.videoId)
                            OutlinedButton(
                                onClick = {
                                    onToggleFavorite(
                                        SongItem(
                                            videoId = item.videoId,
                                            title = item.title,
                                            artistName = item.artistName,
                                            styleTag = item.styleTag,
                                            thumbnailUrl = item.thumbnailUrl,
                                            channelTitle = ""
                                        )
                                    )
                                }
                            ) {
                                Text(if (isFav) "❤️ โปรดแล้ว" else "🤍 โปรด")
                            }
                            if (!isPlaying) {
                                Button(onClick = { onPlayQueueItem(item.id) }) {
                                    Text("เล่นเลย")
                                }
                            }
                            OutlinedButton(onClick = { onRemoveQueueItem(item.id) }) {
                                Text("ลบ")
                            }
                        }
                    }
                }
            }
        }
    }
}
