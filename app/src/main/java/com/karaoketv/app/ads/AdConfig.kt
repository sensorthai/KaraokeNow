package com.karaoketv.app.ads

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class AppOpenAdData(
    val enabled: Boolean = true,
    val displayMode: String = "both", // "text", "image", or "both"
    val badge: String = "SPONSORED",
    val title: String = "Karaoke Now — คาราโอเกะฟรีสำหรับ Android TV",
    val subtitle: String = "พัฒนาโดย บริษัท ไวร์เลส โซลูชั่น เอเชีย จำกัด",
    val imageUrl: String = "",
    val ctaText: String = "wirelesssolution.asia",
    val durationSeconds: Int = 5
)

data class RemoteBannerAdData(
    val enabled: Boolean = true,
    val displayMode: String = "both", // "text", "image", or "both"
    val badge: String = "AD",
    val title: String = "Karaoke Now — Wireless Solution Asia",
    val subtitle: String = "พัฒนาโดย บริษัท ไวร์เลส โซลูชั่น เอเชีย จำกัด",
    val targetUrl: String = "https://www.wirelesssolution.asia",
    val ctaText: String = "เยี่ยมชมเว็บไซต์",
    val imageUrl: String = ""
)

data class AdConfig(
    val appOpen: AppOpenAdData = AppOpenAdData(),
    val remoteBanner: RemoteBannerAdData = RemoteBannerAdData()
)

object AdConfigRepository {
    // Editable ad config hosted on GitHub (update ads.json on main to refresh TV & mobile remote ads)
    private const val REMOTE_AD_CONFIG_URL =
        "https://raw.githubusercontent.com/sensorthai/KaraokeNow/main/ads.json"

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
