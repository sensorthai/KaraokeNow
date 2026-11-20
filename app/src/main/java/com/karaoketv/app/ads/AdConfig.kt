package com.karaoketv.app.ads

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class AppOpenAdData(
    val enabled: Boolean = true,
    val badge: String = "SPONSORED",
    val title: String = "คาราโอเกะ นาว (KaraokeNow) — ฟรีตลอดชีพบน GitHub",
    val subtitle: String = "พื้นที่โฆษณา / ผู้สนับสนุนเมื่อเปิดแอป • รองรับอัปเดตแคมเปญจาก Remote JSON อัตโนมัติ",
    val imageUrl: String = "",
    val ctaText: String = "สนับสนุนโปรเจกต์บน GitHub",
    val durationSeconds: Int = 5
)

data class RemoteBannerAdData(
    val enabled: Boolean = true,
    val badge: String = "AD",
    val title: String = "KaraokeNow Open Source",
    val subtitle: String = "พื้นที่โฆษณารีโมทมือถือ — ปรับแต่งลิงก์ผู้สนับสนุนหรือแบนเนอร์ได้จาก AdConfig",
    val targetUrl: String = "https://github.com",
    val ctaText: String = "ดูเพิ่มเติม",
    val imageUrl: String = ""
)

data class AdConfig(
    val appOpen: AppOpenAdData = AppOpenAdData(),
    val remoteBanner: RemoteBannerAdData = RemoteBannerAdData()
)

object AdConfigRepository {
    // Optional: Point this to a raw GitHub JSON URL (e.g. https://raw.githubusercontent.com/<user>/<repo>/main/ads.json)
    // to update TV & mobile remote ads dynamically across all distributed APKs.
    private const val REMOTE_AD_CONFIG_URL = ""

    private val gson = Gson()

    @Volatile
    var currentConfig: AdConfig = AdConfig()
        private set

    suspend fun refreshIfConfigured(): AdConfig {
        if (REMOTE_AD_CONFIG_URL.isBlank()) return currentConfig
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(REMOTE_AD_CONFIG_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2_000
                    readTimeout = 2_000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().use { it.readText() }
                    gson.fromJson(json, AdConfig::class.java)?.also {
                        currentConfig = it
                    } ?: currentConfig
                } else {
                    currentConfig
                }
            }.getOrDefault(currentConfig)
        }
    }
}
