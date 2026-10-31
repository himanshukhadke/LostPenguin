package com.lostpenguin.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (completedId == UpdateManager.downloadId) {
            // Just mark as ready — install is triggered by user tapping "Install Now"
            // from foreground (background startActivity is blocked on Android 10+)
            UpdateManager.markReadyToInstall()
        }
    }
}
