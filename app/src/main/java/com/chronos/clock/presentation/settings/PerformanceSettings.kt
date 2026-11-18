package com.chronos.clock.presentation.settings

import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.chronos.clock.domain.model.PerformanceSettings
import com.chronos.clock.ui.components.NoteText
import com.chronos.clock.ui.components.SectionHeader
import com.chronos.clock.ui.components.SwitchRow

/**
 * Section F — the single strongest battery control. Everything else
 * (star throttling, inactivity pausing, low-fps rendering) is automatic
 * and needs no user-facing switch.
 */
@Composable
fun PerformanceSettingsSection(
    settings: PerformanceSettings,
    onChange: (PerformanceSettings) -> Unit,
) {
    val context = LocalContext.current

    // Observe the real system battery-saver state (read-only, no permission).
    val systemBatterySaver by produceState(initialValue = false) {
        val pm = context.getSystemService(PowerManager::class.java)
        while (true) {
            value = pm?.isPowerSaveMode == true
            kotlinx.coroutines.delay(5_000)
        }
    }

    SectionHeader("Performance")

    if (systemBatterySaver && !settings.batterySaver) {
        NoteText(
            "Android battery saver is ON. CHRONOS detects it and automatically " +
                "collapses star motion to a static frame.",
        )
    }

    SwitchRow(
        title = "Battery saver",
        checked = settings.batterySaver,
        onCheckedChange = { onChange(settings.copy(batterySaver = it)) },
        description = "Collapses every background effect to a fully static " +
            "black screen and honours Android's own battery saver. " +
            "Clock accuracy is unaffected.",
    )

    SwitchRow(
        title = "Pause stars when idle",
        checked = settings.pauseWhenInactive,
        onCheckedChange = { onChange(settings.copy(pauseWhenInactive = it)) },
        description = "Freezes the star field 30 s after your last touch so " +
            "a clock left on the desk sips power; the next tap resumes it. " +
            "Off by default so the sky keeps drifting. The clock itself " +
            "never pauses.",
    )

    NoteText(
        "Everything else is automatic: stars throttle to ~15 fps and stop " +
            "entirely when CHRONOS is not visible. The clock runs no " +
            "background service, holds no wake lock, uses no WorkManager, " +
            "no location and no network.",
    )
}
