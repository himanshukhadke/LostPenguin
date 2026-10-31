package com.lostpenguin.app.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
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
    private var pendingApkFile: File? = null

    fun setAvailable(version: String, downloadUrl: String) {
        _state.value = UpdateState.Available(version, downloadUrl)
    }

    fun startDownload(context: Context, downloadUrl: String) {
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "LostPenguin-update.apk")
        if (apkFile.exists()) apkFile.delete()
        pendingApkFile = apkFile

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("LostPenguin Update")
            .setDescription("Downloading latest version...")
            .setDestinationUri(Uri.fromFile(apkFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)
        _state.value = UpdateState.Downloading(0)
    }

    fun updateProgress(progress: Int) {
        _state.value = UpdateState.Downloading(progress)
    }

    fun onDownloadComplete(context: Context) {
        _state.value = UpdateState.ReadyToInstall
        installApk(context)
    }

    fun installApk(context: Context) {
        val apkFile = pendingApkFile ?: return
        if (!apkFile.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    fun reset() {
        _state.value = UpdateState.Idle
        downloadId = -1L
        pendingApkFile = null
    }
}
