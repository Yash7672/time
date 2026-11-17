package com.chronos.clock.domain.model

/**
 * The three AMOLED display modes required by the AMOLED optimization spec.
 *
 * 1. PURE_AMOLED  — pure black, minimal dim-white clock, almost no animation.
 * 2. SPACE_AMOLED — black background with subtle, slow moving stars.
 * 3. ULTRA_BATTERY — static black background, minimal clock, reduced visual
 *    updates, no unnecessary effects.
 */
enum class AmoledMode(val label: String, val description: String) {
    PURE_AMOLED(
        label = "Pure AMOLED",
        description = "Completely black screen, dim white clock, almost no animation.",
    ),
    SPACE_AMOLED(
        label = "Space AMOLED",
        description = "Black background with subtle, slow moving stars.",
    ),
    ULTRA_BATTERY(
        label = "Ultra Battery",
        description = "Static black screen, minimal clock, reduced visual updates.",
    ),
}

/** Colour themes. All dark themes keep a near-black AMOLED background. */
enum class ChronosTheme(val label: String) {
    AMOLED_BLACK("AMOLED Black"),
    DEEP_SPACE("Deep Space"),
    MIDNIGHT_BLUE("Midnight Blue"),
    PURPLE_GALAXY("Purple Galaxy"),
    MINIMAL_DARK("Minimal Dark"),
    LIGHT("Light"),
}

data class ThemeSettings(
    val theme: ChronosTheme = ChronosTheme.AMOLED_BLACK,
    val amoledMode: AmoledMode = AmoledMode.SPACE_AMOLED,
    /**
     * Subtle anti burn-in drift: shifts the clock by a few pixels at safe
     * intervals. Never affects time accuracy or readability.
     */
    val burnInProtection: Boolean = true,
    /** Optional minimal always-visible clock style (fewer illuminated pixels). */
    val minimalClockMode: Boolean = false,
    /**
     * Window-level brightness (0.1..1.0) applied only to CHRONOS's own window;
     * never writes system brightness. -1 = follow the system.
     */
    val windowBrightness: Float = -1f,
)
