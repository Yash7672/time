package com.chronos.clock.notifications

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.chronos.clock.domain.model.NotificationItem

/**
 * Sends an inline quick reply through the *source app's* PendingIntent using
 * Android's RemoteInput API — the officially supported mechanism (this is how
 * every system quick-reply surface works). Nothing is simulated or faked.
 */
object QuickReplyHandler {

    /** Returns true when [item] offers at least one RemoteInput action. */
    fun canReply(item: NotificationItem): Boolean = item.canReply

    /**
     * @param actionIndex index into [NotificationItem.replyActions]; 0 selects
     * the first reply-capable action (normal for messaging apps).
     * @return true when the reply was handed to the source app.
     */
    fun send(context: Context, item: NotificationItem, actionIndex: Int, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val action = item.replyActions.getOrNull(actionIndex) ?: return false
        val remoteInputs = action.remoteInputs ?: return false
        if (remoteInputs.isEmpty()) return false

        val results = Bundle()
        // Most apps expose a single result key; fill every declared input so
        // apps with multiple RemoteInput keys still receive the text.
        for (input in remoteInputs) {
            results.putCharSequence(input.resultKey, trimmed)
        }

        val fillIn = Intent().apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        RemoteInput.addResultsToIntent(remoteInputs, fillIn, results)

        val pending: PendingIntent = action.actionIntent ?: return false
        return try {
            pending.send(context, 0, fillIn)
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
