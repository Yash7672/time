package com.chronos.clock.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.chronos.clock.ChronosApp
import com.chronos.clock.core.permissions.PermissionHelper
import com.chronos.clock.domain.model.NotificationItem

/**
 * System-bound notification listener.
 *
 * Only runs after the user explicitly grants notification access in Android
 * settings (declared in the manifest with BIND_NOTIFICATION_LISTENER_SERVICE).
 * It converts each posted notification into a [NotificationItem] and forwards
 * it to the in-memory [NotificationRepository]; nothing is stored or sent off
 * the device.
 *
 * Robustness contract (the icon row must never silently die):
 *  - every callback is exception-guarded — one malformed notification from
 *    any app can never crash the service (a crashed listener = no icons);
 *  - a dropped binding is requested again shortly after
 *    [onListenerDisconnected] while the grant still exists;
 *  - on (re)connect everything currently on screen is backfilled, with one
 *    delayed retry for OEM builds that report an empty snapshot at connect.
 */
class NotificationListener : NotificationListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        super.onListenerConnected()
        active = this
        backfill()
        // Some OEM builds return an empty snapshot the instant the listener
        // connects; retry once so notifications posted before CHRONOS was
        // opened still land in the icon row.
        mainHandler.postDelayed({ backfill() }, BACKFILL_RETRY_MS)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (active === this) active = null
        // OEM builds often drop the binding and never bring it back on their
        // own. Ask the system to re-bind shortly after — only while the
        // grant still exists, and never more than once per few seconds.
        val app = applicationContext
        mainHandler.postDelayed({
            val now = System.currentTimeMillis()
            if (now - lastRebindAtMs < MIN_REBIND_INTERVAL_MS) return@postDelayed
            if (!PermissionHelper.hasNotificationAccess(app)) return@postDelayed
            lastRebindAtMs = now
            forceRebind(app)
        }, REBIND_DELAY_MS)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (active === this) active = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        publish(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        runCatching { repository?.onRemoved(sbn.key) }
    }

    private fun backfill() {
        try {
            activeNotifications?.forEach { sbn -> publish(sbn) }
        } catch (_: Exception) {
            // Not fatal: the next posted notification will resync.
        }
    }

    private fun publish(sbn: StatusBarNotification) {
        // Never let one malformed notification from any app take the whole
        // listener down — a crashed service means no icons, ever.
        try {
            // Ongoing sessions (media, navigation, downloads, screen share)
            // are states rather than messages — they would sit in the icon
            // row forever and bury the apps the user actually cares about.
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
        } catch (_: Exception) {
            // Buggy extras/actions from a single app: skip this notification,
            // keep the listener alive for every other app.
        }
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

        /** Delay before asking for a rebind after the system dropped us. */
        private const val REBIND_DELAY_MS = 1_500L

        /** Never ask for a rebind more often than this. */
        private const val MIN_REBIND_INTERVAL_MS = 5_000L

        /** One delayed re-backfill after connect (OEM empty-snapshot quirk). */
        private const val BACKFILL_RETRY_MS = 1_500L

        @Volatile
        private var lastRebindAtMs = 0L

        /**
         * Asks the system to (re)bind our listener right now.
         *
         * Some OEM builds delay or silently drop the binding after the grant
         * is flipped (or after the system kills the process). A rebind always
         * ends in onListenerConnected, which backfills every notification
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
