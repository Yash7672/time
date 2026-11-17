package com.chronos.clock.domain.model

enum class RotationPreference(val label: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
}

data class DisplaySettings(
    /**
     * Keep the screen awake while CHRONOS is open. Off by default: keeping the
     * display on always consumes significant battery on any device.
     */
    val keepScreenAwake: Boolean = false,
    /** Full-screen (immersive) mode: hides status and navigation bars. */
    val fullscreen: Boolean = true,
    val rotation: RotationPreference = RotationPreference.AUTO,
    /** Optional in-app brightness override (see [ThemeSettings.windowBrightness]). */
    val brightnessShortcut: Boolean = false,
)
