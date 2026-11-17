package com.chronos.clock.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * AMOLED-first palette.
 *
 * Every dark theme keeps a #000000 (or near-black) background so pixels stay
 * off. Foreground colours are deliberately dim — soft white / grey — to limit
 * illuminated pixel area and reduce burn-in risk.
 */
object ChronosColors {

    // Backgrounds ----------------------------------------------------------
    val PureBlack = Color(0xFF000000)
    val DeepSpace = Color(0xFF010103)
    val MidnightBlue = Color(0xFF000208)
    val PurpleGalaxy = Color(0xFF030106)
    val MinimalDark = Color(0xFF0A0A0C)
    val LightBg = Color(0xFFF4F5F7)

    // Text -----------------------------------------------------------------
    // Soft white instead of #FFFFFF to avoid excessive OLED illumination.
    val SoftWhite = Color(0xFFE6E8EC)
    val DimWhite = Color(0xFFB8BCC4)
    val Grey = Color(0xFF8A8F98)
    val FaintGrey = Color(0xFF5A5E66)

    // Accents (used sparingly, small areas only) ---------------------------
    val StarWhite = Color(0xFFD8DCE2)
    val StarBlue = Color(0xFF7FA8FF)
    val StarPurple = Color(0xFFB48CFF)
    val AccentBlue = Color(0xFF6E93FF)
    val AccentPurple = Color(0xFF9A7BFF)

    // Light theme ----------------------------------------------------------
    val LightText = Color(0xFF14161A)
    val LightDim = Color(0xFF4A4E57)
    val LightGrey = Color(0xFF767B85)
}
