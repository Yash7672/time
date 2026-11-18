package com.chronos.clock.domain.model

enum class StarDensity(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
}

enum class StarSpeed(val label: String) {
    SLOW("Slow"),
    NORMAL("Normal"),
}

data class StarSettings(
    /** Master switch for the star field (off = static pure black background). */
    val enabled: Boolean = true,
    val density: StarDensity = StarDensity.MEDIUM,
    val speed: StarSpeed = StarSpeed.NORMAL,
    val twinkling: Boolean = true,
) {
    /**
     * ULTRA_BATTERY forces a static field and PURE_AMOLED allows almost no
     * animation — both collapse to "no continuous animation".
     */
    fun effectivelyAnimated(mode: AmoledMode): Boolean = when (mode) {
        AmoledMode.ULTRA_BATTERY -> false
        AmoledMode.PURE_AMOLED -> false
        AmoledMode.SPACE_AMOLED -> enabled
    }
}
