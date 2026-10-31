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

    private var swipeInProgress = false
    private var lastToIndex = -1
    private var lastSettledIndex = -1

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

            // Ignore invalid events (Instagram fires -1/-1 for non-reel views)
            if (fromIndex < 0 || toIndex < 0) return

            ScrollDebugLog.log("SCROLL from=$fromIndex to=$toIndex settled=$lastSettledIndex inProgress=$swipeInProgress")

            // toIndex > fromIndex: items are transitioning (swipe in progress)
            if (toIndex > fromIndex) {
                if (toIndex > lastToIndex) {
                    // Forward swipe: toIndex is increasing
                    swipeInProgress = true
                    handler.removeCallbacksAndMessages(null)
                    // Schedule a count — will fire after SETTLE_DEBOUNCE_MS of no new events
                    val capturedToIndex = toIndex
                    handler.postDelayed({
                        if (swipeInProgress && lastToIndex == capturedToIndex) {
                            swipeInProgress = false
                            ReelCounterManager.increment(applicationContext)
                            updateOverlay()
                            ScrollDebugLog.log("COUNTED ✓ total=${ReelCounterManager.getTodayCount(applicationContext)}")
                        }
                    }, SETTLE_DEBOUNCE_MS)
                    ScrollDebugLog.log("→ Forward swipe detected (to=$toIndex > last=$lastToIndex)")
                } else if (toIndex < lastToIndex) {
                    // Backward swipe: toIndex is decreasing
                    swipeInProgress = false
                    handler.removeCallbacksAndMessages(null)
                    ScrollDebugLog.log("← Back swipe cancelled")
                }
                lastToIndex = toIndex
            }

            // fromIndex == toIndex: scroll settled on a single item
            if (fromIndex == toIndex && fromIndex >= 0) {
                if (swipeInProgress && toIndex > lastSettledIndex) {
                    // Settled on a new reel further forward — count immediately
                    handler.removeCallbacksAndMessages(null)
                    swipeInProgress = false
                    lastSettledIndex = toIndex
                    lastToIndex = toIndex
                    ReelCounterManager.increment(applicationContext)
                    updateOverlay()
                    ScrollDebugLog.log("COUNTED ✓ total=${ReelCounterManager.getTodayCount(applicationContext)}")
                } else {
                    // Settled on same or previous reel — just reset state
                    handler.removeCallbacksAndMessages(null)
                    swipeInProgress = false
                    lastToIndex = toIndex
                }
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
