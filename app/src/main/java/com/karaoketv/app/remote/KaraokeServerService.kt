package com.karaoketv.app.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.karaoketv.app.R
import com.karaoketv.app.data.local.KaraokeDatabase
import com.karaoketv.app.data.repository.YouTubeRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class KaraokeServerService : Service() {

    private var localServer: LocalKaraokeServer? = null
    private var mdnsResponder: MdnsResponder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        val dao = KaraokeDatabase.getInstance(applicationContext).karaokeDao()
        val repository = YouTubeRepository(dao)

        localServer = LocalKaraokeServer(
            dao = dao,
            repository = repository,
            onPlayTriggeredFromWeb = {
                _playTriggeredFromWeb.tryEmit(Unit)
            }
        ).also {
            it.start()
            currentServerUrl = it.getServerUrl()
        }

        mdnsResponder = MdnsResponder(
            context = applicationContext,
            hostLabel = "karaokenow",
            port = 8888
        ).also {
            it.start()
        }
        Log.i(TAG, "KaraokeServerService started (server=${currentServerUrl}, mDNS=karaokenow.local:8888)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        localServer?.let {
            it.start()
            currentServerUrl = it.getServerUrl()
        }
        mdnsResponder?.start()
        return START_STICKY
    }

    override fun onDestroy() {
        mdnsResponder?.stop()
        localServer?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        val channelId = "karaokenow_remote_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "KaraokeNow Remote & mDNS",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("KaraokeNow Remote")
            .setContentText("พร้อมใช้งานผ่าน http://karaokenow.local:8888")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "KaraokeServerService"
        private const val NOTIFICATION_ID = 8888

        @Volatile
        var currentServerUrl: String = "http://127.0.0.1:8888"
            private set

        private val _playTriggeredFromWeb = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val playTriggeredFromWeb: SharedFlow<Unit> = _playTriggeredFromWeb.asSharedFlow()

        fun start(context: Context) {
            val intent = Intent(context.applicationContext, KaraokeServerService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            }.onFailure {
                Log.w(TAG, "Failed to start service: ${it.message}")
            }
        }
    }
}
