package com.chronos.clock.presentation.settings

import androidx.compose.runtime.Composable
import com.chronos.clock.domain.model.AppSettings
import com.chronos.clock.domain.model.RotationPreference
import com.chronos.clock.presentation.theme.Chronos
import com.chronos.clock.ui.components.NoteText
import com.chronos.clock.ui.components.OptionRow
import com.chronos.clock.ui.components.SectionHeader
import com.chronos.clock.ui.components.SwitchRow

/**
 * Section E — display options.
 *
 * Keep-screen-awake is off by default and clearly warns about battery cost.
 * Brightness is a window-level override only; system brightness is untouched.
 */
@Composable
fun DisplaySettingsSection(
    settings: AppSettings,
    overlayEnabled: Boolean,
    onChange: (AppSettings) -> Unit,
    onOverlayChange: (Boolean) -> Unit,
) {
    SectionHeader("Display")

    SwitchRow(
        title = "Keep screen awake",
        checked = settings.display.keepScreenAwake,
        onCheckedChange = {
            onChange(settings.copy(display = settings.display.copy(keepScreenAwake = it)))
        },
        description = if (settings.display.keepScreenAwake) {
            "On — the display stays lit while CHRONOS is open. This always " +
                "costs significant battery on any device, regardless of how " +
                "well the app is optimised."
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

    OptionRow(
        title = "Screen rotation",
        options = RotationPreference.entries,
        selected = settings.display.rotation,
        optionLabel = { it.label },
        onSelect = {
            onChange(settings.copy(display = settings.display.copy(rotation = it)))
        },
        description = "Auto follows the device sensor; Portrait / Landscape " +
            "lock CHRONOS to that orientation.",
    )

    SwitchRow(
        title = "Brightness shortcut",
        checked = settings.display.brightnessShortcut,
        onCheckedChange = { enable ->
            onChange(
                settings.copy(
                    display = settings.display.copy(brightnessShortcut = enable),
                    // Toggling off returns the window to system brightness.
                    theme = if (enable) {
                        settings.theme.copy(windowBrightness = 0.85f)
                    } else {
                        settings.theme.copy(windowBrightness = -1f)
                    },
                ),
            )
        },
        description = "Applies an 85% window-level brightness while CHRONOS is " +
            "open, and restores the system value when switched off. The system " +
            "slider itself is never changed.",
    )

    if (settings.theme.windowBrightness >= 0f && !settings.display.brightnessShortcut) {
        SwitchRow(
            title = "Reset to system brightness",
            checked = false,
            onCheckedChange = {
                onChange(settings.copy(theme = settings.theme.copy(windowBrightness = -1f)))
            },
            description = "CHRONOS currently overrides its own window brightness.",
        )
    }

    SwitchRow(
        title = "Floating clock overlay",
        checked = overlayEnabled,
        onCheckedChange = onOverlayChange,
        description = "Tiny always-on-top clock (tap to open CHRONOS). Requires " +
            "the 'display over other apps' special access — Android shows a " +
            "consent screen. Closing the floating window never closes CHRONOS.",
    )

    NoteText(
        "Android automatically respects battery saver and the system brightness " +
            "slider; CHRONOS never writes to either.",
    )
}
