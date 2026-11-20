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
    /**
     * Suppress the system heads-up banner **while CHRONOS is on screen**:
     * the listener snoozes each message-like notification the moment it
     * posts, so no preview drops from the top — the message lives only in
     * its app icon here, reachable with tap-and-hold. Calls, alarms and
     * media are never silenced, and with CHRONOS in the background the
     * phone behaves exactly like stock, so nothing can be missed.
     */
    val silenceBanners: Boolean = true,

    // ——— Settings → Notifications → Floating App Windows ———
    /**
     * Master switch for opening tapped apps in a real Android freeform
     * (floating) window over CHRONOS. Availability is decided by the device
     * build and the target app's resize declaration — CHRONOS never forces it.
     */
    val floatingWindowsEnabled: Boolean = true,
    /**
     * ON  → try the floating window first, then split screen.
     * OFF → split screen first; the floating window only if split is refused.
     */
    val preferFloatingWindows: Boolean = true,
    /**
     * When neither floating nor split screen worked, allow a plain
     * full-screen launch. OFF = never launch full-screen — only the source
     * app's official notification bubble may still open.
     */
    val allowFullScreenFallback: Boolean = true,
    /**
     * Explain, after a tap, when the device or app blocks floating windows:
     * what opened instead and where floating windows can be enabled on
     * devices that offer the option. OFF = fail silently.
     */
    val showFloatingInstructions: Boolean = true,
)

enum class NotificationPrivacy(val label: String) {
    ICONS_ONLY("App icons only"),
    SHOW_TITLES("Show titles in picker"),
}
