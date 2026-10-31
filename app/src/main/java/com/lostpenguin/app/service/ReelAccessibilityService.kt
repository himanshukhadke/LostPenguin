package com.lostpenguin.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.lostpenguin.app.overlay.OverlayService
import com.lostpenguin.app.util.ReelCounterManager

class ReelAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastSettledReelIndex = -1

    companion object {
        private const val SETTLE_DEBOUNCE_MS = 400L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        startOverlayService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName != "com.instagram.android") return

        when (event.eventType) {

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val className = event.className?.toString() ?: return

                // Only care about RecyclerView scroll — stable across Instagram versions
                if (!className.contains("RecyclerView", ignoreCase = true)) return

                val capturedFrom = event.fromIndex
                val capturedTo = event.toIndex

                // Debounce: cancel previous check and re-arm
                // We wait for scroll to fully settle before counting
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    // fromIndex == toIndex means exactly one reel is fully visible (settled)
                    if (capturedFrom == capturedTo && capturedFrom != lastSettledReelIndex) {
                        val isNextReel = lastSettledReelIndex == -1
                            || capturedFrom > lastSettledReelIndex

                        if (isNextReel) {
                            // User scrolled forward to a new reel — count it
                            ReelCounterManager.increment(applicationContext)
                            updateOverlay()
                        }
                        // If capturedFrom < lastSettledReelIndex → went back → don't count
                        lastSettledReelIndex = capturedFrom
                    }
                }, SETTLE_DEBOUNCE_MS)
            }

            // Reset tracking when user navigates away from Reels screen
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                lastSettledReelIndex = -1
                handler.removeCallbacksAndMessages(null)
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
