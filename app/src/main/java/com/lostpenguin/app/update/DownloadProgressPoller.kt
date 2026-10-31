package com.lostpenguin.app.update

import android.app.DownloadManager
import android.content.Context
import android.os.Handler
import android.os.Looper

class DownloadProgressPoller(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val pollRunnable = object : Runnable {
        override fun run() {
            val id = UpdateManager.downloadId
            if (id == -1L) return

            val query = DownloadManager.Query().setFilterById(id)
            val cursor = dm.query(query)
            if (cursor.moveToFirst()) {
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (total > 0) {
                    val progress = ((downloaded * 100) / total).toInt()
                    UpdateManager.updateProgress(progress)
                }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                    handler.postDelayed(this, 500)
                }
            }
            cursor.close()
        }
    }

    fun start() { handler.post(pollRunnable) }
    fun stop() { handler.removeCallbacks(pollRunnable) }
}
