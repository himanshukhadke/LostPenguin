package com.lostpenguin.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.lostpenguin.app.overlay.OverlayService
import com.lostpenguin.app.util.ReelCounterManager

class ReelAccessibilityService : AccessibilityService() {

    private var isOnReels = false
    private var lastScrollTime = 0L

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
                val root = rootInActiveWindow ?: return
                isOnReels = isReelsScreen(root)
                root.recycle()
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (!isOnReels) return
                val now = System.currentTimeMillis()
                // Debounce: at least 600ms between counts to avoid double-counting
                if (now - lastScrollTime > 600) {
                    lastScrollTime = now
                    ReelCounterManager.increment(applicationContext)
                    updateOverlay()
                }
            }
        }
    }

    private fun isReelsScreen(root: AccessibilityNodeInfo): Boolean {
        // Check multiple indicators Instagram uses for the reels feed
        val reelIds = listOf(
            "clips_viewer_video_container",
            "reel_viewer_container",
            "clips_swipe_refresh_container",
            "clips_tab_content_container",
            "unified_clips_player_grid_view"
        )
        for (id in reelIds) {
            if (root.findAccessibilityNodeInfosByViewId(
                    "com.instagram.android:id/$id"
                ).isNotEmpty()
            ) return true
        }
        // Fallback: check content description for reel indicators
        return containsTextNode(root, "Reels") || containsTextNode(root, "reel")
    }

    private fun containsTextNode(node: AccessibilityNodeInfo, text: String): Boolean {
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains(text, ignoreCase = true)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsTextNode(child, text)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
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
