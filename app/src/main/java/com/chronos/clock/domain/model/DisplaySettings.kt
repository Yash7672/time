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
)
