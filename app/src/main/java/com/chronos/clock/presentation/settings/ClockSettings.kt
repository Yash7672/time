package com.chronos.clock.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.clock.domain.model.ClockSettings
import com.chronos.clock.presentation.theme.Chronos
import com.chronos.clock.ui.components.NoteText
import com.chronos.clock.ui.components.OptionRow
import com.chronos.clock.ui.components.SectionHeader
import com.chronos.clock.ui.components.SwitchRow

/** Section A — clock format, visibility, size and dimming. */
@Composable
fun ClockSettingsSection(
    settings: ClockSettings,
    onChange: (ClockSettings) -> Unit,
) {
    val palette = Chronos.palette
    SectionHeader("Clock")

    OptionRow(
        title = "Time format",
        options = listOf(true, false),
        selected = settings.use24Hour,
        optionLabel = { if (it) "24-hour" else "12-hour" },
        onSelect = { onChange(settings.copy(use24Hour = it)) },
    )

    SwitchRow(
        title = "Show seconds",
        checked = settings.showSeconds,
        onCheckedChange = { onChange(settings.copy(showSeconds = it)) },
        description = if (settings.showSeconds) {
            "Updates align exactly to each second boundary."
        } else {
            "Ticks once per minute — the quietest option."
        },
    )

    SwitchRow(
        title = "Show date",
        checked = settings.showDate,
        onCheckedChange = { onChange(settings.copy(showDate = it)) },
    )

    // The day of week rides along with the date in DateView — one row
    // fewer, and the pair always reads as one line.

    SliderRow(
        title = "Clock size",
        value = settings.fontScale,
        valueRange = 0.75f..1.5f,
        onChange = { onChange(settings.copy(fontScale = it)) },
        valueLabel = "%.2f×".format(settings.fontScale),
    )

    SliderRow(
        title = "Clock brightness",
        value = settings.brightness,
        valueRange = 0.35f..1f,
        onChange = { onChange(settings.copy(brightness = it)) },
        valueLabel = "${(settings.brightness * 100).toInt()}%",
        description = "Dim the clock text to reduce OLED illumination. " +
            "This affects CHRONOS's own text only — never system brightness.",
    )

    NoteText(
        "Tabular figures keep every digit the same width, so the clock never " +
            "shifts horizontally while seconds change.",
    )
}

@Composable
internal fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: String? = null,
    description: String? = null,
) {
    val palette = Chronos.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = TextStyle(fontSize = 15.sp),
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (valueLabel != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = valueLabel,
                    style = TextStyle(fontSize = 13.sp),
                    color = palette.accent,
                )
            }
        }
        if (description != null) {
            Text(
                text = description,
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                color = palette.textFaint,
                modifier = Modifier.padding(top = 3.dp, bottom = 4.dp),
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = palette.textPrimary,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.surface,
            ),
        )
    }
}
