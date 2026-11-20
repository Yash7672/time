package com.chronos.clock.domain.model

data class DisplaySettings(
    /**
     * Keep the screen awake while CHRONOS is open. Off by default: keeping the
     * display on always consumes significant battery on any device.
     */
    val keepScreenAwake: Boolean = false,
    /** Full-screen (immersive) mode: hides status and navigation bars. */
    val fullscreen: Boolean = true,
    /**
     * Auto brightness from the ambient light sensor: dims the CHRONOS
     * window in a dark room and brightens it in a bright one, only while
     * the app is open. Falls back to the manual value when no reading
     * exists (no sensor, or app in background).
     */
    val autoBrightness: Boolean = true,
    /**
     * Low-battery screen release: when charge falls to [lowBatteryThreshold]
     * or below, hand the keep-awake flag back to Android so the display
     * sleeps (locks) by the normal system screen-timeout instead of lighting
     * the phone down to a shutdown. Only ever releases the flag CHRONOS
     * itself set — it never forces the screen off. On by default.
     */
    val lowBatteryRelease: Boolean = true,
    /** Charge percent (5–50, step 5) at or below which the screen releases. */
    val lowBatteryThreshold: Int = 30,
)
