package com.lostpenguin.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)

        // Restore downloadId from prefs in case process was restarted
        UpdateManager.restoreDownloadId(context)

        if (completedId != -1L && completedId == UpdateManager.downloadId) {
            // Verify download actually succeeded before marking ready
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(completedId)
            val cursor = dm.query(query)
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    UpdateManager.markReadyToInstall()
                } else {
                    // Download failed — reset so user can retry
                    UpdateManager.reset(context)
                }
            }
            cursor.close()
        }
    }
}
