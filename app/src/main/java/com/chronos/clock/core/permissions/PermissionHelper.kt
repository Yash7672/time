package com.chronos.clock.core.permissions

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Helpers for the two special-access permissions CHRONOS can optionally use. */
object PermissionHelper {

    /** True when the user has granted CHRONOS notification-listener access. */
    fun hasNotificationAccess(context: Context): Boolean {
        val expected = ComponentName(context, com.chronos.clock.notifications.NotificationListener::class.java)
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabled.contains(expected.flattenToString())
    }

    /** System screen where the user grants notification access. */
    fun notificationAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** True when the user has granted "display over other apps". */
    fun hasOverlayAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

    /** System screen where the user grants overlay access. */
    fun overlayAccessIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${contextPackageName()}"),
        )

    private var packageNameCache: String? = null
    private fun contextPackageName(): String = packageNameCache.orEmpty()

    fun bindPackageName(name: String) {
        packageNameCache = name
    }

    /** POST_NOTIFICATIONS is only needed for the overlay's own FGS notice. */
    fun needsPostNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun hasPostNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.areNotificationsEnabled() != false
        } else {
            true
        }
}
