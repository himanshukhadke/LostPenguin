package com.lostpenguin.app.util

import android.content.Context
import android.provider.Settings

object AccessibilityUtils {
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedService = "${context.packageName}/.service.ReelAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":")
            .any { it.equals(expectedService, ignoreCase = true) }
    }
}
