package com.chronos.clock.domain.model

/** Clock formatting and typography options. */
data class ClockSettings(
    /** true = 24-hour, false = 12-hour with AM/PM. */
    val use24Hour: Boolean = true,
    val showSeconds: Boolean = true,
    val showDate: Boolean = true,
    val showDay: Boolean = true,
    /** User font-size multiplier applied on top of the auto-fitted size (0.75..1.5). */
    val fontScale: Float = 1.0f,
    /** Clock text opacity 0.4..1.0 — dim white on AMOLED instead of max-bright white. */
    val brightness: Float = 0.92f,
)
