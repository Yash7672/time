package com.chronos.clock.presentation.home

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.chronos.clock.core.constants.AppConstants
import com.chronos.clock.core.permissions.PermissionHelper
import com.chronos.clock.notifications.NotificationListener
import com.chronos.clock.presentation.background.StarFieldCanvas
import com.chronos.clock.presentation.theme.Chronos
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Main clock screen.
 *
 * Layout contract (portrait):
 *   centre   → the clock (time), then the date beneath it — time first,
 *              date second, never overlapping
 *   bottom   → notification app icons (tap = open that app beside CHRONOS
 *              in multi-window, long-press = reply sheet or dismiss), a
 *              faint "listening" line while access exists but nothing has
 *              arrived, or a tappable "allow notification access" prompt
 *              while the one required permission is missing
 *   bottom-L → settings gear (tap-to-reveal, auto-hide)
 *
 * Landscape mirrors the same order with tighter proportions.
 *
 * AMOLED: background is the theme's (usually pure black) colour, drawn once;
 * everything else is text and tiny icons.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Chronos.palette
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val groups by viewModel.notificationGroups.collectAsStateWithLifecycle()
    val accessGranted by viewModel.notificationAccess.collectAsStateWithLifecycle()
    val floatingNotice by viewModel.floatingNotice.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ---- notification access: keep the grant state fresh ------------------
    // While the screen is visible, re-check every 3 s so a grant made on the
    // system screen (or a later revocation) is reflected without reopening
    // Settings. refreshNotificationAccess also flips the feature on the first
    // time a grant is seen, so icons appear as soon as access exists.
    // Additionally, whenever we don't yet have a live grant in memory (the
    // moment access appears, and every app resume), the listener is force
    // re-bound: OEM builds often drop or delay that binding while CHRONOS is
    // closed, and a rebind backfills everything currently on screen — so
    // icons show even for notifications that arrived while the app was shut.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastGranted: Boolean? = null
            while (true) {
                viewModel.refreshNotificationAccess()
                val granted = viewModel.notificationAccess.value
                if (granted && lastGranted != true) {
                    NotificationListener.forceRebind(context)
                }
                lastGranted = granted
                delay(3_000)
            }
        }
    }

    // ---- guided grant: Android's consent screen on every launch -----------
    // Android never lets an app grant itself notification access — the
    // closest legal equivalent to "on by default" is opening Android's own
    // consent screen on every launch until the grant exists (one switch
    // flip). Once granted this never fires again; if declined, the
    // bottom-centre hint remains as the manual path.
    LaunchedEffect(Unit) {
        if (!PermissionHelper.hasNotificationAccess(context)) {
            delay(700) // let the clock render first — no abrupt jump
            openNotificationAccess(context)
        }
    }

    // ---- inactivity tracking: pauses background effects -------------------
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var uiActive by remember { mutableStateOf(true) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val idle = System.currentTimeMillis() - lastInteractionMs
                val shouldPause = settings.performance.pauseWhenInactive &&
                    idle > AppConstants.INACTIVITY_PAUSE_MS
                if (uiActive == shouldPause) {
                    uiActive = !shouldPause
                }
                delay(5_000)
            }
        }
    }

    // ---- tap-to-reveal settings gear -------------------------------------
    var gearVisible by remember { mutableStateOf(false) }
    var gearVersion by remember { mutableLongStateOf(0L) }
    LaunchedEffect(gearVersion, gearVisible) {
        if (gearVisible) {
            delay(3_000)
            gearVisible = false
        }
    }

    // ---- anti burn-in drift ----------------------------------------------
    // A very small periodic offset (±2 dp at most) shifted on a long cycle.
    // Readability and accuracy are unaffected: the digits never move relative
    // to each other, only the whole block shifts by a couple of pixels.
    // The step itself is instant (1–2 px is imperceptible) — no animation
    // machinery needed, which keeps the recomposition cost at zero.
    val driftSteps = remember {
        listOf(
            Offset(0f, 0f),
            Offset(2f, -1f),
            Offset(-2f, 1f),
            Offset(1f, 2f),
            Offset(-1f, -2f),
        )
    }
    var driftIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(
        settings.theme.burnInProtection,
        settings.theme.minimalClockMode,
        settings.performance.reduceAnimations,
    ) {
        if (!settings.theme.burnInProtection ||
            settings.theme.minimalClockMode ||
            settings.performance.reduceAnimations
        ) {
            driftIndex = 0
        } else {
            // Shift every 10 minutes by a deterministic small amount.
            while (true) {
                delay(600_000)
                driftIndex = (driftIndex + 1) % driftSteps.size
            }
        }
    }

    val drift = driftSteps[driftIndex]

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        lastInteractionMs = System.currentTimeMillis()
                        uiActive = true
                        gearVersion++
                        gearVisible = true
                    },
                )
            },
    ) {
        // ---- animated space background -----------------------------------
        StarFieldCanvas(
            starSettings = settings.stars,
            performance = settings.performance,
            amoledMode = settings.theme.amoledMode,
            theme = settings.theme.theme,
            active = uiActive,
            modifier = Modifier.fillMaxSize(),
        )

        if (isLandscape) {
            LandscapeContent(
                now = now,
                settings = settings,
                groups = groups,
                accessGranted = accessGranted,
                drift = drift,
                viewModel = viewModel,
                onOpenSettings = onOpenSettings,
                gearVisible = gearVisible,
            )
        } else {
            PortraitContent(
                now = now,
                settings = settings,
                groups = groups,
                accessGranted = accessGranted,
                drift = drift,
                viewModel = viewModel,
                onOpenSettings = onOpenSettings,
                gearVisible = gearVisible,
            )
        }
    }

    // Explain a floating-window launch that could not engage (device or
    // app limitation), or a tap that opened nothing — or, on vivo/iQOO
    // phones, show the guided native Small Window walkthrough (nothing
    // launches until the user taps "Open app"). Never blocks the clock;
    // dismissible; only shown when the settings ask for it.
    floatingNotice?.let { notice ->
        FloatingWindowNoticeDialog(
            notice = notice,
            onDismiss = viewModel::dismissFloatingNotice,
            onOpenApp = viewModel::confirmSmallWindowGuide,
        )
    }
}

