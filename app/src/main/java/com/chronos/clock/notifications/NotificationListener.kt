package com.chronos.clock.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.chronos.clock.ChronosApp
import com.chronos.clock.core.permissions.PermissionHelper
import com.chronos.clock.domain.model.NotificationItem
import com.chronos.clock.domain.model.StyleMessage

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

    /**
     * Keys we have snoozed for banner silencing. Their "removal" is only
     * the snooze hiding them from the system — the message itself must
     * stay in the icon row, so removal callbacks consult this set.
     */
    private val silencedKeys: MutableSet<String> =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())

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

    override fun onNotificationRemoved(sbn: StatusBarNotification?) =
        handleRemoved(sbn, REASON_UNKNOWN)

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int,
    ) = handleRemoved(sbn, reason)

    /**
     * One shared removal path (the plain overload covers OEM builds that
     * report no reason; the reason-carrying one runs everywhere else).
     *
     * Banner-silenced (snoozed) messages are kept: our snooze only hides
     * the notification from the system, the message itself must stay in
     * the icon row. A real user dismissal can never be mistaken for that,
     * because NotificationManager drops dismissed keys from the store
     * itself first; every other reason (source app clearing it, a tap in
     * the shade) removes the entry as usual.
     */
    private fun handleRemoved(sbn: StatusBarNotification?, reason: Int) {
        sbn ?: return
        runCatching {
            val key = sbn.key
            val keep = key in silencedKeys &&
                (
                    reason == NotificationListenerService.REASON_SNOOZED ||
                        reason == REASON_UNKNOWN ||
                        // Our own dismiss() cancelling a silenced key —
                        // the store already dropped it locally.
                        reason == NotificationListenerService.REASON_LISTENER_CANCEL
                    )
            if (keep) {
                if (reason == NotificationListenerService.REASON_LISTENER_CANCEL) {
                    silencedKeys.remove(key)
                }
                return
            }
            silencedKeys.remove(key)
            repository?.onRemoved(key)
        }
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
            val repo = repository ?: return
            val n = sbn.notification
            // Ongoing sessions (navigation, downloads, screen share) are
            // states rather than messages — they would sit in the icon
            // row forever and bury the apps the user actually cares about.
            // Active media playback is the one exception: its icon is the
            // handle for the tap-and-hold transport panel, and Android
            // clears the notification itself when playback ends.
            if (sbn.isOngoing && !isMediaNotification(n)) return
            val extras = n.extras ?: return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            // Prefer the expanded BigText (the full message) over the
            // one-line preview — and, for MessagingStyle notifications,
            // also pull out every individual message (see below): that is
            // where "all last unseen messages" really live.
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            val styleMessages = styleMessagesOf(extras)
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
                    styleMessages = styleMessages,
                ),
            )

            // Banner silencing: hide the system preview for this message
            // (the setting, CHRONOS's visibility and the exemptions are
            // all decided here — see silenceBanner).
            silenceBanner(sbn, n, repo)
        } catch (_: Exception) {
            // Buggy extras/actions from a single app: skip this notification,
            // keep the listener alive for every other app.
        }
    }

    /**
     * Banner silencing: snooze the notification the instant it posts so
     * Android never gets to show its heads-up banner — the message is then
     * reachable only through CHRONOS's tap-and-hold sheet. Scoped to while
     * CHRONOS is actually on screen (in the background the phone stays
     * stock, so nothing can be missed and no notification gets stuck),
     * never touches calls, alarms or media, and a snooze the OEM build
     * refuses is simply ignored (the banner shows, nothing breaks).
     */
    private fun silenceBanner(
        sbn: StatusBarNotification,
        n: Notification,
        repo: NotificationRepository,
    ) {
        if (!repo.settings.value.silenceBanners) return
        if ((application as? ChronosApp)?.uiVisible != true) return
        if (sbn.packageName == packageName) return
        if (isMediaNotification(n) || n.category in NEVER_SILENCE) return
        runCatching {
            snoozeNotification(sbn.key, SNOOZE_MS)
            silencedKeys.add(sbn.key)
        }
    }

    /** True for music/video playback notifications (they get the media icon). */
    private fun isMediaNotification(n: Notification): Boolean =
        n.category == Notification.CATEGORY_TRANSPORT ||
            n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true

    /**
     * The individual messages packed inside a MessagingStyle notification —
     * chat apps put several sender/text/timestamp messages into ONE
     * notification, so this is what makes "all last unseen messages"
     * possible. Read with plain Bundle calls against the **public**
     * [Notification.EXTRA_MESSAGES] constant (documented in the SDK) and
     * the per-message keys spelled out in frameworks/base
     * (`"text"` / `"time"` / `"sender"`) — no reflection, no hidden
     * method. Best-effort: a malformed payload yields an empty list and
     * can never take the listener down.
     */
    @Suppress("DEPRECATION") // no public typed-array Bundle overload before API 33
    private fun styleMessagesOf(extras: Bundle): List<StyleMessage> = try {
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.filterIsInstance<Bundle>()
            ?.mapNotNull { b ->
                val body = b.getCharSequence(KEY_TEXT)?.toString()
                if (body.isNullOrBlank()) {
                    null
                } else {
                    StyleMessage(
                        sender = b.getCharSequence(KEY_SENDER)?.toString(),
                        text = body,
                        time = b.getLong(KEY_TIME, 0L),
                    )
                }
            } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
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

        // Per-message keys inside Notification.EXTRA_MESSAGES bundles —
        // exact spellings verified in AOSP frameworks/base (Notification
        //.MessagingStyle.Message.toBundle).
        private const val KEY_TEXT = "text"
        private const val KEY_TIME = "time"
        private const val KEY_SENDER = "sender"

        /** Notification categories that must never be silenced. */
        private val NEVER_SILENCE = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
        )

        /** ~10 years — effectively "until the source app itself clears it". */
        private const val SNOOZE_MS = 315_360_000_000L

        /** Marker for removal callbacks that arrive without a reason. */
        private const val REASON_UNKNOWN = -1

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
