package com.chronos.clock.presentation.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronos.clock.presentation.home.HomeViewModel
import com.chronos.clock.presentation.theme.Chronos
import kotlinx.coroutines.flow.collectLatest

/**
 * Dark, minimal settings screen: a single scrolling column, no cards,
 * no gradients — just labelled controls on black.
 */
@Composable
fun SettingsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Chronos.palette
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accessGranted by viewModel.notificationAccess.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAppPicker by remember { mutableStateOf(false) }
    var overlayRequested by remember { mutableStateOf(false) }
    // Whether the floating overlay is actually running right now.
    var overlayEnabled by remember { mutableStateOf(false) }

    // Reflect overlay permission once the user returns from the system screen.
    LaunchedEffect(Unit) {
        while (true) {
            val granted = com.chronos.clock.services.OverlayService.hasOverlayPermission(context)
            if (granted && overlayRequested) {
                overlayEnabled = com.chronos.clock.services.OverlayService.startIfAllowed(context)
                overlayRequested = false
            }
            if (!granted) {
                overlayEnabled = false
                overlayRequested = false
            }
            kotlinx.coroutines.delay(1_500)
        }
    }

    // Poll listener access while this screen is visible so the master switch
    // reflects a grant made on the system screen (user returns here after).
    LaunchedEffect(Unit) {
        viewModel.notificationAccessTicker.collectLatest { granted ->
            if (granted && !settings.notifications.integrationEnabled) {
                viewModel.updateSettings {
                    it.copy(
                        notifications = it.notifications.copy(integrationEnabled = true),
                    )
                }
            }
            viewModel.refreshNotificationAccess()
        }
    }

    val installedApps = remember(settings.notifications.allowedPackages) {
        queryLaunchableApps(context, settings.notifications.allowedPackages)
    }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }

    if (showAppPicker) {
        AppPickerScreen(
            apps = installedApps,
            onBack = { showAppPicker = false },
            onToggle = { pkg ->
                viewModel.updateSettings { s ->
                    val current = s.notifications.allowedPackages
                    val next = if (pkg in current) current - pkg else current + pkg
                    s.copy(notifications = s.notifications.copy(allowedPackages = next))
                }
            },
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = palette.textSecondary,
                    )
                }
                Text(
                    text = "Settings",
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                    color = palette.textPrimary,
                    modifier = Modifier.padding(start = 52.dp),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                ClockSettingsSection(settings = settings.clock) { newClock ->
                    viewModel.updateSettings { it.copy(clock = newClock) }
                }

                AppearanceSettingsSection(settings = settings.theme) { newTheme ->
                    viewModel.updateSettings { it.copy(theme = newTheme) }
                }

                StarBackgroundSettingsSection(
                    settings = settings.stars,
                    batteryMode = settings.performance.batterySaver,
                ) { newStars ->
                    viewModel.updateSettings { it.copy(stars = newStars) }
                }

                NotificationSettingsSection(
                    settings = settings.notifications,
                    accessGranted = accessGranted,
                    installedApps = installedApps.filter { it.selected },
                    onChange = { newNotif ->
                        viewModel.updateSettings { it.copy(notifications = newNotif) }
                    },
                    onOpenAppPicker = { showAppPicker = true },
                )

                DisplaySettingsSection(
                    settings = settings,
                    overlayEnabled = overlayEnabled,
                    onChange = { newSettings ->
                        viewModel.updateSettings { newSettings }
                    },
                    onOverlayChange = { enable ->
                        if (enable) {
                            // Request the special access first; the switch flips
                            // only after the system reports it granted.
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        } else {
                            com.chronos.clock.services.OverlayService.stop(context)
                            overlayEnabled = false
                        }
                        overlayRequested = enable
                    },
                )

                PerformanceSettingsSection(settings = settings.performance) { newPerf ->
                    viewModel.updateSettings { it.copy(performance = newPerf) }
                }

                AboutSection(versionName = versionName)

                Box(Modifier.fillMaxWidth().padding(bottom = 40.dp))
            }
        }
    }
}

/** Loads launchable apps for the picker (label + package, no icons). */
private fun queryLaunchableApps(
    context: android.content.Context,
    selected: Set<String>,
): List<AppEntry> {
    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    return try {
        context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .sortedBy { it.lowercase() }
            .mapNotNull { pkg ->
                val label = try {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(pkg, 0),
                    ).toString()
                } catch (_: Exception) {
                    return@mapNotNull null
                }
                AppEntry(packageName = pkg, label = label, selected = pkg in selected)
            }
    } catch (_: Exception) {
        emptyList()
    }
}
