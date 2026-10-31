package com.lostpenguin.app.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogFileWriter {

    private var logFile: File? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        logFile = File(dir, "lostpenguin_debug.txt")
        // Keep last 500KB — truncate if too large
        if (logFile!!.exists() && logFile!!.length() > 500_000) {
            logFile!!.delete()
        }
        append("=== Session started ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())} ===")
    }

    fun append(message: String) {
        try {
            val line = "[${timeFormat.format(Date())}] $message\n"
            logFile?.appendText(line)
        } catch (_: Exception) {}
    }

    fun getFile(): File? = logFile
}
