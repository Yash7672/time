package com.chronos.clock.core.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Helpers for the one special-access permission CHRONOS can optionally use. */
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
}
