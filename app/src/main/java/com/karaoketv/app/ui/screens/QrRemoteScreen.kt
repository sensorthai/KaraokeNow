package com.karaoketv.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.karaoketv.app.ui.theme.KaraokeColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QrRemoteScreen(
    serverUrl: String,
    mdnsUrl: String,
    queueCount: Int,
    totalSongsCount: Int
) {
    val qrBitmap = remember(serverUrl) { generateQrBitmap(serverUrl, 420) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            qrBitmap?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = "QR Code สำหรับเปิดเว็บสั่งเพลง",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "สแกน QR Code เพื่อสั่งเพลงจากมือถือ",
                color = KaraokeColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "เชื่อมต่อ Wi-Fi เดียวกันกับทีวี แล้วสแกน QR Code หรือเปิดลิงก์ด้านล่างบนเบราว์เซอร์มือถือ",
                color = KaraokeColors.TextSecondary,
                fontSize = 15.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(KaraokeColors.Surface)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = mdnsUrl,
                        color = KaraokeColors.Primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "หรือ $serverUrl",
                        color = KaraokeColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "• ค้นหาจากเพลงทั้งหมด $totalSongsCount เพลง (รวมยุค 70s–90s)\n" +
                    "• เลือกดูตาม นักร้อง / ช่อง / สไตล์เพลง\n" +
                    "• กด + คิว, ร้องเลย, ข้ามเพลง หรือจัดการคิว (คิวปัจจุบัน: $queueCount เพลง)",
                color = KaraokeColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "พัฒนาโดย บริษัท ไวร์เลส โซลูชั่น เอเชีย จำกัด • https://www.wirelesssolution.asia",
                color = KaraokeColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun generateQrBitmap(content: String, sizePx: Int): ImageBitmap? {
    return runCatching {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bmp.asImageBitmap()
    }.getOrNull()
}
