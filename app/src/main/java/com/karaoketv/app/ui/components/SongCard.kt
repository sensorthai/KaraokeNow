package com.karaoketv.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.karaoketv.app.data.local.ThumbnailCacheManager
import com.karaoketv.app.data.model.SongItem
import com.karaoketv.app.ui.theme.KaraokeColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SongCard(
    song: SongItem,
    onPlayNow: (SongItem) -> Unit,
    onAddToQueue: (SongItem) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: ((SongItem) -> Unit)? = null,
    showQueueButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var thumbnailBitmap by remember(song.thumbnailUrl) {
        mutableStateOf(ThumbnailCacheManager.getFromMemory(song.thumbnailUrl))
    }

    LaunchedEffect(song.thumbnailUrl) {
        if (thumbnailBitmap == null) {
            thumbnailBitmap = ThumbnailCacheManager.load(context, song.thumbnailUrl)
        }
    }

    Column(
        modifier = modifier
            .width(260.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(KaraokeColors.Surface)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KaraokeColors.SurfaceElevated)
        ) {
            thumbnailBitmap?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (song.songCode.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(KaraokeColors.Primary.copy(alpha = 0.9f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "#${song.songCode}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = song.styleTag,
                    color = KaraokeColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = song.title,
            color = KaraokeColors.TextPrimary,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = song.artistName,
            color = KaraokeColors.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showQueueButton) {
                Button(
                    onClick = { onAddToQueue(song) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "+ คิว", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onPlayNow(song) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "ร้องเลย", fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = { onPlayNow(song) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "▶ ร้องเลย", fontSize = 12.sp)
                }
            }
            if (onToggleFavorite != null) {
                OutlinedButton(
                    onClick = { onToggleFavorite(song) }
                ) {
                    Text(text = if (isFavorite) "♥" else "♡", fontSize = 14.sp)
                }
            }
        }
    }
}
