package com.lostpenguin.app.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.lostpenguin.app.util.ScrollDebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

sealed class UpdateState {
    object Idle : UpdateState()
    data class Available(val version: String, val downloadUrl: String) : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    object ReadyToInstall : UpdateState()
}

object UpdateManager {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    var downloadId: Long = -1L
    private const val APK_FILENAME = "LostPenguin-update.apk"
    private const val PREFS_NAME = "update_prefs"
    private const val KEY_DOWNLOAD_ID = "download_id"

    fun setAvailable(version: String, downloadUrl: String) {
        _state.value = UpdateState.Available(version, downloadUrl)
    }

    fun startDownload(context: Context, downloadUrl: String) {
        // Delete old file if exists
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILENAME)
        if (apkFile.exists()) apkFile.delete()

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("LostPenguin Update")
            .setDescription("Downloading latest version...")
            // Use setDestinationInExternalFilesDir — avoids Uri.fromFile SecurityException on Android 7+
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, APK_FILENAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)

        // Persist downloadId so DownloadCompleteReceiver can match it after process restart
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_DOWNLOAD_ID, downloadId).apply()

        _state.value = UpdateState.Downloading(0)
    }

    fun updateProgress(progress: Int) {
        if (_state.value is UpdateState.Downloading) {
            _state.value = UpdateState.Downloading(progress)
        }
    }

    fun markReadyToInstall() {
        _state.value = UpdateState.ReadyToInstall
    }

    fun installApk(context: Context) {
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILENAME)

        ScrollDebugLog.log("installApk: file=${apkFile.absolutePath} exists=${apkFile.exists()} size=${apkFile.length()}")

        if (!apkFile.exists()) {
            ScrollDebugLog.log("ERROR: APK file not found — resetting update state")
            reset(context)
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            ScrollDebugLog.log("installApk: uri=$uri")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            ScrollDebugLog.log("installApk: startActivity called OK")
        } catch (e: Exception) {
            ScrollDebugLog.log("ERROR: installApk failed: ${e.javaClass.simpleName}: ${e.message}")
            reset(context)
        }
    }

    fun reset(context: Context? = null) {
        _state.value = UpdateState.Idle
        downloadId = -1L
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.remove(KEY_DOWNLOAD_ID)?.apply()
    }

    // Called on app start to restore downloadId if process was killed mid-download
    fun restoreDownloadId(context: Context) {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_DOWNLOAD_ID, -1L)
        if (saved != -1L) downloadId = saved
    }
}
