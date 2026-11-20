package com.karaoketv.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.karaoketv.app.data.local.QueueItemEntity
import com.karaoketv.app.ui.components.YouTubePlayerView
import com.karaoketv.app.ui.theme.KaraokeColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    currentSong: QueueItemEntity,
    waitingCount: Int,
    onSongEnded: () -> Unit,
    onSkipNext: () -> Unit,
    onBackToMenu: () -> Unit
) {
    val context = LocalContext.current
    val useExternalYouTubeApp = remember(context) { isLegacyWebView(context) }

    fun launchYouTubeTv(videoId: String) {
        val videoUri = Uri.parse("https://www.youtube.com/watch?v=$videoId")
        val tvIntent = Intent(Intent.ACTION_VIEW, videoUri).apply {
            setPackage("com.google.android.youtube.tv")
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val mobileIntent = Intent(Intent.ACTION_VIEW, videoUri).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fallbackIntent = Intent(Intent.ACTION_VIEW, videoUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(tvIntent) }
            .recoverCatching { context.startActivity(mobileIntent) }
            .recoverCatching { context.startActivity(fallbackIntent) }
    }

    LaunchedEffect(currentSong.videoId, useExternalYouTubeApp) {
        if (useExternalYouTubeApp) {
            launchYouTubeTv(currentSong.videoId)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!useExternalYouTubeApp) {
            YouTubePlayerView(
                videoId = currentSong.videoId,
                modifier = Modifier.fillMaxSize(),
                onVideoEnded = onSongEnded
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(KaraokeColors.Surface)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "กำลังเล่นผ่านแอป YouTube TV",
                    color = KaraokeColors.Primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${currentSong.title} • ${currentSong.artistName}",
                    color = KaraokeColors.TextPrimary,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { launchYouTubeTv(currentSong.videoId) }) {
                        Text("เปิดเล่นใน YouTube อีกครั้ง")
                    }
                    OutlinedButton(onClick = onSkipNext) {
                        Text("เพลงถัดไป ($waitingCount)")
                    }
                    OutlinedButton(onClick = onBackToMenu) {
                        Text("กลับเมนูหลัก")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentSong.title,
                    color = KaraokeColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${currentSong.artistName} • คิวถัดไป: $waitingCount เพลง",
                    color = KaraokeColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onSkipNext) {
                    Text("เพลงถัดไป")
                }
                OutlinedButton(onClick = { launchYouTubeTv(currentSong.videoId) }) {
                    Text("เปิดใน YouTube")
                }
                OutlinedButton(onClick = onBackToMenu) {
                    Text("กลับเมนูหลัก")
                }
            }
        }
    }
}

private fun isLegacyWebView(context: Context): Boolean {
    val packages = listOf("com.google.android.webview", "com.android.webview")
    for (pkg in packages) {
        val versionName = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pkg, 0).versionName
        }.getOrNull() ?: continue
        val major = versionName.substringBefore(".").toIntOrNull() ?: continue
        return major < 70
    }
    return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O
}