@Composable
private fun PortraitContent(
    now: Long,
    settings: com.chronos.clock.domain.model.AppSettings,
    groups: List<com.chronos.clock.domain.model.NotificationGroup>,
    accessGranted: Boolean,
    drift: Offset,
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    gearVisible: Boolean,
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // CENTRE: the time, then the date beneath it (time first,
            // date second).
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    translationX = drift.x * density
                    translationY = drift.y * density
                },
            ) {
                ClockView(
                    time = com.chronos.clock.core.utils.TimeUtils.formatClock(
                        epochMillis = now,
                        use24Hour = settings.clock.use24Hour,
                        showSeconds = settings.clock.showSeconds,
                    ),
                    settings = settings.clock,
                    minimal = settings.theme.minimalClockMode,
                    reduceAnimations = settings.performance.reduceAnimations ||
                        settings.performance.batterySaver,
                    clockColor = settings.theme.clockColor.argb?.let { Color(it) },
                )

                if (settings.clock.showDate || settings.clock.showDay) {
                    DateView(
                        nowMillis = now,
                        settings = settings.clock,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // BOTTOM: notification app icons — tap opens the app beside
            // CHRONOS in multi-window; long-press replies (or dismisses).
            // While access is missing, a bottom-centre prompt offers the
            // grant path instead.
            if (accessGranted) {
                NotificationIconsRow(
                    groups = groups,
                    settings = settings.notifications,
                    loadIcon = { viewModel.loadIcon(it)?.asImageBitmap() },
                    onOpen = { viewModel.openAppMultiWindow(it) },
                    onDismiss = { viewModel.dismissNotification(it) },
                    onReply = { group, text -> viewModel.replyToNotification(group, text) },
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                // Access exists but the lane is empty: explain why and
                // offer a one-tap fix instead of silently showing nothing.
                NotificationBottomStatus(
                    settings = settings,
                    groups = groups,
                    viewModel = viewModel,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            } else {
                NotificationAccessHint(
                    onClick = { openNotificationAccess(context) },
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            Spacer(Modifier.height(56.dp)) // reserved lane for the gear
        }

        // BOTTOM-LEFT: settings gear (hidden until tapped)
        SettingsButton(
            visible = gearVisible,
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun LandscapeContent(
    now: Long,
    settings: com.chronos.clock.domain.model.AppSettings,
    groups: List<com.chronos.clock.domain.model.NotificationGroup>,
    accessGranted: Boolean,
    drift: Offset,
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    gearVisible: Boolean,
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // CENTRE: the time, then the date beneath it; the whole block
            // drifts together for burn-in protection.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    translationX = drift.x * density
                    translationY = drift.y * density
                },
            ) {
                ClockView(
                    time = com.chronos.clock.core.utils.TimeUtils.formatClock(
                        epochMillis = now,
                        use24Hour = settings.clock.use24Hour,
                        showSeconds = settings.clock.showSeconds,
                    ),
                    settings = settings.clock,
                    minimal = settings.theme.minimalClockMode,
                    reduceAnimations = settings.performance.reduceAnimations ||
                        settings.performance.batterySaver,
                    clockColor = settings.theme.clockColor.argb?.let { Color(it) },
                )

                if (settings.clock.showDate || settings.clock.showDay) {
                    DateView(
                        nowMillis = now,
                        settings = settings.clock,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // BOTTOM of the screen: notification app icons — never tucked
            // under the date. While access is missing, the grant prompt
            // sits in the same bottom lane instead.
            if (accessGranted) {
                NotificationIconsRow(
                    groups = groups,
                    settings = settings.notifications,
                    loadIcon = { viewModel.loadIcon(it)?.asImageBitmap() },
                    onOpen = { viewModel.openAppMultiWindow(it) },
                    onDismiss = { viewModel.dismissNotification(it) },
                    onReply = { group, text -> viewModel.replyToNotification(group, text) },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                NotificationBottomStatus(
                    settings = settings,
                    groups = groups,
                    viewModel = viewModel,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            } else {
                NotificationAccessHint(
                    onClick = { openNotificationAccess(context) },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Spacer(Modifier.height(56.dp)) // reserved lane for the gear
        }

        SettingsButton(
            visible = gearVisible,
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding(),
        )
    }
}

/**
 * Bottom status lane shown under the icon row whenever no icons render:
 * explains *why* the lane is empty (icons switched off / limited to chosen
 * apps / still waiting) and offers a one-tap fix. An empty bottom lane with
 * no explanation is indistinguishable from a broken feature — this line
 * removes that ambiguity.
 */
@Composable
private fun NotificationBottomStatus(
    settings: com.chronos.clock.domain.model.AppSettings,
    groups: List<com.chronos.clock.domain.model.NotificationGroup>,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    if (groups.isNotEmpty()) return // icons are rendering — no status needed
    NotificationStatusLine(
        settings = settings.notifications,
        groupCount = groups.size,
        onEnableIcons = {
            viewModel.updateSettings { s ->
                s.copy(
                    notifications = s.notifications.copy(
                        integrationEnabled = true,
                        showAppIcons = true,
                    ),
                )
            }
        },
        onShowAllApps = {
            viewModel.updateSettings { s ->
                s.copy(notifications = s.notifications.copy(allowedPackages = emptySet()))
            }
        },
        modifier = modifier,
    )
}

/**
 * Opens Android's own notification-access consent screen. Some OEM skins
 * lack that activity, in which case the app's system settings page is the
 * closest reachable fallback — access can only ever be granted by the user
 * on a system screen; CHRONOS never grants it to itself.
 */
private fun openNotificationAccess(context: android.content.Context) {
    runCatching {
        context.startActivity(PermissionHelper.notificationAccessIntent())
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
    }
}
