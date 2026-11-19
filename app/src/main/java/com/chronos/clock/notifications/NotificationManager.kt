package com.chronos.clock.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.chronos.clock.domain.model.NotificationItem

/**
 * Facade the UI uses for notification interactions. All operations are
 * best-effort and swallow Android's expected failure modes (cancelled
 * PendingIntents, revoked listener access) so a click can never crash CHRONOS.
 */
class NotificationManager(
    private val appContext: Context,
    /** Shared in-memory store — the listener writes into this same instance. */
    private val repository: NotificationRepository,
) {

    val items get() = repository.items
    val groups get() = repository.groups
    val settings get() = repository.settings

    fun onPosted(item: NotificationItem) = repository.onPosted(item)

    fun onRemoved(key: String) = repository.onRemoved(key)

    fun clear() = repository.clear()

    suspend fun loadIcon(packageName: String) = repository.loadIcon(packageName)

    fun hasAccess(): Boolean = repository.hasAccess()

    /**
     * Opens the source app's conversation. Preference order:
     *  1. bubble intent (when the user enabled bubbles and the app provides one),
     *  2. the notification's own content PendingIntent.
     * Both are Android-provided handles — CHRONOS never pokes another app's UI.
     */
    fun open(item: NotificationItem, useBubbles: Boolean): Boolean {
        val target: PendingIntent? = when {
            useBubbles && item.bubbleIntent != null -> item.bubbleIntent
            else -> item.contentIntent
        }
        if (target == null) return false
        return try {
            target.send()
            true
        } catch (_: PendingIntent.CanceledException) {
            // Stale handle; fall back to the plain content intent once.
            if (target !== item.contentIntent) {
                try {
                    item.contentIntent?.send()
                    item.contentIntent != null
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens the source app in multi-window (split screen) beside CHRONOS.
     *
     * [Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT] asks Android to give the app its
     * own window area next to CHRONOS; the screen splits so both stay usable.
     * Devices or apps without multi-window support simply fall back to a
     * normal full launch — never a crash. Pressing Back inside that window
     * closes it again and CHRONOS takes the whole screen back.
     */
    fun openInMultiWindow(item: NotificationItem): Boolean {
        val launch = try {
            appContext.packageManager.getLaunchIntentForPackage(item.packageName)
        } catch (_: Exception) {
            null
        }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            return try {
                appContext.startActivity(launch)
                true
            } catch (_: Exception) {
                // Split refused by the system: retry once as a plain launch.
                try {
                    launch.flags = launch.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT.inv()
                    appContext.startActivity(launch)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        // No launcher entry (rare): fall back to the notification's own
        // handle — bubble intent first when the user prefers bubbles.
        return open(item, useBubbles = repository.settings.value.bubblesEnabled)
    }

    /** Dismiss through the listener service; only possible with granted access. */
    fun dismiss(item: NotificationItem): Boolean {
        val listener = NotificationListener.active
        return if (listener != null) {
            listener.dismiss(item)
            true
        } else {
            // Service not bound right now: drop it locally at least.
            repository.onRemoved(item.key)
            false
        }
    }

    fun reply(item: NotificationItem, text: String, actionIndex: Int = 0): Boolean =
        QuickReplyHandler.send(appContext, item, actionIndex, text)
}