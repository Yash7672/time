package com.chronos.clock.core.constants

/** Global constants for CHRONOS. */
object AppConstants {
    /** Bounds for star-field particle counts by density preset. */
    const val STARS_LOW = 60
    const val STARS_MEDIUM = 120
    const val STARS_HIGH = 220

    /** Hard cap so a runaway setting can never allocate an unbounded list. */
    const val STARS_MAX = 300

    /** Maximum notification entries kept in memory (never persisted). */
    const val MAX_NOTIFICATIONS = 50

    /** Maximum distinct app icons shown around the clock. */
    const val MAX_VISIBLE_ICONS = 8

    /** Milliseconds of inactivity after which background effects pause. */
    const val INACTIVITY_PAUSE_MS = 30_000L

    /** Frame intervals for the star field, in milliseconds. */
    const val STAR_FRAME_INTERVAL_NORMAL_MS = 66L // ~15 fps: plenty for slow drift
    const val STAR_FRAME_INTERVAL_BATTERY_MS = 200L // ~5 fps in battery-friendly mode

    /** Repository link shown in Settings → About. */
    const val GITHUB_URL = "https://github.com/Yash7672/time"
}
