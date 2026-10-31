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
    private var lastFromIndex = -1
    private var eventCount = 0

    companion object {
        private const val SETTLE_DEBOUNCE_MS = 600L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScrollDebugLog.log("=== Service connected ===")
        startOverlayService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName != "com.instagram.android") return

        val eventTypeName = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "SCROLL"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WIN_STATE"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WIN_CONTENT"
            else -> "OTHER(${event.eventType})"
        }

        // Log ALL event types so we can see what fires on open/close
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val cls = event.className?.toString()?.substringAfterLast(".") ?: "?"
            ScrollDebugLog.log("EVT $eventTypeName cls=$cls")
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                lastToIndex = -1
                lastFromIndex = -1
                handler.removeCallbacksAndMessages(null)
                ScrollDebugLog.log("  → state reset (lastTo=-1 lastFrom=-1)")
            }
            return
        }

        // TYPE_VIEW_SCROLLED
        val className = event.className?.toString() ?: return
        val shortClass = className.substringAfterLast(".")
        val fromIndex = event.fromIndex
        val toIndex = event.toIndex
        val itemCount = event.itemCount
        val scrollX = event.scrollX
        val scrollY = event.scrollY

        eventCount++
        ScrollDebugLog.log("#$eventCount SCROLL cls=$shortClass from=$fromIndex to=$toIndex itemCount=$itemCount scrollX=$scrollX scrollY=$scrollY | lastFrom=$lastFromIndex lastTo=$lastToIndex")

        if (!className.contains("RecyclerView", ignoreCase = true)) {
            ScrollDebugLog.log("  → skip (not RecyclerView)")
            return
        }

        if (fromIndex < 0 || toIndex < 0) {
            ScrollDebugLog.log("  → skip (invalid indices)")
            return
        }

        // Log fromIndex changes — this is the current reel the user is on
        if (fromIndex != lastFromIndex) {
            ScrollDebugLog.log("  → fromIndex changed: $lastFromIndex → $fromIndex")
        }

        if (toIndex > lastToIndex) {
            ScrollDebugLog.log("  → toIndex UP: $lastToIndex → $toIndex (forward swipe)")
            lastToIndex = toIndex
            lastFromIndex = fromIndex
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                ReelCounterManager.increment(applicationContext)
                updateOverlay()
                ScrollDebugLog.log("  ✓ COUNTED total=${ReelCounterManager.getTodayCount(applicationContext)}")
            }, SETTLE_DEBOUNCE_MS)
        } else if (toIndex < lastToIndex) {
            ScrollDebugLog.log("  → toIndex DOWN: $lastToIndex → $toIndex (reset/back)")
            lastToIndex = toIndex
            lastFromIndex = fromIndex
            handler.removeCallbacksAndMessages(null)
        } else {
            ScrollDebugLog.log("  → toIndex SAME=$toIndex (no change)")
            lastFromIndex = fromIndex
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
