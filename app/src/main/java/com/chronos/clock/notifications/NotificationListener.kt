package com.chronos.clock.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.chronos.clock.ChronosApp
import com.chronos.clock.domain.model.NotificationItem

/**
 * System-bound notification listener.
 *
 * Only runs after the user explicitly grants notification access in Android
 * settings (declared in the manifest with BIND_NOTIFICATION_LISTENER_SERVICE).
 * It converts each posted notification into a [NotificationItem] and forwards
 * it to the in-memory [NotificationRepository]; nothing is stored or sent off
 * the device.
 */
class NotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        active = this
        // Re-sync whatever is currently active after (re)connect.
        try {
            activeNotifications?.forEach { sbn -> publish(sbn) }
        } catch (_: Exception) {
            // Not fatal: the next posted notification will resync.
        }
    }

    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        publish(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        repository?.onRemoved(sbn.key)
    }

    private fun publish(sbn: StatusBarNotification) {
        // Ongoing sessions (media, navigation, downloads, screen share) are
        // states rather than messages — they would sit in the icon row
        // forever and bury the apps the user actually cares about.
        if (sbn.isOngoing) return
        val repo = repository ?: return
        val n = sbn.notification
        val extras = n.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val label = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0),
            ).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        // Only actions that declare RemoteInput can accept a typed quick reply.
        val replyActions = n.actions
            ?.filter { action -> action?.remoteInputs?.isNotEmpty() == true }
            ?.filterNotNull()
            ?: emptyList()

        repo.onPosted(
            NotificationItem(
                key = sbn.key,
                packageName = sbn.packageName,
                appLabel = label,
                id = sbn.id,
                tag = sbn.tag,
                postTime = sbn.postTime,
                title = title,
                text = text,
                isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
                groupKey = n.group ?: sbn.tag,
                contentIntent = n.contentIntent,
                bubbleIntent = bubbleIntentOf(n),
                replyActions = replyActions,
            ),
        )
    }

    private fun bubbleIntentOf(n: Notification): android.app.PendingIntent? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            n.bubbleMetadata?.intent
        } else {
            null
        }

    private val repository: NotificationRepository?
        get() = (application as? ChronosApp)?.container?.notificationStore

    /** Live service instance while bound; used for dismiss actions. */
    companion object {
        @Volatile
        var active: NotificationListener? = null
            private set

        /**
         * Asks the system to (re)bind our listener right now.
         *
         * Some OEM builds delay or silently drop the binding after the grant
         * is flipped (or after the system kills the process). A rebind always
         * ends in [onListenerConnected], which backfills every notification
         * currently on screen — so icons appear even for messages that
         * arrived while CHRONOS was closed. Best-effort: never throws.
         */
        fun forceRebind(context: Context) {
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(context, NotificationListener::class.java),
                )
            }
        }
    }

    /** Dismiss a notification the way Android allows (requires listener access). */
    fun dismiss(item: NotificationItem) {
        try {
            cancelNotification(item.key)
        } catch (_: Exception) {
            // Access revoked between render and click — ignore.
        }
    }
}
