package com.chronos.clock.core.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Helpers for the one special-access permission CHRONOS can optionally use. */
object PermissionHelper {

    /**
     * True when the user has granted CHRONOS notification-listener access.
     *
     * Primary check uses Android's own enabled-listener set. The fallback
     * re-reads the raw secure setting and tolerates the flattened forms some
     * OEM skins write ("pkg/.Relative", "pkg.Class", …), so a flipped system
     * switch can never read back as "not granted".
     */
    fun hasNotificationAccess(context: Context): Boolean {
        val expected = ComponentName(
            context,
            com.chronos.clock.notifications.NotificationListener::class.java,
        )
        val flat = expected.flattenToString()
        if (NotificationManagerCompat.getEnabledListenerPackages(context).contains(flat)) {
            return true
        }

        val raw = Settings.Secure.getString(
            context.contentResolver,
            // Settings.Secure.ENABLED_NOTIFICATION_LISTENERS is @hide in the
            // SDK; this is its exact, stable secure-setting name.
            "enabled_notification_listeners",
        ) ?: return false
        val simple = expected.className.substringAfterLast('.')
        return raw.split(':').any { entry ->
            val e = entry.trim()
            e == flat ||
                e == expected.className ||
                e.endsWith("/${expected.className}") ||
                e.endsWith("/.$simple")
        }
    }

    /** System screen where the user grants notification access. */
    fun notificationAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
