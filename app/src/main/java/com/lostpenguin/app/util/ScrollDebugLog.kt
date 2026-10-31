package com.lostpenguin.app.util

object ScrollDebugLog {
    private val logs = ArrayDeque<String>(maxSize = 50)
    private val listeners = mutableListOf<() -> Unit>()

    fun log(message: String) {
        android.util.Log.d("LostPenguin", message)
        if (logs.size >= 50) logs.removeFirst()
        logs.addLast(message)
        listeners.forEach { it() }
    }

    fun getLogs(): List<String> = logs.toList().reversed()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    private fun ArrayDeque(maxSize: Int) = ArrayDeque<String>()
}
