package com.chronos.clock.notifications

import com.chronos.clock.domain.model.NotificationItem

/**
 * Facade the UI uses for notification interactions. All operations are
 * best-effort and swallow Android's expected failure modes (cancelled
 * PendingIntents, revoked listener access) so a click can never crash CHRONOS.
 *
 * Launching other apps lives in [FloatingAppLauncher] — this class only
 * routes to it and keeps the repository contract.
 */
class NotificationManager(
    private val appContext: android.content.Context,
    /** Shared in-memory store — the listener writes into this same instance. */
    private val repository: NotificationRepository,
) {

    private val launcher = FloatingAppLauncher(appContext)

    val items get() = repository.items
    val groups get() = repository.groups
    val settings get() = repository.settings

    fun onPosted(item: NotificationItem) = repository.onPosted(item)

    fun onRemoved(key: String) = repository.onRemoved(key)

    fun clear() = repository.clear()

    suspend fun loadIcon(packageName: String) = repository.loadIcon(packageName)

    fun hasAccess(): Boolean = repository.hasAccess()

    /**
     * Opens the source app's conversation through its own PendingIntent —
     * the officially supported Android handle (bubble first when allowed).
     * CHRONOS never fabricates an intent into another app.
     */
    fun open(item: NotificationItem, useBubbles: Boolean): Boolean =
        launcher.openHandle(item, useBubbles = useBubbles, allowFullScreen = true)

    /**
     * Opens the source app on top of CHRONOS via [FloatingAppLauncher].
     *
     * Preference order (see `FloatingAppLauncher.plan`): floating window
     * (freeform) → split screen → plain full-screen → the notification's
     * own handle — honouring the Settings → Notifications → Floating App
     * Windows options. The result says which strategy won and, when the
     * floating window could not engage, why — so the UI can explain it
     * honestly instead of pretending.
     */
    fun openInMultiWindow(item: NotificationItem): FloatingAppLauncher.LaunchResult =
        launcher.open(item, repository.settings.value)

    /**
     * Step 1 of the guided Small Window flow — runs only from the user's
     * own "Open app" tap in the guidance dialog (bubble first when the
     * source app offers one, otherwise a plain launch so the app shows up
     * in Recents for the Small Window gesture).
     */
    fun openFromGuide(item: NotificationItem): Boolean =
        launcher.openFromGuide(item, repository.settings.value)

    /** Dismiss through the listener service; only possible with granted access. */
    fun dismiss(item: NotificationItem): Boolean {
        val listener = NotificationListener.active
        return if (listener != null) {
            listener.dismiss(item)
            // Drop it locally right away: a banner-silenced (snoozed)
            // notification may never produce a system removal callback,
            // and a dismissed icon must never linger. The system callback,
            // if it arrives, is a no-op on an already-removed key.
            repository.onRemoved(item.key)
            true
        } else {
            // Service not bound right now: drop it locally at least.
            repository.onRemoved(item.key)
            false
        }
    }

    /**
     * Dismiss after a successful quick reply. Besides the normal dismiss,
     * every re-post of this notification key is kept out for a few seconds:
     * chat apps rewrite their notification almost immediately after a reply
     * (read receipt / "sent" echo), which used to pop the icon straight back
     * in even though the user had just answered.
     */
    fun dismissAfterReply(item: NotificationItem): Boolean {
        val dismissed = dismiss(item)
        repository.suppressFor(item.key, REPLY_QUIET_MS)
        return dismissed
    }

    fun reply(item: NotificationItem, text: String, actionIndex: Int = 0): Boolean =
        QuickReplyHandler.send(appContext, item, actionIndex, text)

    private companion object {
        /** Quiet window after a reply: echo/read-receipt re-posts are ignored. */
        const val REPLY_QUIET_MS = 6_000L
    }
}
