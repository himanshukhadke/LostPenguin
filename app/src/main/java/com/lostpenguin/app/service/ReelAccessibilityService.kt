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

    // lastToIndex: highest toIndex seen in current forward swipe burst
    // pendingCount: how many forward reels are waiting to be counted when burst settles
    private var lastToIndex = -1
    private var pendingCount = 0

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

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val className = event.className?.toString() ?: return
            if (!className.contains("RecyclerView", ignoreCase = true)) return

            val fromIndex = event.fromIndex
            val toIndex = event.toIndex

            // Ignore invalid events (-1 = non-reel RecyclerView like stories/feed)
            if (fromIndex < 0 || toIndex < 0) return

            ScrollDebugLog.log("SCROLL from=$fromIndex to=$toIndex last=$lastToIndex pending=$pendingCount")

            if (toIndex > lastToIndex) {
                // Forward movement — each new highest toIndex = one more reel passed
                val newReels = toIndex - lastToIndex
                pendingCount += newReels
                lastToIndex = toIndex

                // Reset debounce — count fires 600ms after scrolling stops
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    if (pendingCount > 0) {
                        val counted = pendingCount
                        pendingCount = 0
                        repeat(counted) {
                            ReelCounterManager.increment(applicationContext)
                        }
                        updateOverlay()
                        ScrollDebugLog.log("COUNTED ✓ +$counted total=${ReelCounterManager.getTodayCount(applicationContext)}")
                    }
                }, SETTLE_DEBOUNCE_MS)

                ScrollDebugLog.log("→ forward +$newReels pending=$pendingCount")

            } else if (toIndex < lastToIndex) {
                // Backward swipe — subtract the reels scrolled back, min 0
                val backReels = lastToIndex - toIndex
                pendingCount = maxOf(0, pendingCount - backReels)
                lastToIndex = toIndex

                // Reset debounce
                handler.removeCallbacksAndMessages(null)
                if (pendingCount > 0) {
                    handler.postDelayed({
                        if (pendingCount > 0) {
                            val counted = pendingCount
                            pendingCount = 0
                            repeat(counted) {
                                ReelCounterManager.increment(applicationContext)
                            }
                            updateOverlay()
                            ScrollDebugLog.log("COUNTED ✓ +$counted total=${ReelCounterManager.getTodayCount(applicationContext)}")
                        }
                    }, SETTLE_DEBOUNCE_MS)
                }

                ScrollDebugLog.log("← back -$backReels pending=$pendingCount")
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
