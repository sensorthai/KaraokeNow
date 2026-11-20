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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.karaoketv.app.data.local.QueueItemEntity
import com.karaoketv.app.data.model.ArtistProfile
import com.karaoketv.app.data.model.MusicStyle
import com.karaoketv.app.data.model.SongItem
import com.karaoketv.app.ui.components.SongCard
import com.karaoketv.app.ui.theme.KaraokeColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    currentPlaying: QueueItemEntity?,
    queueCount: Int,
    popularSongs: List<SongItem>,
    hasPlayStats: Boolean = false,
    styles: List<MusicStyle>,
    artists: List<ArtistProfile>,
    favoriteIds: Set<String> = emptySet(),
    onPlayNow: (SongItem) -> Unit,
    onAddToQueue: (SongItem) -> Unit,
    onToggleFavorite: (SongItem) -> Unit = {},
    onSelectStyle: (MusicStyle) -> Unit,
    onSelectArtist: (ArtistProfile) -> Unit,
    onOpenPlayer: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KaraokeColors.Surface)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentPlaying != null) "กำลังเล่น: ${currentPlaying.title}" else "พร้อมร้องคาราโอเกะบน Android TV",
                        color = KaraokeColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (currentPlaying != null) {
                            "${currentPlaying.artistName} • คิวรอเล่น $queueCount เพลง"
                        } else {
                            "ค้นหาจาก YouTube ด้วยชื่อเพลง รายชื่อเพลง นักร้อง หรือสไตล์เพลง"
                        },
                        color = KaraokeColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
                if (currentPlaying != null) {
                    Button(onClick = onOpenPlayer) {
                        Text("เปิดหน้าจอเล่นเพลง")
                    }
                }
            }
        }

        item {
            SectionTitle(
                if (hasPlayStats) "เพลงฮิตคาราโอเกะ (จัดอันดับจากยอดเล่นสูงสุด)" else "เพลงฮิตคาราโอเกะ"
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(end = 24.dp)
            ) {
                items(popularSongs, key = { it.videoId }) { song ->
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

        item {
            SectionTitle("เลือกตามสไตล์เพลง")
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(end = 24.dp)
            ) {
                items(styles, key = { it.id }) { style ->
                    Card(
                        onClick = { onSelectStyle(style) },
                        modifier = Modifier
                            .width(190.dp)
                            .height(84.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(style.accentColorHex).copy(alpha = 0.25f))
                                .padding(16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = style.name,
                                color = KaraokeColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("นักร้องยอดนิยม")
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(end = 24.dp)
            ) {
                items(artists, key = { it.name }) { artist ->
                    Card(
                        onClick = { onSelectArtist(artist) },
                        modifier = Modifier
                            .width(190.dp)
                            .height(84.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(KaraokeColors.Surface)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = artist.name,
                                color = KaraokeColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = artist.styleHint,
                                color = KaraokeColors.TextSecondary,
                                fontSize = 12.sp
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
fun SectionTitle(title: String) {
    Text(
        text = title,
        color = KaraokeColors.TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
    )
}
