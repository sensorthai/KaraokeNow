package com.karaoketv.app.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.karaoketv.app.data.model.SongItem
import com.karaoketv.app.ui.theme.KaraokeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private object ThumbnailLoader {
    private val cache = LruCache<String, ImageBitmap>(120)
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        android.util.Log.e("ThumbLoader", "HTTP ${resp.code} for $url")
                        return@use null
                    }
                    val bytes = resp.body?.bytes() ?: return@use null
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use null
                    android.util.Log.d("ThumbLoader", "Decoded ${bmp.width}x${bmp.height} for $url")
                    val img = bmp.asImageBitmap()
                    cache.put(url, img)
                    img
                }
            }.onFailure {
                android.util.Log.e("ThumbLoader", "Failed $url", it)
            }.getOrNull()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SongCard(
    song: SongItem,
    onPlayNow: (SongItem) -> Unit,
    onAddToQueue: (SongItem) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: ((SongItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var thumbnailBitmap by remember(song.thumbnailUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(song.thumbnailUrl) {
        thumbnailBitmap = ThumbnailLoader.load(song.thumbnailUrl)
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
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = song.styleTag,
                    color = KaraokeColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = song.title,
            color = KaraokeColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
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
