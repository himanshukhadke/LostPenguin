package com.lostpenguin.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.lostpenguin.app.R
import com.lostpenguin.app.ui.MainActivity
import com.lostpenguin.app.util.ReelCounterManager

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: android.view.View? = null
    private var countTextView: TextView? = null

    companion object {
        const val ACTION_SHOW = "ACTION_SHOW"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_HIDE = "ACTION_HIDE"
        const val EXTRA_COUNT = "EXTRA_COUNT"
        private const val CHANNEL_ID = "lostpenguin_overlay"
        private const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_UPDATE -> {
                val count = intent.getIntExtra(EXTRA_COUNT, 0)
                updateCount(count)
            }
            ACTION_HIDE -> hideOverlay()
        }
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_counter, null)
        countTextView = overlayView?.findViewById(R.id.tv_count)

        val count = ReelCounterManager.getTodayCount(this)
        countTextView?.text = count.toString()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 200
        }

        setupDragBehavior(params)
        windowManager?.addView(overlayView, params)
    }

    private fun setupDragBehavior(params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        overlayView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (initialTouchX - event.rawX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun updateCount(count: Int) {
        countTextView?.text = count.toString()
    }

    private fun hideOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LostPenguin Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Reel counter overlay"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LostPenguin Active")
            .setContentText("Counting your reels...")
            .setSmallIcon(R.drawable.ic_penguin)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }
}
