package com.chronos.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronos.clock.navigation.AppNavigation
import com.chronos.clock.presentation.home.HomeViewModel
import com.chronos.clock.presentation.theme.ChronosTheme
import kotlin.math.log10

/**
 * Single activity.
 *
 * Responsibilities kept here (and nowhere else):
 *  - applying display flags (keep-awake, immersive, window brightness)
 *    from settings, lifecycle-aware,
 *  - auto-rotation: the activity follows the sensor while it is open and
 *    never writes the system rotation setting, so the home screen and
 *    other apps keep the user's own choice,
 *  - ambient-light sensing for auto brightness: registered only while the
 *    activity is started and only while the setting is on,
 *  - low-battery screen release: at the chosen charge threshold (default
 *    30 %, Settings → Display) the keep-awake flag is handed back to the
 *    system so the display sleeps by the normal screen-timeout and the
 *    clock can never run the battery to shutdown,
 *  - hosting the Compose tree.
 *
 * No service is started for the clock; nothing runs while this activity is
 * not visible.
 */
class MainActivity : ComponentActivity() {

    private var viewModel: HomeViewModel? = null

    private var sensorManager: SensorManager? = null
    private var lightRegistered = false
    private val lightListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            viewModel?.onAmbientLux(event.values[0])
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Policy: release the screen at/below this charge (settings-driven). */
    private var releaseAtLowBattery = true
    private var lowBatteryThreshold = 30

    /** True once charge is at/below the threshold and release is enabled. */
    private var lowBattery = false

    /** Last charge percent seen (null until the first battery broadcast). */
    private var lastBatteryPercent: Int? = null

