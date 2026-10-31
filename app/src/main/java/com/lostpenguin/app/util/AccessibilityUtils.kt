package com.lostpenguin.app.util

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object AccessibilityUtils {
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedService = "${context.packageName}/.service.ReelAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
            .asSequence()
            .any { it.equals(expectedService, ignoreCase = true) }
    }
}
