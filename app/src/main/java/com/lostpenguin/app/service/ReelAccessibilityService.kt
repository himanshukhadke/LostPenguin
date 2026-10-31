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

    // Track whether a forward swipe is in progress
    private var swipeInProgress = false
    private var lastToIndex = 0

    companion object {
        private const val SETTLE_DEBOUNCE_MS = 500L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScrollDebugLog.log("Service connected")
        startOverlayService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName != "com.instagram.android") return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val className = event.className?.toString() ?: return
            if (!className.contains("RecyclerView", ignoreCase = true)) return

            val fromIndex = event.fromIndex
            val toIndex = event.toIndex

            ScrollDebugLog.log("SCROLL from=$fromIndex to=$toIndex swipeInProgress=$swipeInProgress")

            // toIndex > fromIndex means swipe is in progress (multiple items visible)
            if (toIndex > fromIndex) {
                // Only count forward swipes (toIndex increasing from last seen)
                if (toIndex > lastToIndex) {
                    swipeInProgress = true
                    ScrollDebugLog.log("→ Forward swipe detected (to=$toIndex > last=$lastToIndex)")
                } else {
                    // toIndex decreasing = going back, cancel any pending count
                    swipeInProgress = false
                    handler.removeCallbacksAndMessages(null)
                    ScrollDebugLog.log("← Back swipe detected, cancelled")
                }
                lastToIndex = toIndex
            }

            // fromIndex == toIndex means scroll settled on exactly one item
            if (fromIndex == toIndex) {
                if (swipeInProgress) {
                    // Settled after a forward swipe — count it
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({
                        swipeInProgress = false
                        lastToIndex = 0
                        ReelCounterManager.increment(applicationContext)
                        updateOverlay()
                        ScrollDebugLog.log("COUNTED ✓ total=${ReelCounterManager.getTodayCount(applicationContext)}")
                    }, SETTLE_DEBOUNCE_MS)
                }
                // Reset lastToIndex when settled
                lastToIndex = 0
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
