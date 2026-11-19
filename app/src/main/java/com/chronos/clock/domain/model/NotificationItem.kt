package com.chronos.clock.domain.model

import android.app.PendingIntent

/**
 * One message inside a MessagingStyle notification: chat apps pack
 * several sender/text/timestamp messages into a **single** notification.
 * This is where the reply sheet's full "last unseen messages" list comes
 * from. Memory only, exactly like the rest of the notification data.
 *
 * @property sender Who sent it (null for own messages / no sender given)
 * @property text The message body
 * @property time Epoch ms when it was sent; 0 = not provided
 */
data class StyleMessage(
    val sender: String?,
    val text: String,
    val time: Long,
)

/**
 * One active notification, held in memory only.
 *
 * CHRONOS never writes notification content to disk, never uploads it and
 * never transmits it. [contentIntent] / [replyActions] are the source app's
 * own Android-provided handles, used verbatim to open or reply.
 */
data class NotificationItem(
    /** Stable Android key (package + tag + id). */
    val key: String,
    val packageName: String,
    val appLabel: String,
    val id: Int,
    val tag: String?,
    val postTime: Long,
    val title: String?,
    val text: String?,
    val isGroupSummary: Boolean = false,
    val groupKey: String? = null,
    /** Source app's intent that opens the corresponding conversation. */
    val contentIntent: PendingIntent? = null,
    /** Source app's bubble intent, when the app exposes one (Android 10+). */
    val bubbleIntent: PendingIntent? = null,
    /** Source app's actions that declare RemoteInput (i.e. quick reply). */
    val replyActions: List<android.app.Notification.Action> = emptyList(),
    /**
     * Every message carried inside this notification's MessagingStyle,
     * oldest first (empty for plain notifications, which only have
     * [title]/[text]).
     */
    val styleMessages: List<StyleMessage> = emptyList(),
) {
    val canReply: Boolean get() = replyActions.isNotEmpty()
}

/** Notifications grouped per app for the icon ring around the clock. */
data class NotificationGroup(
    val packageName: String,
    val appLabel: String,
    val count: Int,
    val latest: NotificationItem,
    /**
     * Every active (unseen) non-summary notification of this app, newest
     * first — what the quick-reply sheet shows as "last unseen messages".
     * Memory only, exactly like [latest]; vanishes with the notification.
     */
    val items: List<NotificationItem> = emptyList(),
)
