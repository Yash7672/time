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

    private const val PREFS = "chronos_permissions"
    private const val KEY_CONSENT_AUTO_SHOWN = "consent_auto_shown"

    /**
     * Exactly-once gate for the guided first grant: returns `true` only the
     * first time it is called (and records the call), so the app can jump
     * straight to Android's consent screen on first launch without ever
     * repeating that detour if the user declines.
     */
    fun consumeFirstConsentPrompt(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CONSENT_AUTO_SHOWN, false)) return false
        prefs.edit().putBoolean(KEY_CONSENT_AUTO_SHOWN, true).apply()
        return true
    }
}
