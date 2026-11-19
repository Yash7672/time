package com.chronos.clock.presentation.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronos.clock.domain.model.NotificationPrivacy
import com.chronos.clock.domain.model.NotificationSettings
import com.chronos.clock.presentation.theme.Chronos
import com.chronos.clock.ui.components.NoteText
import com.chronos.clock.ui.components.OptionRow
import com.chronos.clock.ui.components.SectionHeader
import com.chronos.clock.ui.components.SwitchRow

/**
 * Section D — notification integration.
 *
 * Notification access is requested only here, with an explanation, and only
 * when the user flips the master switch.
 */
@Composable
fun NotificationSettingsSection(
    settings: NotificationSettings,
    accessGranted: Boolean,
    onChange: (NotificationSettings) -> Unit,
    onOpenAppPicker: () -> Unit,
) {
    val context = LocalContext.current
    val palette = Chronos.palette

    SectionHeader("Notifications")

    if (!accessGranted) {
        NoteText(
            "To show other apps' icons around the clock, CHRONOS needs " +
                "notification access. Android shows a system consent screen; " +
                "CHRONOS cannot grant it to itself. Notification content is " +
                "read on-device only and never stored or uploaded.",
        )
    }

    SwitchRow(
        title = "Notification integration",
        // Lit only when the feature can actually work: intent AND grant.
        checked = settings.integrationEnabled && accessGranted,
        onCheckedChange = { enable ->
            if (enable && !accessGranted) {
                // Open the system consent screen; the switch lights up once
                // the user returns with access granted (observed here and on
                // the home screen). The intent itself stays ON — this setting
                // now defaults to allowed.
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                onChange(settings.copy(integrationEnabled = true))
            } else {
                onChange(settings.copy(integrationEnabled = enable))
            }
        },
        description = when {
            accessGranted && settings.integrationEnabled -> {
                "Active — showing ${if (settings.showAppIcons) "app icons" else "nothing yet"} " +
                    "for your notifications."
            }
            accessGranted -> {
                "Off. Turn on to show app icons when notifications arrive."
            }
            else -> {
                "Tap to open Android's notification-access screen and allow " +
                    "CHRONOS — icons appear at the bottom centre automatically."
            }
        },
    )

    if (settings.integrationEnabled && accessGranted) {
        SwitchRow(
            title = "Show app icons",
            checked = settings.showAppIcons,
            onCheckedChange = { onChange(settings.copy(showAppIcons = it)) },
        )

        SwitchRow(
            title = "Show unread indicators",
            checked = settings.showIndicators,
            onCheckedChange = { onChange(settings.copy(showIndicators = it)) },
            description = "Accent dot plus a count badge on each app icon.",
        )

        OptionRow(
            title = "Privacy",
            options = NotificationPrivacy.entries,
            selected = settings.privacy,
            optionLabel = { it.label },
            onSelect = { onChange(settings.copy(privacy = it)) },
            description = "Titles are only ever rendered inside CHRONOS, never " +
                "persisted and never sent anywhere.",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenAppPicker)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Choose apps",
                    style = TextStyle(fontSize = 15.sp),
                    color = palette.textPrimary,
                )
                Text(
                    text = "Pick which apps appear on the clock",
                    style = TextStyle(fontSize = 11.sp),
                    color = palette.textFaint,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = if (settings.allowedPackages.isEmpty()) {
                    "All apps"
                } else {
                    "${settings.allowedPackages.size} selected"
                },
                style = TextStyle(fontSize = 13.sp),
                color = palette.textSecondary,
            )
            Text(
                text = " ›",
                style = TextStyle(fontSize = 15.sp),
                color = palette.textFaint,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        SwitchRow(
            title = "Quick reply",
            checked = settings.quickReplyEnabled,
            onCheckedChange = { onChange(settings.copy(quickReplyEnabled = it)) },
            description = "Long-press an icon to reply through the source app's " +
                "own Android RemoteInput (WhatsApp, Telegram, etc.) — the same " +
                "dialog also offers Dismiss. Falls back to a plain dismiss when " +
                "the notification has no reply action.",
        )

        SwitchRow(
            title = "Prefer bubbles when available",
            checked = settings.bubblesEnabled,
            onCheckedChange = { onChange(settings.copy(bubblesEnabled = it)) },
            description = "When an app cannot be launched normally, open its " +
                "notification's own bubble/content handle instead. Whether a " +
                "floating window appears is decided by Android and the source " +
                "app — see About → limitations.",
        )

        // ——— Settings → Notifications → Floating App Windows ———
        SectionHeader("Floating App Windows")

        SwitchRow(
            title = "Enable floating-window launch",
            checked = settings.floatingWindowsEnabled,
            onCheckedChange = { onChange(settings.copy(floatingWindowsEnabled = it)) },
            description = "Open a tapped app in a real Android floating " +
                "(freeform) window on top of the clock, with the system's own " +
                "window controls. Availability is decided by your device and " +
                "the target app — CHRONOS never forces it.",
        )
        SwitchRow(
            title = "Prefer floating windows when supported",
            checked = settings.preferFloatingWindows,
            onCheckedChange = { onChange(settings.copy(preferFloatingWindows = it)) },
            description = "On: try the floating window first, then split " +
                "screen. Off: split screen first, floating only if split is " +
                "refused.",
        )
        SwitchRow(
            title = "Allow normal full-screen fallback",
            checked = settings.allowFullScreenFallback,
            onCheckedChange = { onChange(settings.copy(allowFullScreenFallback = it)) },
            description = "If neither floating nor split screen works, open " +
                "the app the normal full-screen way. Off: never launch " +
                "full-screen — only the source app's official notification " +
                "bubble may still open.",
        )
        SwitchRow(
            title = "Show instructions for unsupported devices",
            checked = settings.showFloatingInstructions,
            onCheckedChange = { onChange(settings.copy(showFloatingInstructions = it)) },
            description = "Explain after a tap when your device or an app " +
                "blocks floating windows, what opened instead, and where " +
                "floating windows can be enabled on devices that offer them.",
        )
    }

    NoteText(
        "CHRONOS keeps active notifications in memory only. They are cleared " +
            "when the process exits, and nothing is written to disk.",
    )
}

/** Lightweight package descriptor for the app picker. */
data class AppEntry(val packageName: String, val label: String, val selected: Boolean)

/** Row used by the app picker screen — a real on/off switch per app. */
@Composable
fun AppPickerRow(entry: AppEntry, onToggle: () -> Unit) {
    val palette = Chronos.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.label,
                color = if (entry.selected) palette.textPrimary else palette.textSecondary,
                style = TextStyle(fontSize = 15.sp),
            )
            Text(
                text = entry.packageName,
                color = palette.textFaint,
                style = TextStyle(fontSize = 11.sp),
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = entry.selected,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = palette.accent,
                checkedThumbColor = if (palette.isLight) palette.background else palette.textPrimary,
                uncheckedTrackColor = palette.surface,
                uncheckedThumbColor = palette.textFaint,
                uncheckedBorderColor = palette.surface,
            ),
        )
    }
}
