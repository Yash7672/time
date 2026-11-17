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

/** Section F — performance and battery controls. */
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
        title = "Battery-friendly mode",
        checked = settings.batteryFriendly,
        onCheckedChange = { onChange(settings.copy(batteryFriendly = it)) },
        description = "Halves star count, drops updates to ~5 fps and disables " +
            "twinkling. Clock accuracy is unaffected.",
    )

    SwitchRow(
        title = "Battery saver",
        checked = settings.batterySaver,
        onCheckedChange = { onChange(settings.copy(batterySaver = it)) },
        description = "Forces a fully static background regardless of the " +
            "star settings. Strongest in-app saving.",
    )

    SwitchRow(
        title = "Reduce animations",
        checked = settings.reduceAnimations,
        onCheckedChange = { onChange(settings.copy(reduceAnimations = it)) },
        description = "Stops star drift, colon pulse and burn-in drift.",
    )

    SwitchRow(
        title = "Pause background effects when inactive",
        checked = settings.pauseWhenInactive,
        onCheckedChange = { onChange(settings.copy(pauseWhenInactive = it)) },
        description = "Freezes the star field 30 s after the last tap; resumes " +
            "instantly on the next touch.",
    )

    SwitchRow(
        title = "Lightweight rendering mode",
        checked = settings.lightweightRender,
        onCheckedChange = { onChange(settings.copy(lightweightRender = it)) },
        description = "Caps particles at 40 and removes per-frame work entirely.",
    )

    NoteText(
        "CHRONOS runs no background service for the clock, holds no wake lock, " +
            "uses no WorkManager, no location and no network. The clock only " +
            "ticks while the screen is visible.",
    )
}
