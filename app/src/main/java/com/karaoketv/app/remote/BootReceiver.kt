package com.karaoketv.app.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i("BootReceiver", "Received action: $action -> starting KaraokeServerService")
        KaraokeServerService.start(context)
    }
}
