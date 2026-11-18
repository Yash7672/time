package com.chronos.clock.domain.model

data class PerformanceSettings(
    /** Reduces star count, animation rate and disables twinkling. */
    val batteryFriendly: Boolean = false,
    /** Disables non-essential UI motion (fades, colon pulse, drift). */
    val reduceAnimations: Boolean = false,
    /**
     * Pause the star field after a period of inactivity. Off by default so
     * the sky keeps drifting the whole time CHRONOS is open; the field always
     * stops anyway when the app is no longer visible.
     */
    val pauseWhenInactive: Boolean = false,
    /** Caps particles to the lowest preset and skips per-frame work. */
    val lightweightRender: Boolean = false,
    /**
     * Master battery saver: also honours the Android battery-saver state and
     * collapses effects to the static AMOLED behaviour.
     */
    val batterySaver: Boolean = false,
)

/** Aggregated, immutable snapshot of every persisted setting. */
data class AppSettings(
    val clock: ClockSettings = ClockSettings(),
    val theme: ThemeSettings = ThemeSettings(),
    val stars: StarSettings = StarSettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    val display: DisplaySettings = DisplaySettings(),
    val performance: PerformanceSettings = PerformanceSettings(),
)
