package com.chronos.clock.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.chronos.clock.MainActivity
import com.chronos.clock.R
import com.chronos.clock.core.constants.AppConstants
import java.util.Locale

/**
 * Optional floating micro-clock.
 *
 * Design constraints (deliberate):
 *  - Only started after the user grants "display over other apps" and enables
 *    it in Settings; denial is handled gracefully (see [startIfAllowed]).
 *  - Runs as a typed foreground service because it must survive the host
 *    activity being covered; the notification is silent and minimal.
 *  - The window is FLAG_NOT_FOCUSABLE so it can never block touches to other
 *    apps, and closing it never affects CHRONOS's main activity.
 *  - It draws a tiny TextView (no Compose composition in an overlay window),
 *    updates once per minute in on-screen mode / every second is unnecessary —
 *    it re-reads on tick using a lightweight handler.
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayRoot: View? = null
    private var overlayTime: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (!hasOverlayPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        showOverlay()
        return START_STICKY
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConstants.OVERLAY_CHANNEL_ID,
                AppConstants.OVERLAY_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN, // silent: no sound, no peek
            ).apply {
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, AppConstants.OVERLAY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Floating clock active")
            .setContentText("Tap to open CHRONOS")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()

        // Android 14+ requires an explicit type that matches the manifest;
        // older releases take the plain overload (no type enforcement).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                AppConstants.OVERLAY_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(AppConstants.OVERLAY_NOTIFICATION_ID, notification)
        }
    }

    private fun showOverlay() {
        if (overlayRoot != null) return
        val wm = getSystemService(WindowManager::class.java)
        windowManager = wm

        val root = LayoutInflater.from(this).inflate(R.layout.overlay_clock, null)
        val timeView = root.findViewById<TextView>(R.id.overlay_time)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable: never intercepts touches meant for other apps.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 120
        }

        try {
            wm?.addView(root, params)
            overlayRoot = root
            overlayTime = timeView
        } catch (_: SecurityException) {
            // Permission revoked between check and add — fail silently.
            stopSelf()
            return
        }

        startTicking(timeView)
    }

    private fun startTicking(view: TextView) {
        val handler = android.os.Handler(mainLooper)
        val formatter = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
        val task = object : Runnable {
            override fun run() {
                if (overlayRoot == null) return
                view.text = formatter.format(java.util.Date())
                handler.postDelayed(this, 60_000) // once per minute: AOD style
            }
        }
        handler.post(task)
        view.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun stopOverlay() {
        overlayRoot?.let { root ->
            try {
                windowManager?.removeView(root)
            } catch (_: Exception) {
                // Already detached — nothing to do.
            }
        }
        overlayRoot = null
        overlayTime = null
    }

    override fun onDestroy() {
        stopOverlay()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.chronos.clock.action.STOP_OVERLAY"

        fun hasOverlayPermission(context: Context): Boolean =
            com.chronos.clock.core.permissions.PermissionHelper.hasOverlayAccess(context)

        /** Starts the floating clock only when permission is already granted. */
        fun startIfAllowed(context: Context): Boolean {
            if (!hasOverlayPermission(context)) return false
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            return true
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
                .setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
