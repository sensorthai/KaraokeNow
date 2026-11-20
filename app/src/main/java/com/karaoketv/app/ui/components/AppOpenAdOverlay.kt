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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.karaoketv.app.ads.AppOpenAdData
import com.karaoketv.app.ui.theme.KaraokeColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppOpenAdOverlay(
    ad: AppOpenAdData,
    onDismiss: () -> Unit
) {
    var secondsLeft by remember(ad) { mutableIntStateOf(ad.durationSeconds.coerceAtLeast(1)) }
    val skipFocusRequester = remember { FocusRequester() }

    LaunchedEffect(ad) {
        runCatching { skipFocusRequester.requestFocus() }
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEBEEF1F5)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(0.78f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFF3F5F9)
                        )
                    )
                )
                .border(1.dp, KaraokeColors.Primary.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = ad.badge,
                        color = Color(0xFFF59E0B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "ปิดอัตโนมัติใน ${secondsLeft} วินาที",
                    color = KaraokeColors.TextSecondary,
                    fontSize = 13.sp
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
                        .height(if (mode == "image") 320.dp else 220.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            if (showText) {
                Text(
                    text = ad.title,
                    color = KaraokeColors.TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = ad.subtitle,
                    color = KaraokeColors.TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(skipFocusRequester),
                    colors = ButtonDefaults.colors(
                        containerColor = KaraokeColors.Primary,
                        focusedContainerColor = Color(0xFFF59E0B),
                        contentColor = KaraokeColors.TextPrimary,
                        focusedContentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "ข้ามโฆษณา (${secondsLeft}s)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
