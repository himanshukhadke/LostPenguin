package com.lostpenguin.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Accessibility service restarts itself; nothing extra needed.
            // This receiver exists to re-show overlay if user had it enabled.
        }
    }
}