    /** The keep-awake state the settings ask for (battery can override). */
    private var keepAwakeWanted = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryState(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Rotate with the sensor while this activity is open. The manifest
        // already declares screenOrientation="sensor"; setting it here too
        // makes the intent explicit and covers any later reset.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR

        val vm = ViewModelProvider(
            this,
            HomeViewModel.factory(application as ChronosApp),
        )[HomeViewModel::class.java]
        viewModel = vm

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val ambientLux by vm.ambientLux.collectAsStateWithLifecycle()

            val autoBrightness = settings.display.autoBrightness
            // Effective window brightness: the light sensor wins while auto
            // brightness is on and a reading exists; otherwise the manual
            // value (-1f = follow the system slider).
            val windowBrightness = if (autoBrightness && ambientLux != null) {
                autoWindowBrightness(ambientLux!!)
            } else {
                settings.theme.windowBrightness
            }

            // Apply display flags whenever the relevant settings change.
            LaunchedEffect(
                settings.display.keepScreenAwake,
                settings.display.fullscreen,
                windowBrightness,
            ) {
                applyDisplayFlags(
                    keepAwake = settings.display.keepScreenAwake,
                    fullscreen = settings.display.fullscreen,
                    windowBrightness = windowBrightness,
                )
            }

            // Keep the light sensor in step with the setting while visible.
            LaunchedEffect(autoBrightness) { syncLightSensor(autoBrightness) }

            // Keep the low-battery policy in step with its switch/threshold.
            LaunchedEffect(
                settings.display.lowBatteryRelease,
                settings.display.lowBatteryThreshold,
            ) {
                updateBatteryPolicy(
                    release = settings.display.lowBatteryRelease,
                    threshold = settings.display.lowBatteryThreshold,
                )
            }

            ChronosTheme(theme = settings.theme.theme) {
                AppNavigation(viewModel = vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as? ChronosApp)?.uiVisible = true
        startBatteryWatch()
        syncLightSensor(viewModel?.settings?.value?.display?.autoBrightness ?: true)
    }

    override fun onStop() {
        // Sensor and battery watch never run while CHRONOS is not visible.
        unregisterLightSensor()
        stopBatteryWatch()
        (application as? ChronosApp)?.uiVisible = false
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterLightSensor()
        // Never leave a keep-screen-on flag behind.
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun syncLightSensor(enabled: Boolean) {
        if (enabled) {
            registerLightSensor()
        } else {
            unregisterLightSensor()
            viewModel?.resetAmbientLux()
        }
    }

    private fun registerLightSensor() {
        if (lightRegistered) return
        val sm = sensorManager
            ?: getSystemService(SensorManager::class.java).also { sensorManager = it }
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return
        lightRegistered = sm.registerListener(
            lightListener,
            sensor,
            // 2 Hz instead of the 5 Hz default: ambient light changes over
            // seconds, so this halves the sensor callbacks with no visible
            // difference in auto-brightness response.
            500_000,
        )
    }

    private fun unregisterLightSensor() {
        if (!lightRegistered) return
        sensorManager?.unregisterListener(lightListener)
        lightRegistered = false
    }

    // ——— low-battery screen release ————————————————————————————————

    /**
     * Keep-screen-awake with the low-battery override: while
     * [releaseAtLowBattery] is on and charge sits at [lowBatteryThreshold]
     * percent or less, the flag is released even when the keep-awake
     * setting asks for it, so the display sleeps by the normal system
     * screen-timeout instead of lighting the phone down to a shutdown.
     * Above the threshold (or with the policy off) the setting applies
     * unchanged — charging back up restores it automatically.
     *
     * CHRONOS never forces the screen off directly (that needs a system
     * permission no normal app can hold) — it only stops holding the
     * screen awake, which is precisely what the keep-awake flag does.
     */
    private fun applyKeepAwake() {
        if (keepAwakeWanted && !lowBattery) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * ACTION_BATTERY_CHANGED is sticky: registration delivers the current
     * level immediately, and afterwards one broadcast per battery tick —
     * zero polling, negligible cost, active only while CHRONOS is visible.
     */
    private fun startBatteryWatch() {
        val sticky = runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
            )
        }.getOrNull()
        updateBatteryState(sticky)
    }

    private fun stopBatteryWatch() {
        runCatching { unregisterReceiver(batteryReceiver) }
    }

    private fun updateBatteryState(intent: Intent?) {
        val level = intent?.let { batteryPercent(it) } ?: return
        lastBatteryPercent = level
        recomputeLowBattery()
    }

    /** Applies the policy (switch + threshold + current charge) to the flag. */
    private fun recomputeLowBattery() {
        val level = lastBatteryPercent ?: return
        val nowLow = releaseAtLowBattery && level <= lowBatteryThreshold
        if (nowLow == lowBattery) return
        lowBattery = nowLow
        applyKeepAwake()
    }

    /** Called from composition whenever the policy settings change. */
    private fun updateBatteryPolicy(release: Boolean, threshold: Int) {
        releaseAtLowBattery = release
        lowBatteryThreshold = threshold.coerceIn(5, 50)
        recomputeLowBattery()
    }

    private fun batteryPercent(intent: Intent): Int? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100) / scale
    }

    private fun applyDisplayFlags(
        keepAwake: Boolean,
        fullscreen: Boolean,
        windowBrightness: Float,
    ) {
        keepAwakeWanted = keepAwake
        applyKeepAwake()

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // WindowInsetsCompat.Type works on every API level (minSdk 26), unlike
        // the framework WindowInsets.Type which requires API 30.
        val systemBars = androidx.core.view.WindowInsetsCompat.Type.systemBars()
        if (fullscreen) {
            controller.hide(systemBars)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(systemBars)
        }

        // Window-level brightness only; system brightness is never touched.
        // -1f = follow the system value.
        val params = window.attributes
        params.screenBrightness = windowBrightness
        window.attributes = params
    }

    /**
     * Maps ambient light (lux) to this window's brightness (0..1) on a
     * perceptual log curve: a dark room (~5 lux) lands near 16%, a lit room
     * (~300 lux) near 71%, and bright daylight saturates at 100%. Applied
     * only to the CHRONOS window — the system slider is never written.
     */
    private fun autoWindowBrightness(lux: Float): Float {
        val t = (log10(1f + lux.coerceAtLeast(0f)) / 3f).coerceIn(0f, 1f)
        return (0.10f + 0.90f * t * t).coerceIn(0.10f, 1f)
    }
}
