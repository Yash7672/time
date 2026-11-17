package com.chronos.clock.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.chronos.clock.domain.model.ChronosTheme

/**
 * Resolved colours for the current theme. Kept as a plain immutable holder so
 * composables read exactly the values they need (minimal recomposition).
 */
@Immutable
data class ChronosPalette(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textFaint: Color,
    val accent: Color,
    val accentSecondary: Color,
    val isLight: Boolean,
    /** True when the background is exactly #000000 (all pixels off). */
    val isPureBlack: Boolean,
)

fun paletteFor(theme: ChronosTheme): ChronosPalette = when (theme) {
    ChronosTheme.AMOLED_BLACK -> ChronosPalette(
        background = ChronosColors.PureBlack,
        surface = Color(0xFF0A0A0C),
        textPrimary = ChronosColors.SoftWhite,
        textSecondary = ChronosColors.DimWhite,
        textFaint = ChronosColors.FaintGrey,
        accent = ChronosColors.AccentBlue,
        accentSecondary = ChronosColors.AccentPurple,
        isLight = false,
        isPureBlack = true,
    )
    ChronosTheme.DEEP_SPACE -> ChronosPalette(
        background = ChronosColors.DeepSpace,
        surface = Color(0xFF0A0B10),
        textPrimary = ChronosColors.SoftWhite,
        textSecondary = ChronosColors.DimWhite,
        textFaint = ChronosColors.FaintGrey,
        accent = ChronosColors.AccentBlue,
        accentSecondary = ChronosColors.AccentPurple,
        isLight = false,
        isPureBlack = false,
    )
    ChronosTheme.MIDNIGHT_BLUE -> ChronosPalette(
        background = ChronosColors.MidnightBlue,
        surface = Color(0xFF05070F),
        textPrimary = ChronosColors.SoftWhite,
        textSecondary = ChronosColors.DimWhite,
        textFaint = ChronosColors.FaintGrey,
        accent = ChronosColors.AccentBlue,
        accentSecondary = ChronosColors.StarBlue,
        isLight = false,
        isPureBlack = false,
    )
    ChronosTheme.PURPLE_GALAXY -> ChronosPalette(
        background = ChronosColors.PurpleGalaxy,
        surface = Color(0xFF0A0510),
        textPrimary = ChronosColors.SoftWhite,
        textSecondary = ChronosColors.DimWhite,
        textFaint = ChronosColors.FaintGrey,
        accent = ChronosColors.AccentPurple,
        accentSecondary = ChronosColors.AccentBlue,
        isLight = false,
        isPureBlack = false,
    )
    ChronosTheme.MINIMAL_DARK -> ChronosPalette(
        background = ChronosColors.MinimalDark,
        surface = Color(0xFF141416),
        textPrimary = ChronosColors.DimWhite,
        textSecondary = ChronosColors.Grey,
        textFaint = ChronosColors.FaintGrey,
        accent = ChronosColors.Grey,
        accentSecondary = ChronosColors.FaintGrey,
        isLight = false,
        isPureBlack = false,
    )
    ChronosTheme.LIGHT -> ChronosPalette(
        background = ChronosColors.LightBg,
        surface = Color(0xFFFFFFFF),
        textPrimary = ChronosColors.LightText,
        textSecondary = ChronosColors.LightDim,
        textFaint = ChronosColors.LightGrey,
        accent = Color(0xFF3D63DD),
        accentSecondary = Color(0xFF7451C8),
        isLight = true,
        isPureBlack = false,
    )
}

val LocalChronosPalette = staticCompositionLocalOf { paletteFor(ChronosTheme.AMOLED_BLACK) }

/**
 * Minimal Material 3 bridge: only the colours Settings needs. The home screen
 * reads [LocalChronosPalette] directly and draws no Material surfaces at all.
 */
@Composable
fun ChronosTheme(
    theme: ChronosTheme,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(theme)
    val scheme = if (palette.isLight) {
        lightColorScheme(
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            primary = palette.accent,
            onPrimary = Color.White,
            outline = palette.textFaint,
        )
    } else {
        darkColorScheme(
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            primary = palette.accent,
            onPrimary = Color.Black,
            outline = palette.textFaint,
        )
    }
    CompositionLocalProvider(LocalChronosPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Convenience accessor for palette values inside composables. */
object Chronos {
    val palette: ChronosPalette
        @Composable @ReadOnlyComposable get() = LocalChronosPalette.current
}
