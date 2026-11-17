package com.chronos.clock.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.clock.domain.model.AmoledMode
import com.chronos.clock.domain.model.ChronosTheme
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

    SliderRow(
        title = "Window brightness",
        value = if (settings.windowBrightness < 0f) 1f else settings.windowBrightness,
        valueRange = 0.1f..1f,
        onChange = { onChange(settings.copy(windowBrightness = it)) },
        valueLabel = if (settings.windowBrightness < 0f) {
            "System"
        } else {
            "${(settings.windowBrightness * 100).toInt()}%"
        },
        description = "Applies only to the CHRONOS window; system brightness is " +
            "never modified. This is the fixed fallback used while Auto " +
            "brightness (Display) is off or the sensor has no reading.",
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
