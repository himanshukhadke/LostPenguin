package com.lostpenguin.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.lostpenguin.app.overlay.OverlayService
import com.lostpenguin.app.util.ReelCounterManager
import com.lostpenguin.app.util.ScrollDebugLog

class ReelAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var lastToIndex = -1

    companion object {
        private const val SETTLE_DEBOUNCE_MS = 600L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScrollDebugLog.log("Service connected")
        startOverlayService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName != "com.instagram.android") return

        // Reset state when Instagram is reopened/foregrounded
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastToIndex = -1
            handler.removeCallbacksAndMessages(null)
            ScrollDebugLog.log("--- Instagram window changed, state reset ---")
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val className = event.className?.toString() ?: return
            if (!className.contains("RecyclerView", ignoreCase = true)) return

            val fromIndex = event.fromIndex
            val toIndex = event.toIndex

            // Ignore invalid events (-1 = non-reel RecyclerView)
            if (fromIndex < 0 || toIndex < 0) return

            ScrollDebugLog.log("SCROLL from=$fromIndex to=$toIndex last=$lastToIndex")

            if (toIndex > lastToIndex) {
                // Scrolled forward — schedule a count 600ms after scrolling stops
                lastToIndex = toIndex
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    ReelCounterManager.increment(applicationContext)
                    updateOverlay()
                    ScrollDebugLog.log("COUNTED ✓ total=${ReelCounterManager.getTodayCount(applicationContext)}")
                }, SETTLE_DEBOUNCE_MS)
                ScrollDebugLog.log("→ forward to=$toIndex")
            } else if (toIndex < lastToIndex) {
                // Scrolled back — cancel pending count
                lastToIndex = toIndex
                handler.removeCallbacksAndMessages(null)
                ScrollDebugLog.log("← back cancelled")
            }
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
        }
        startForegroundService(intent)
    }

    private fun updateOverlay() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE
            putExtra(OverlayService.EXTRA_COUNT, ReelCounterManager.getTodayCount(applicationContext))
        }
        startService(intent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        stopService(Intent(this, OverlayService::class.java))
    }
}
