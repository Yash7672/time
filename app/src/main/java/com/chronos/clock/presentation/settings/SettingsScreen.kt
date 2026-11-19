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
import com.chronos.clock.presentation.home.SectionDivider
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

    // Poll listener access while this screen is visible so the master switch
    // reflects a grant made on the system screen (user returns here after).
    // The grant-edge auto-enable itself lives in refreshNotificationAccess().
    LaunchedEffect(Unit) {
        viewModel.notificationAccessTicker.collectLatest {
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
            onClearSelection = {
                viewModel.updateSettings { s ->
                    s.copy(notifications = s.notifications.copy(allowedPackages = emptySet()))
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

                SectionDivider()

                AppearanceSettingsSection(settings = settings.theme) { newTheme ->
                    viewModel.updateSettings { it.copy(theme = newTheme) }
                }

                SectionDivider()

                StarBackgroundSettingsSection(
                    settings = settings.stars,
                    batteryMode = settings.performance.batterySaver,
                ) { newStars ->
                    viewModel.updateSettings { it.copy(stars = newStars) }
                }

                SectionDivider()

                NotificationSettingsSection(
                    settings = settings.notifications,
                    accessGranted = accessGranted,
                    onChange = { newNotif ->
                        viewModel.updateSettings { it.copy(notifications = newNotif) }
                    },
                    onOpenAppPicker = { showAppPicker = true },
                )

                SectionDivider()

                DisplaySettingsSection(settings = settings) { newSettings ->
                    viewModel.updateSettings { newSettings }
                }

                SectionDivider()

                PerformanceSettingsSection(settings = settings.performance) { newPerf ->
                    viewModel.updateSettings { it.copy(performance = newPerf) }
                }

                SectionDivider()

                AboutSection(versionName = versionName)

                Box(Modifier.fillMaxWidth().padding(bottom = 40.dp))
            }
        }
    }
}

/** Loads launchable apps for the picker, sorted by visible app name. */
private fun queryLaunchableApps(
    context: android.content.Context,
    selected: Set<String>,
): List<AppEntry> {
    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    return try {
        context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                val label = try {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(pkg, 0),
                    ).toString()
                } catch (_: Exception) {
                    return@mapNotNull null
                }
                pkg to label
            }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
            .map { (pkg, label) ->
                AppEntry(packageName = pkg, label = label, selected = pkg in selected)
            }
    } catch (_: Exception) {
        emptyList()
    }
}
