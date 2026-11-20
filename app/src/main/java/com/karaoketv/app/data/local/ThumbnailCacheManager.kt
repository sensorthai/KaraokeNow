package com.karaoketv.app.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object ThumbnailCacheManager {
    private const val TAG = "ThumbnailCache"
    private const val DISK_DIR = "thumbnails"

    private val memoryCache = LruCache<String, ImageBitmap>(180)
    private val isPreloading = AtomicBoolean(false)

    @Volatile
    private var cacheDir: File? = null

    private val client: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 24
            maxRequestsPerHost = 12
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    fun init(context: Context) {
        if (cacheDir == null) {
            cacheDir = File(context.applicationContext.filesDir, DISK_DIR).apply {
                if (!exists()) mkdirs()
            }
        }
    }

    fun getFromMemory(url: String): ImageBitmap? {
        if (url.isBlank()) return null
        return memoryCache.get(url)
    }

    suspend fun load(context: Context, url: String): ImageBitmap? {
        if (url.isBlank()) return null
        memoryCache.get(url)?.let { return it }
        init(context)

        return withContext(Dispatchers.IO) {
            // 1. Check local disk cache first
            val diskFile = fileForUrl(url)
            if (diskFile != null && diskFile.exists() && diskFile.length() > 0L) {
                val bmp = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bmp != null) {
                    val img = bmp.asImageBitmap()
                    memoryCache.put(url, img)
                    return@withContext img
                } else {
                    diskFile.delete()
                }
            }

            // 2. Download from network, save to local disk, and cache in memory
            val bytes = downloadBytes(url) ?: return@withContext null
            writeBytesToDisk(diskFile, bytes)

            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            val img = bmp.asImageBitmap()
            memoryCache.put(url, img)
            img
        }
    }

    /**
     * Pre-downloads all song thumbnails onto local disk in the background so
     * song cards render immediately without waiting on network per scroll.
     */
    suspend fun preloadAll(context: Context, urls: List<String>) {
        if (urls.isEmpty()) return
        init(context)
        if (!isPreloading.compareAndSet(false, true)) return

        try {
            withContext(Dispatchers.IO) {
                val missing = urls
                    .asSequence()
                    .filter { it.isNotBlank() }
                    .distinct()
                    .filter { url ->
                        val f = fileForUrl(url)
                        f != null && (!f.exists() || f.length() == 0L)
                    }
                    .toList()

                if (missing.isEmpty()) {
                    Log.d(TAG, "All thumbnails already cached on local disk")
                    return@withContext
                }

                Log.d(TAG, "Preloading ${missing.size} thumbnails to local disk...")
                val semaphore = Semaphore(8)
                var downloaded = 0
                coroutineScope {
                    missing.map { url ->
                        async {
                            semaphore.withPermit {
                                val file = fileForUrl(url) ?: return@withPermit
                                if (file.exists() && file.length() > 0L) return@withPermit
                                val bytes = downloadBytes(url)
                                if (bytes != null && bytes.isNotEmpty()) {
                                    writeBytesToDisk(file, bytes)
                                    downloaded++
                                }
                            }
                        }
                    }.awaitAll()
                }
                Log.d(TAG, "Finished preloading $downloaded/${missing.size} thumbnails to disk")
            }
        } finally {
            isPreloading.set(false)
        }
    }

    private fun downloadBytes(url: String): ByteArray? {
        return runCatching {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "HTTP ${resp.code} for $url")
                    return@use null
                }
                resp.body?.bytes()
            }
        }.onFailure {
            Log.w(TAG, "Download failed for $url: ${it.message}")
        }.getOrNull()
    }

    private fun writeBytesToDisk(target: File?, bytes: ByteArray) {
        if (target == null || bytes.isEmpty()) return
        runCatching {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
        }
    }

    private fun fileForUrl(url: String): File? {
        val dir = cacheDir ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$digest.jpg")
    }
}
