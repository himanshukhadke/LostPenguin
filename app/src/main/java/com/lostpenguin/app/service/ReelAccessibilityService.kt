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

                // RecyclerView is what ViewPager2 uses internally — stable across Instagram versions
                if (!className.contains("RecyclerView", ignoreCase = true)) return

                val capturedFrom = event.fromIndex
                val capturedTo = event.toIndex

                // Debounce: cancel previous check and re-arm on each scroll event
                // Waits for scroll animation to fully settle before reading final index
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    // fromIndex == toIndex means exactly one item (reel) is fully visible
                    if (capturedFrom == capturedTo && capturedFrom != lastSettledReelIndex) {
                        val isNextReel = lastSettledReelIndex == -1
                            || capturedFrom > lastSettledReelIndex

                        if (isNextReel) {
                            ReelCounterManager.increment(applicationContext)
                            updateOverlay()
                        }
                        lastSettledReelIndex = capturedFrom
                    }
                }, SETTLE_DEBOUNCE_MS)
            }

            // Only reset when the app itself changes window (user leaves Instagram entirely)
            // NOT on every TYPE_WINDOW_STATE_CHANGED which fires for dialogs, bottom sheets etc.
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val stillOnInstagram = windows?.any {
                    it.root?.packageName == "com.instagram.android"
                } ?: false
                if (!stillOnInstagram) {
                    lastSettledReelIndex = -1
                    handler.removeCallbacksAndMessages(null)
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
