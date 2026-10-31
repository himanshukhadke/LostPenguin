package com.lostpenguin.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.lostpenguin.app.overlay.OverlayService
import com.lostpenguin.app.util.ReelCounterManager
import com.lostpenguin.app.util.ScrollDebugLog

class ReelAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastScrollY = 0
    private var isOnReels = false
    private var pendingCount = false

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

        when (event.eventType) {

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val className = event.className?.toString() ?: return
                if (!className.contains("RecyclerView", ignoreCase = true)) return

                val scrollY = event.scrollY
                val fromIndex = event.fromIndex
                val toIndex = event.toIndex
                val maxScrollY = event.maxScrollY

                ScrollDebugLog.log(
                    "SCROLL sY=$scrollY maxSY=$maxScrollY " +
                    "from=$fromIndex to=$toIndex " +
                    "onReels=$isOnReels"
                )

                if (scrollY > lastScrollY) {
                    scheduleCount()
                }
                lastScrollY = scrollY
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val root = rootInActiveWindow ?: return
                val wasOnReels = isOnReels
                isOnReels = isReelsScreen(root)
                root.recycle()

                if (wasOnReels != isOnReels) {
                    ScrollDebugLog.log("isOnReels changed: $wasOnReels → $isOnReels")
                }

                if (!isOnReels) {
                    handler.removeCallbacksAndMessages(null)
                    pendingCount = false
                    lastScrollY = 0
                }
            }
        }
    }

    private fun scheduleCount() {
        if (!pendingCount) {
            pendingCount = true
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                pendingCount = false
                if (isOnReels) {
                    ReelCounterManager.increment(applicationContext)
                    updateOverlay()
                    ScrollDebugLog.log("COUNTED ✓ total=${ReelCounterManager.getTodayCount(applicationContext)}")
                } else {
                    ScrollDebugLog.log("SCROLL ignored — not on reels")
                }
            }, SETTLE_DEBOUNCE_MS)
        }
    }

    private fun isReelsScreen(root: AccessibilityNodeInfo): Boolean {
        val reelIds = listOf(
            "clips_viewer_video_container",
            "reel_viewer_container",
            "clips_swipe_refresh_container",
            "clips_tab_content_container",
            "unified_clips_player_grid_view",
            "clips_viewer_pager"
        )
        for (id in reelIds) {
            if (root.findAccessibilityNodeInfosByViewId(
                    "com.instagram.android:id/$id"
                ).isNotEmpty()
            ) return true
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
        handler.removeCallbacksAndMessages(null)
        stopService(Intent(this, OverlayService::class.java))
    }
}
