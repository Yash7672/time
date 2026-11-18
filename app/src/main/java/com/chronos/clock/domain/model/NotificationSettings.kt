package com.chronos.clock.domain.model

/**
 * Privacy-first notification integration settings.
 *
 * Notification content is never persisted — this only records preferences.
 */
data class NotificationSettings(
    /**
     * Master switch; ON by default so icons work the moment notification
     * access exists. Enabling (or re-enabling) prompts for notification-
     * listener access — the one consent Android never lets an app self-grant.
     */
    val integrationEnabled: Boolean = true,
    /** Empty = all apps. Otherwise only these packages are surfaced. */
    val allowedPackages: Set<String> = emptySet(),
    val showAppIcons: Boolean = true,
    val showIndicators: Boolean = true,
    /**
     * ICONS_ONLY  — only app icons and counts (default, most private).
     * SHOW_TITLES — allow titles inside the in-app notification picker.
     */
    val privacy: NotificationPrivacy = NotificationPrivacy.ICONS_ONLY,
    /** Offer RemoteInput quick reply where the source app supports it. */
    val quickReplyEnabled: Boolean = true,
    /**
     * Prefer the source notification's bubble intent when the source app and
     * Android version provide one. Bubbles themselves remain under the source
     * app's control — see README "Known Android limitations".
     */
    val bubblesEnabled: Boolean = true,
)

enum class NotificationPrivacy(val label: String) {
    ICONS_ONLY("App icons only"),
    SHOW_TITLES("Show titles in picker"),
}
