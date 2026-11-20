package com.chronos.clock.presentation.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.chronos.clock.core.constants.AppConstants
import com.chronos.clock.domain.model.AmoledMode
import com.chronos.clock.domain.model.ChronosTheme
import com.chronos.clock.domain.model.PerformanceSettings
import com.chronos.clock.domain.model.StarSettings
import com.chronos.clock.presentation.theme.ChronosColors
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Lightweight AMOLED star-field.
 *
 * Battery / GPU contract:
 *  - particles are precomputed once per setting change (no per-frame allocs),
 *  - the update loop runs only while the app is STARTED (`repeatOnLifecycle`)
 *    and only when the current mode actually animates,
 *  - update rate is throttled (~5 fps normal, ~2 fps battery friendly) —
 *    slow drift does not need 60 fps, so the GPU stays idle,
 *  - the field freezes entirely 30 s after the last touch and whenever
 *    Android's own battery saver is active (observed via its broadcast),
 *  - static modes (PURE_AMOLED, ULTRA_BATTERY, stars off) draw exactly once,
 *  - colours are allocated once per theme, never inside the draw loop.
 */
@Composable
fun StarFieldCanvas(
    starSettings: StarSettings,
    performance: PerformanceSettings,
    amoledMode: AmoledMode,
    theme: ChronosTheme,
    modifier: Modifier = Modifier,
    /** false while the UI is inactive → animation pauses, field freezes. */
    active: Boolean = true,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // Android's own battery saver, observed through its broadcast (zero
    // polling): the moment the system enters power-save mode the field
    // freezes too — the strongest saving, with no switch to remember.
    var systemSaver by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val pm = context.getSystemService(PowerManager::class.java)
        systemSaver = pm?.isPowerSaveMode == true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                systemSaver = pm?.isPowerSaveMode == true
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    val maxParticles = when {
        performance.lightweightRender -> 40
        performance.batteryFriendly || performance.batterySaver -> 60
        else -> AppConstants.STARS_MAX
    }

    // ULTRA_BATTERY and PURE_AMOLED are static by definition; battery saver,
    // reduceAnimations and the system's own power-save mode also collapse
    // motion to a single still frame.
    val shouldAnimate = starSettings.effectivelyAnimated(amoledMode) &&
        active &&
        !systemSaver &&
        !performance.batterySaver &&
        !performance.reduceAnimations &&
        !performance.lightweightRender

    // Deterministic field: same settings → same sky (survives recomposition).
    val density = starSettings.density
    val speed = starSettings.speed
    val twinkleOn = starSettings.twinkling && !performance.batteryFriendly && !performance.batterySaver
    val field = remember(density, speed, twinkleOn, maxParticles, shouldAnimate, amoledMode) {
        StarField.generate(
            density = density,
            speed = speed,
            twinkling = twinkleOn,
            animated = shouldAnimate,
            maxParticles = maxParticles,
        )
    }

    // Dim, theme-aware colours allocated once. White stars are deliberately
    // not #FFFFFF — soft grey-white keeps OLED illumination low.
    val colors = remember(theme) {
        if (theme == ChronosTheme.LIGHT) {
            arrayOf(
                Color(0xFF9AA0AA), // dim grey on light background
                Color(0xFF6F86C9),
                Color(0xFF8B76C4),
            )
        } else {
            arrayOf(
                ChronosColors.StarWhite,
                ChronosColors.StarBlue,
                ChronosColors.StarPurple,
            )
        }
    }

    // Animation clock in ms; 0 when static → fully deterministic single frame.
    var elapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(shouldAnimate, lifecycleOwner, performance.batteryFriendly, performance.batterySaver) {
        if (!shouldAnimate) {
            elapsedMs = 0L
            return@LaunchedEffect
        }
        val interval = if (performance.batteryFriendly || performance.batterySaver) {
            AppConstants.STAR_FRAME_INTERVAL_BATTERY_MS
        } else {
            AppConstants.STAR_FRAME_INTERVAL_NORMAL_MS
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val start = SystemClock.uptimeMillis()
            var next = start + interval
            while (isActive) {
                val now = SystemClock.uptimeMillis()
                val wait = next - now
                if (wait > 0) {
                    delay(wait)
                }
                val frameNow = SystemClock.uptimeMillis()
                next = frameNow + interval
                elapsedMs = frameNow - start
            }
        }
    }

    Canvas(modifier = modifier) {
        drawStars(field, colors, elapsedMs)
    }
}

private fun DrawScope.drawStars(
    field: StarField,
    colors: Array<Color>,
    elapsedMs: Long,
) {
    if (field.count == 0) return
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val t = if (field.animated) elapsedMs / 1000f else 0f

    for (i in 0 until field.count) {
        var x = field.xs[i]
        var y = field.ys[i]
        if (field.animated) {
            // Normalized drift with cheap wrap into [0,1).
            x += field.vxs[i] * t
            y += field.vys[i] * t
            x -= x.toInt().toFloat()
            y -= y.toInt().toFloat()
            if (x < 0f) x += 1f
            if (y < 0f) y += 1f
        }

        var alpha = field.baseAlphas[i]
        if (field.twinkling && field.animated) {
            // Gentle sine twinkle: 0.75× … 1.0× of base alpha.
            val phase = field.phases[i] + field.twinkleSpeeds[i] * t
            alpha *= 0.75f + 0.25f * (0.5f + 0.5f * sin(phase))
        }

        val color = colors[field.colorKinds[i].toInt()]
        drawCircle(
            color = color,
            radius = field.radii[i] * density,
            center = Offset(x * w, y * h),
            alpha = alpha,
        )
    }
}
