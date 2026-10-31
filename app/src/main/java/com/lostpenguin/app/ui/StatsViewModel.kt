package com.lostpenguin.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lostpenguin.app.data.AppDatabase
import com.lostpenguin.app.data.ReelStat
import com.lostpenguin.app.util.AccessibilityUtils
import com.lostpenguin.app.util.ReelCounterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URL

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).reelStatDao()

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

    // null = no update, non-null = new version tag available
    private val _updateInfo = MutableStateFlow<String?>(null)
    val updateInfo: StateFlow<String?> = _updateInfo

    init {
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
                val release = org.json.JSONObject(json)
                val tagName = release.getString("tag_name")  // e.g. "v0.0.9"
                val currentVersion = getApplication<Application>().packageManager
                    .getPackageInfo(getApplication<Application>().packageName, 0).versionName  // e.g. "0.0.9"
                val tagVersion = tagName.removePrefix("v")  // "0.0.9"
                if (tagVersion != currentVersion) {
                    _updateInfo.value = tagName
                }
            } catch (e: Exception) {
                // No internet or no releases yet — silently ignore
            }
        }
    }

    fun openUpdate(version: String) {
        val url = "https://github.com/himanshukhadke/LostPenguin/releases/tag/$version"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        getApplication<Application>().startActivity(intent)
    }
}
