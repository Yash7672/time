package com.chronos.clock.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.clock.domain.model.AmoledMode
import com.chronos.clock.domain.model.ChronosTheme
import com.chronos.clock.domain.model.ClockColor
import com.chronos.clock.domain.model.ThemeSettings
import com.chronos.clock.presentation.theme.Chronos
import com.chronos.clock.ui.components.NoteText
import com.chronos.clock.ui.components.OptionRow
import com.chronos.clock.ui.components.SectionHeader
import com.chronos.clock.ui.components.SwitchRow

/** Section B — theme plus the three AMOLED display modes. */
@Composable
fun AppearanceSettingsSection(
    settings: ThemeSettings,
    onChange: (ThemeSettings) -> Unit,
) {
    SectionHeader("Appearance")

    OptionRow(
        title = "Theme",
        options = ChronosTheme.entries,
        selected = settings.theme,
        optionLabel = { it.label },
        onSelect = { onChange(settings.copy(theme = it)) },
        description = "All dark themes keep a near-black background so most " +
            "OLED pixels stay off.",
    )

    ClockColorPicker(
        selected = settings.clockColor,
        onSelect = { onChange(settings.copy(clockColor = it)) },
    )

    SectionHeader("AMOLED display mode")

    AmoledMode.entries.forEach { mode ->
        AmoledModeRow(
            title = mode.label,
            description = mode.description,
            selected = settings.amoledMode == mode,
            onSelect = { onChange(settings.copy(amoledMode = mode)) },
        )
    }

    NoteText(
        "Pure AMOLED and Ultra Battery render a completely static black screen. " +
            "Space AMOLED adds very slow, dim stars. None of the modes change " +
            "clock accuracy — timekeeping is independent of visual effects.",
    )

    SwitchRow(
        title = "Anti burn-in drift",
        checked = settings.burnInProtection,
        onCheckedChange = { onChange(settings.copy(burnInProtection = it)) },
        description = "Shifts the whole clock block by 1–2 px every 10 minutes " +
            "so static pixels never bake in. Individually imperceptible.",
    )

    SwitchRow(
        title = "Minimal clock mode",
        checked = settings.minimalClockMode,
        onCheckedChange = { onChange(settings.copy(minimalClockMode = it)) },
        description = "Always-on-display style: fewer illuminated pixels, " +
            "lighter weight, wider letter-spacing.",
    )
}

/**
 * Radio-style AMOLED mode row: a small radio glyph plus text — no cards and
 * no large bright surfaces, so selecting a mode never lights up the panel.
 */
@Composable
private fun AmoledModeRow(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val palette = Chronos.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (selected) "●" else "○",
                color = if (selected) palette.accent else palette.textFaint,
                style = TextStyle(fontSize = 13.sp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                color = if (selected) palette.textPrimary else palette.textSecondary,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                ),
            )
        }
        Text(
            text = description,
            color = palette.textFaint,
            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
            modifier = Modifier.padding(top = 3.dp, start = 23.dp),
        )
    }
}

/**
 * Wrapping swatch picker for the clock digit colour. Small circles and text
 * only — no bright filled surfaces, so choosing a colour keeps the AMOLED
 * look; the selected swatch is marked with an accent ring.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ClockColorPicker(
    selected: ClockColor,
    onSelect: (ClockColor) -> Unit,
) {
    val palette = Chronos.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Clock colour",
            style = TextStyle(fontSize = 15.sp),
            color = palette.textPrimary,
        )
        Text(
            text = "Colour of the time digits. \"Theme\" follows the active theme.",
            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
            color = palette.textFaint,
            modifier = Modifier.padding(top = 3.dp, bottom = 8.dp),
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClockColor.entries.forEach { option ->
                val isSelected = option == selected
                val swatch = option.argb?.let { Color(it) } ?: palette.textPrimary
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onSelect(option) }
                        .padding(vertical = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .then(
                                if (isSelected) {
                                    Modifier.border(1.5.dp, palette.accent, CircleShape)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = option.label,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        ),
                        color = if (isSelected) palette.accent else palette.textSecondary,
                    )
                }
            }
        }
    }
}
