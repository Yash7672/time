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

/**
 * Optional clock digit colour the user can pick independently of the
 * background theme. `DEFAULT` keeps the theme palette's own text colour.
 * ARGB values are chosen to stay legible on the near-black backgrounds
 * without being full-intensity white.
 */
enum class ClockColor(val label: String, val argb: Long?) {
    DEFAULT("Theme", null),
    WHITE("White", 0xFFF4F6FB),
    ICE("Ice", 0xFFBFE9FF),
    CYAN("Cyan", 0xFF66E0FF),
    BLUE("Blue", 0xFF7FB2FF),
    MINT("Mint", 0xFF8BE7B0),
    AMBER("Amber", 0xFFFFC46B),
    ORANGE("Orange", 0xFFFF9E5E),
    ROSE("Rose", 0xFFFF9EC7),
    VIOLET("Violet", 0xFFC6A8FF),
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
    /** User-chosen colour for the clock digits; DEFAULT follows the theme. */
    val clockColor: ClockColor = ClockColor.DEFAULT,
)
