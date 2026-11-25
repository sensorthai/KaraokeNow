package com.karaoketv.app.ota

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.karaoketv.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class OtaManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    val minAndroidSdk: Int = 21,
    val forceUpdate: Boolean = false,
    val apkUrl: String = "",
    val sha256: String = "",
    val changelog: List<String> = emptyList()
)

sealed class OtaState {
    object Idle : OtaState()
    object Checking : OtaState()
    data class UpdateAvailable(val manifest: OtaManifest) : OtaState()
    data class Downloading(val manifest: OtaManifest, val progressPercent: Int) : OtaState()
    data class ReadyToInstall(val manifest: OtaManifest, val apkFile: File) : OtaState()
    data class Error(val message: String) : OtaState()
}

object OtaUpdateManager {
    private const val TAG = "OtaUpdate"
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/sensorthai/KaraokeNow/main/version.json"

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _state = MutableStateFlow<OtaState>(OtaState.Idle)
    val state: StateFlow<OtaState> = _state.asStateFlow()

    @Volatile
    private var hasCheckedThisLaunch = false

    suspend fun checkForUpdate(force: Boolean = false) {
        if (hasCheckedThisLaunch && !force) return
        hasCheckedThisLaunch = true
        if (force) _state.value = OtaState.Checking

        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(MANIFEST_URL).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        if (force) _state.value = OtaState.Error("ตรวจสอบเวอร์ชันไม่สำเร็จ (HTTP ${resp.code})")
                        return@use
                    }
                    val body = resp.body?.string().orEmpty()
                    val manifest = gson.fromJson(body, OtaManifest::class.java)
                    if (manifest != null &&
                        manifest.versionCode > BuildConfig.VERSION_CODE &&
                        Build.VERSION.SDK_INT >= manifest.minAndroidSdk &&
                        manifest.apkUrl.isNotBlank()
                    ) {
                        _state.value = OtaState.UpdateAvailable(manifest)
                    } else if (force) {
                        _state.value = OtaState.Idle
                    }
                }
            }.onFailure {
                Log.w(TAG, "OTA check failed: ${it.message}")
                if (force) _state.value = OtaState.Error("ไม่สามารถเชื่อมต่อเซิร์ฟเวอร์อัปเดตได้")
            }
        }
    }

    suspend fun downloadAndVerify(context: Context, manifest: OtaManifest) {
        withContext(Dispatchers.IO) {
            val otaDir = File(context.cacheDir, "ota").apply { mkdirs() }
            val targetFile = File(otaDir, "karaokenow-update.apk")
            if (targetFile.exists()) targetFile.delete()

            if (otaDir.usableSpace < 50L * 1024L * 1024L) {
                _state.value = OtaState.Error("พื้นที่ในเครื่องไม่เพียงพอสำหรับการดาวน์โหลดอัปเดต")
                return@withContext
            }

            _state.value = OtaState.Downloading(manifest, 0)
            runCatching {
                val req = Request.Builder().url(manifest.apkUrl).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        _state.value = OtaState.Error("ดาวน์โหลดไม่สำเร็จ (HTTP ${resp.code})")
                        return@use
                    }
                    val body = resp.body ?: run {
                        _state.value = OtaState.Error("ไม่พบไฟล์อัปเดต")
                        return@use
                    }
                    val totalBytes = body.contentLength()
                    var downloaded = 0L
                    var lastPct = -1

                    body.byteStream().use { input ->
                        targetFile.outputStream().use { output ->
                            val buf = ByteArray(16 * 1024)
                            while (true) {
                                val read = input.read(buf)
                                if (read == -1) break
                                output.write(buf, 0, read)
                                downloaded += read
                                if (totalBytes > 0) {
                                    val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        _state.value = OtaState.Downloading(manifest, pct)
                                    }
                                }
                            }
                        }
                    }

                    if (manifest.sha256.isNotBlank()) {
                        val actualHash = sha256Hex(targetFile)
                        if (!actualHash.equals(manifest.sha256.trim(), ignoreCase = true)) {
                            targetFile.delete()
                            _state.value = OtaState.Error("ไฟล์อัปเดตไม่สมบูรณ์ (SHA-256 ไม่ตรงกัน)")
                            return@use
                        }
                    }

                    _state.value = OtaState.ReadyToInstall(manifest, targetFile)
                }
            }.onFailure {
                targetFile.delete()
                _state.value = OtaState.Error("ดาวน์โหลดอัปเดตล้มเหลว: ${it.message}")
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permIntent)
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }

    fun dismiss() {
        _state.value = OtaState.Idle
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r == -1) break
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
