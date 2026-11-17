package com.chronos.clock.presentation.settings

import androidx.compose.runtime.Composable
import com.chronos.clock.domain.model.StarDensity
import com.chronos.clock.domain.model.StarSettings
import com.chronos.clock.domain.model.StarSpeed
import com.chronos.clock.ui.components.NoteText
import com.chronos.clock.ui.components.OptionRow
import com.chronos.clock.ui.components.SectionHeader
import com.chronos.clock.ui.components.SwitchRow

/** Section C — animated star background controls. */
@Composable
fun StarBackgroundSettingsSection(
    settings: StarSettings,
    batteryMode: Boolean,
    onChange: (StarSettings) -> Unit,
) {
    SectionHeader("Star background")

    SwitchRow(
        title = "Enable star animation",
        checked = settings.enabled,
        onCheckedChange = { onChange(settings.copy(enabled = it)) },
        description = "Off = pure static black background (most battery efficient).",
    )

    // The remaining controls only matter while animation is on.
    if (settings.enabled && !batteryMode) {
        OptionRow(
            title = "Star density",
            options = StarDensity.entries,
            selected = settings.density,
            optionLabel = { it.label },
            onSelect = { onChange(settings.copy(density = it)) },
            description = "Low ≈ 60, Medium ≈ 120, High ≈ 220 particles. " +
                "Counts are hard-capped by the performance settings.",
        )

        OptionRow(
            title = "Star speed",
            options = StarSpeed.entries,
            selected = settings.speed,
            optionLabel = { it.label },
            onSelect = { onChange(settings.copy(speed = it)) },
            description = "Drift is intentionally slow — a full screen pass " +
                "takes minutes, not seconds.",
        )

        SwitchRow(
            title = "Twinkling",
            checked = settings.twinkling,
            onCheckedChange = { onChange(settings.copy(twinkling = it)) },
            description = "Gentle alpha modulation only; disabled automatically " +
                "when battery saver is on.",
        )
    }

    NoteText(
        "Star updates are throttled to ~15 fps, freeze when battery saver " +
            "is on, and pause completely whenever CHRONOS is not visible.",
    )
}
