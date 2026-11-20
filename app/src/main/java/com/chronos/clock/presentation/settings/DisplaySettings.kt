package com.chronos.clock.presentation.settings

import androidx.compose.runtime.Composable
import com.chronos.clock.domain.model.AppSettings
import com.chronos.clock.presentation.theme.Chronos
import com.chronos.clock.ui.components.NoteText
import com.chronos.clock.ui.components.SectionHeader
import com.chronos.clock.ui.components.SwitchRow

/**
 * Section E — display options.
 *
 * Keep-screen-awake is off by default and clearly warns about battery cost.
 * Brightness is a window-level override only; system brightness is untouched.
 * Rotation is automatic while the app is open (activity-level sensor
 * orientation) and the system rotation setting is never written.
 */
@Composable
fun DisplaySettingsSection(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
) {
    SectionHeader("Display")

    SwitchRow(
        title = "Auto brightness",
        checked = settings.display.autoBrightness,
        onCheckedChange = {
            onChange(settings.copy(display = settings.display.copy(autoBrightness = it)))
        },
        description = "Uses the phone's light sensor: the clock dims in a " +
            "dark room and brightens in a bright one — only while CHRONOS " +
            "is open. Off = fixed manual window brightness.",
    )

    SwitchRow(
        title = "Keep screen awake",
        checked = settings.display.keepScreenAwake,
        onCheckedChange = {
            onChange(settings.copy(display = settings.display.copy(keepScreenAwake = it)))
        },
        description = if (settings.display.keepScreenAwake) {
            "On — the display stays lit while CHRONOS is open. This always " +
                "costs significant battery on any device, regardless of how " +
                "well the app is optimised. At 30% battery or less CHRONOS " +
                "releases it automatically: the screen then sleeps by your " +
                "system timeout, so the phone can't run down to shutdown."
        } else {
            "Off (recommended). The screen sleeps normally and CHRONOS stops " +
                "all work while invisible."
        },
    )

    SwitchRow(
        title = "Full-screen mode",
        checked = settings.display.fullscreen,
        onCheckedChange = {
            onChange(settings.copy(display = settings.display.copy(fullscreen = it)))
        },
        description = "Hides the status and navigation bars for an edge-to-edge clock.",
    )

    NoteText(
        "Auto-rotation follows the sensor while CHRONOS is open and never " +
            "changes your system rotation setting — the home screen and " +
            "other apps keep your own choice. Android automatically " +
            "respects battery saver and the system brightness slider; " +
            "CHRONOS never writes to either.",
    )
}
