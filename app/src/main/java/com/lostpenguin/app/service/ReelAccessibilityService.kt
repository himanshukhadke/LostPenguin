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

    // Track scroll position per approach
    private var lastScrollY = 0
    private var lastScrollX = 0
    private var isOnReels = false
    private var pendingCount = false

    companion object {
        private const val SETTLE_DEBOUNCE_MS = 500L
        private const val MIN_SCROLL_DELTA = 1
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
                if (!className.contains("RecyclerView", ignoreCase = true)) return

                val scrollY = event.scrollY
                val scrollX = event.scrollX
                val fromIndex = event.fromIndex
                val toIndex = event.toIndex
                val maxScrollY = event.maxScrollY

                // Log all values to understand what Instagram actually sends
                android.util.Log.d("LostPenguin",
                    "SCROLL className=$className " +
                    "scrollX=$scrollX scrollY=$scrollY maxScrollY=$maxScrollY " +
                    "fromIndex=$fromIndex toIndex=$toIndex " +
                    "itemCount=${event.itemCount}"
                )

                // Approach 1: scrollY changes (pixel-based, works on some Instagram versions)
                if (maxScrollY > 0 && scrollY > lastScrollY + MIN_SCROLL_DELTA) {
                    scheduleCount()
                }

                // Approach 2: fromIndex increases (page-based, works on others)
                if (fromIndex > 0 && fromIndex > toIndex.coerceAtLeast(0) + 0) {
                    // fromIndex advanced
                }

                // Approach 3: ANY downward scroll on reels tab — use scrollY delta
                if (scrollY > lastScrollY) {
                    scheduleCount()
                }

                lastScrollY = scrollY
                lastScrollX = scrollX
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val className = event.className?.toString() ?: ""
                val text = event.text.joinToString()

                android.util.Log.d("LostPenguin",
                    "WINDOW className=$className text=$text"
                )

                // Detect if we're on the reels tab
                val root = rootInActiveWindow ?: return
                isOnReels = isReelsScreen(root)
                root.recycle()

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
                ReelCounterManager.increment(applicationContext)
                updateOverlay()
                android.util.Log.d("LostPenguin", "COUNTED reel, total=${ReelCounterManager.getTodayCount(applicationContext)}")
            }, SETTLE_DEBOUNCE_MS)
        }
    }

    private fun isReelsScreen(root: android.view.accessibility.AccessibilityNodeInfo): Boolean {
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
