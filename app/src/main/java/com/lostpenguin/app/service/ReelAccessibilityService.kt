package com.lostpenguin.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.lostpenguin.app.overlay.OverlayService
import com.lostpenguin.app.util.ReelCounterManager

class ReelAccessibilityService : AccessibilityService() {

    private var isOnReelsTab = false
    private var lastReelId: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        startOverlayService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName != "com.instagram.android") return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                checkIfOnReels(event)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (isOnReelsTab) {
                    countReelScroll(event)
                }
            }
        }
    }

    private fun checkIfOnReels(event: AccessibilityEvent) {
        val source = event.source ?: return
        // Instagram marks the reels feed with specific content descriptions
        val onReels = containsReelIndicator(source)
        if (onReels && !isOnReelsTab) {
            isOnReelsTab = true
            startOverlayService()
        } else if (!onReels && isOnReelsTab) {
            isOnReelsTab = false
            updateOverlay()
        }
        source.recycle()
    }

    private fun containsReelIndicator(node: AccessibilityNodeInfo): Boolean {
        // Check for reel-specific view IDs Instagram uses
        val reelIndicators = listOf(
            "clips_viewer_video_container",
            "reel_viewer_container",
            "clips_swipe_refresh_container"
        )
        for (indicator in reelIndicators) {
            if (node.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$indicator").isNotEmpty()) {
                return true
            }
        }
        return false
    }

    private fun countReelScroll(event: AccessibilityEvent) {
        // Each upward scroll in reels = new reel viewed
        val source = event.source ?: return
        val nodeId = source.viewIdResourceName ?: ""
        source.recycle()

        // Debounce: only count if it's a different reel container scroll
        if (nodeId != lastReelId && nodeId.contains("reel", ignoreCase = true)) {
            lastReelId = nodeId
            ReelCounterManager.increment(applicationContext)
            updateOverlay()
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
        stopService(Intent(this, OverlayService::class.java))
    }
}
