package com.lostpenguin.app.util

import android.content.Context
import com.lostpenguin.app.data.AppDatabase
import com.lostpenguin.app.data.ReelStat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object ReelCounterManager {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun getTodayDate(): String = dateFormat.format(Date())

    fun getTodayCount(context: Context): Int {
        val prefs = context.getSharedPreferences("lostpenguin_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("count_${getTodayDate()}", 0)
    }

    fun increment(context: Context) {
        val today = getTodayDate()
        val prefs = context.getSharedPreferences("lostpenguin_prefs", Context.MODE_PRIVATE)
        val newCount = prefs.getInt("count_$today", 0) + 1
        prefs.edit().putInt("count_$today", newCount).apply()

        // Persist to Room DB
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getInstance(context).reelStatDao()
            dao.upsert(ReelStat(date = today, count = newCount))
        }
    }
}
