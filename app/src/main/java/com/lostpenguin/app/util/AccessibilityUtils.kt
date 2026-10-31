package com.lostpenguin.app.util

import android.content.Context
import android.provider.Settings

object AccessibilityUtils {
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val pkg = context.packageName
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        // Android stores component names in two formats depending on manufacturer:
        // "com.lostpenguin.app/.service.ReelAccessibilityService"  (short form)
        // "com.lostpenguin.app/com.lostpenguin.app.service.ReelAccessibilityService" (full form)
        return enabledServices.split(":")
            .any { component ->
                component.startsWith(pkg, ignoreCase = true) &&
                component.contains("ReelAccessibilityService", ignoreCase = true)
            }
    }
}
