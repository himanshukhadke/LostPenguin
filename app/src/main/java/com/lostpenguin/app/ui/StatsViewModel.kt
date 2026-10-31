package com.lostpenguin.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lostpenguin.app.data.AppDatabase
import com.lostpenguin.app.data.ReelStat
import com.lostpenguin.app.util.ReelCounterManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
}
