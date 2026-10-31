package com.lostpenguin.app.util

import android.content.Context

object ScrollDebugLog {
    private val logs = ArrayDeque<String>()
    private val listeners = mutableListOf<() -> Unit>()

    fun init(context: Context) {
        LogFileWriter.init(context)
    }

    fun log(message: String) {
        android.util.Log.d("LostPenguin", message)
        LogFileWriter.append(message)
        if (logs.size >= 100) logs.removeFirst()
        logs.addLast(message)
        listeners.forEach { it() }
    }

    fun clear() {
        logs.clear()
        LogFileWriter.append("=== Log cleared by user ===")
        listeners.forEach { it() }
    }

    fun getLogs(): List<String> = logs.toList().reversed()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
}
