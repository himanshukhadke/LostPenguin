package com.lostpenguin.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (completedId == UpdateManager.downloadId) {
            // Small delay to ensure file is fully flushed before install
            Handler(Looper.getMainLooper()).postDelayed({
                UpdateManager.onDownloadComplete(context.applicationContext)
            }, 500)
        }
    }
}
