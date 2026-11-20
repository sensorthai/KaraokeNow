package com.karaoketv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.karaoketv.app.ads.AppOpenAdData
import com.karaoketv.app.data.local.QueueItemEntity
import com.karaoketv.app.ui.theme.KaraokeColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BetweenSongsAdOverlay(
    nextSong: QueueItemEntity,
    ad: AppOpenAdData,
    onFinish: () -> Unit
) {
    var secondsLeft by remember(nextSong.id, ad) { mutableIntStateOf(ad.durationSeconds.coerceAtLeast(1)) }
    val playNowFocusRequester = remember { FocusRequester() }

    LaunchedEffect(nextSong.id, ad) {
        runCatching { playNowFocusRequester.requestFocus() }
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KaraokeColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxWidth(0.84f),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top: Next Song Info Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(1.5.dp, KaraokeColors.Primary, RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⏭️ เพลงถัดไปในคิว (เริ่มใน ${secondsLeft} วินาที)",
                        color = KaraokeColors.Primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = nextSong.title,
                        color = KaraokeColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${nextSong.artistName} • ${nextSong.styleTag}",
                        color = KaraokeColors.TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                Button(
                    onClick = onFinish,
                    modifier = Modifier.focusRequester(playNowFocusRequester),
                    colors = ButtonDefaults.colors(
                        containerColor = KaraokeColors.Primary,
                        focusedContainerColor = Color(0xFFD97706),
                        contentColor = Color.White,
                        focusedContentColor = Color.White
                    )
                ) {
                    Text("เล่นเลย (${secondsLeft}s)", fontWeight = FontWeight.Bold)
                }
            }

            // Bottom: Configurable Ad / Sponsor Card (from GitHub ads.json cached on startup)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(22.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = ad.badge,
                        color = Color(0xFFB45309),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val mode = ad.displayMode.lowercase()
                val showImage = mode != "text" && ad.imageUrl.isNotBlank()
                val showText = mode != "image" || !showImage

                if (showImage) {
                    AsyncImage(
                        model = ad.imageUrl,
                        contentDescription = ad.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (mode == "image") 260.dp else 180.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                }

                if (showText) {
                    Text(
                        text = ad.title,
                        color = KaraokeColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = ad.subtitle,
                        color = KaraokeColors.TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
