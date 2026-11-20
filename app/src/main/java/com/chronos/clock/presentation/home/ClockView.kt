package com.chronos.clock.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.clock.core.utils.ClockText
import com.chronos.clock.domain.model.ClockSettings
import com.chronos.clock.presentation.theme.Chronos
import kotlin.math.min

/**
 * The primary clock: hours : minutes : seconds in tabular-figure typography.
 *
 * AMOLED behaviour:
 *  - text colour comes from the theme palette (soft white, never #FFFFFF at
 *    full brightness unless the user raises the opacity slider),
 *  - tabular figures (`tnum`) prevent digit-width shifting,
 *  - the colon is a static separator — the clock redraws nothing except the
 *    digits themselves, once per second (or minute),
 *  - font size auto-fits the available box so rotation never clips text.
 */
@Composable
fun ClockView(
    time: ClockText,
    settings: ClockSettings,
    minimal: Boolean,
    modifier: Modifier = Modifier,
    /** User-picked digit colour; null = the theme palette's text colour. */
    clockColor: androidx.compose.ui.graphics.Color? = null,
) {
    val palette = Chronos.palette
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // Auto-fit: keep the clock inside the box in both orientations.
        val charCount = time.display.length
        val widthPerChar = if (charCount > 0) maxWidth / (charCount * 0.62f) else 100.dp
        val heightCap = if (isLandscape) maxHeight * 0.62f else maxHeight * 0.34f
        val fitted: Dp = if (widthPerChar < heightCap) widthPerChar else heightCap
        val fontSize = (fitted.value * settings.fontScale).sp

        val baseStyle = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = if (minimal) FontWeight.Light else FontWeight.Normal,
            fontSize = fontSize,
            // Tabular numerals: every digit occupies the same advance width.
            fontFeatureSettings = "tnum",
            letterSpacing = if (minimal) 2.sp else 0.sp,
        )

        val digitBase = clockColor ?: palette.textPrimary
        val color = digitBase.copy(alpha = settings.brightness.coerceIn(0.35f, 1f))
        // Colon: dimmer companion of the digit colour (theme faint by default).
        val colonBase = clockColor ?: palette.textFaint
        val colonColor = colonBase.copy(alpha = settings.brightness.coerceIn(0.35f, 1f))

        if (settings.meridiemShown) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClockDigits(time, baseStyle, color, colonColor)
                if (time.meridiem != null) {
                    Spacer(Modifier.width((fontSize.value * 0.16f).dp))
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Spacer(Modifier.height((fontSize.value * 0.18f).dp))
                        androidx.compose.material3.Text(
                            text = time.meridiem,
                            style = baseStyle.copy(
                                fontSize = (fontSize.value * 0.30f).sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            color = palette.textSecondary.copy(alpha = settings.brightness),
                        )
                    }
                }
            }
        } else {
            ClockDigits(time, baseStyle, color, colonColor)
        }
    }
}

@Composable
private fun ClockDigits(
    time: ClockText,
    style: TextStyle,
    digitColor: androidx.compose.ui.graphics.Color,
    colonColor: androidx.compose.ui.graphics.Color,
) {
    val colonModifier = Modifier.padding(horizontal = (style.fontSize.value * 0.01f).dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Digit(text = time.hours, style = style, color = digitColor)
        Colon(style = style, color = colonColor, modifier = colonModifier)
        Digit(text = time.minutes, style = style, color = digitColor)
        if (time.seconds != null) {
            Colon(style = style, color = colonColor, modifier = colonModifier)
            Digit(text = time.seconds, style = style, color = digitColor)
        }
    }
}

@Composable
private fun Digit(text: String, style: TextStyle, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.material3.Text(text = text, style = style, color = color, maxLines = 1)
}

@Composable
private fun Colon(
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Text(
        text = ":",
        style = style,
        color = color,
        modifier = modifier,
        maxLines = 1,
    )
}

/** Small helper so ClockSettings can express "show meridiem" in one place. */
private val ClockSettings.meridiemShown: Boolean get() = !use24Hour
