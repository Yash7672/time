package com.chronos.clock.presentation.home

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.chronos.clock.core.constants.AppConstants
import com.chronos.clock.domain.model.AmoledMode
import com.chronos.clock.domain.model.NotificationPrivacy
import com.chronos.clock.presentation.background.StarFieldCanvas
import com.chronos.clock.presentation.theme.Chronos
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Main clock screen.
 *
 * Layout contract (portrait):
 *   centre   → notification icons, then the clock (time), then the date
 *              beneath it — time first, date second, never overlapping
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

    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
                drift = drift,
                viewModel = viewModel,
                onOpenSettings = onOpenSettings,
                gearVisible = gearVisible,
            )
        }
    }
}

@Composable
private fun PortraitContent(
    now: Long,
    settings: com.chronos.clock.domain.model.AppSettings,
    groups: List<com.chronos.clock.domain.model.NotificationGroup>,
    drift: Offset,
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    gearVisible: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // CENTRE: icons above the clock, then the time, then the date
            // beneath it (time first, date second).
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    translationX = drift.x * density
                    translationY = drift.y * density
                },
            ) {
                NotificationIconsRow(
                    groups = groups,
                    settings = settings.notifications,
                    loadIcon = { viewModel.loadIcon(it)?.asImageBitmap() },
                    onOpen = { viewModel.openNotification(it) },
                    onDismiss = { viewModel.dismissNotification(it) },
                    modifier = Modifier.padding(bottom = 20.dp),
                )

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
    drift: Offset,
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    gearVisible: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Centre: the time, with the date beneath it.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = drift.x * density
                        translationY = drift.y * density
                    },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NotificationIconsRow(
                        groups = groups,
                        settings = settings.notifications,
                        loadIcon = { viewModel.loadIcon(it)?.asImageBitmap() },
                        onOpen = { viewModel.openNotification(it) },
                        onDismiss = { viewModel.dismissNotification(it) },
                        modifier = Modifier.padding(bottom = 14.dp),
                    )

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
                    )

                    if (settings.clock.showDate || settings.clock.showDay) {
                        DateView(
                            nowMillis = now,
                            settings = settings.clock,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
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
