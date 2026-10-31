package com.lostpenguin.app.ui

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lostpenguin.app.data.AppDatabase
import com.lostpenguin.app.data.ReelStat
import com.lostpenguin.app.update.DownloadProgressPoller
import com.lostpenguin.app.update.UpdateManager
import com.lostpenguin.app.update.UpdateState
import com.lostpenguin.app.util.AccessibilityUtils
import com.lostpenguin.app.util.ReelCounterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).reelStatDao()
    private val progressPoller = DownloadProgressPoller(app)

    val allStats: StateFlow<List<ReelStat>> = dao.getAllStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val last7Days: StateFlow<List<ReelStat>> = dao.getLast7Days()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> = dao.getTotalCount()
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayCount: Int
        get() = ReelCounterManager.getTodayCount(getApplication())

    private val _hasOverlayPermission = MutableStateFlow(false)
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission

    private val _hasAccessibilityPermission = MutableStateFlow(false)
    val hasAccessibilityPermission: StateFlow<Boolean> = _hasAccessibilityPermission

    val updateState: StateFlow<UpdateState> = UpdateManager.state

    init {
        UpdateManager.restoreDownloadId(app)
        checkForUpdates()
    }

    fun refreshPermissions(context: Context) {
        _hasOverlayPermission.value = Settings.canDrawOverlays(context)
        _hasAccessibilityPermission.value = AccessibilityUtils.isAccessibilityServiceEnabled(context)
    }

    private fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = URL("https://api.github.com/repos/himanshukhadke/LostPenguin/releases/latest")
                    .readText()
                val release = JSONObject(json)
                val tagName = release.getString("tag_name")  // e.g. "v0.0.9"
                val currentVersion = getApplication<Application>().packageManager
                    .getPackageInfo(getApplication<Application>().packageName, 0).versionName

                val tagVersion = tagName.removePrefix("v")
                if (tagVersion == currentVersion) return@launch

                // Find the APK download URL from release assets
                val assets = release.getJSONArray("assets")
                var apkUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                if (apkUrl.isNotEmpty()) {
                    UpdateManager.setAvailable(tagName, apkUrl)
                }
            } catch (e: Exception) {
                // No internet or no releases yet — silently ignore
            }
        }
    }

    fun startUpdate(context: Context) {
        when (val state = UpdateManager.state.value) {
            is UpdateState.Available -> {
                UpdateManager.startDownload(context, state.downloadUrl)
                progressPoller.start()
            }
            is UpdateState.ReadyToInstall -> {
                UpdateManager.installApk(context)
            }
            else -> {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressPoller.stop()
    }
}
