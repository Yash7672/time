package com.chronos.clock

import android.content.pm.ActivityInfo
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
import com.chronos.clock.domain.model.RotationPreference
import com.chronos.clock.navigation.AppNavigation
import com.chronos.clock.presentation.home.HomeViewModel
import com.chronos.clock.presentation.theme.ChronosTheme

/**
 * Single activity.
 *
 * Responsibilities kept here (and nowhere else):
 *  - applying display flags (keep-awake, immersive, rotation, window
 *    brightness) from settings, lifecycle-aware,
 *  - hosting the Compose tree.
 *
 * No service is started for the clock; nothing runs while this activity is
 * not visible.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val viewModel = ViewModelProvider(
            this,
            HomeViewModel.factory(application as ChronosApp),
        )[HomeViewModel::class.java]

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            // Apply display flags whenever the relevant settings change.
            LaunchedEffect(
                settings.display.keepScreenAwake,
                settings.display.fullscreen,
                settings.display.rotation,
                settings.theme.windowBrightness,
            ) {
                applyDisplayFlags(
                    keepAwake = settings.display.keepScreenAwake,
                    fullscreen = settings.display.fullscreen,
                    rotation = settings.display.rotation,
                    windowBrightness = settings.theme.windowBrightness,
                )
            }

            ChronosTheme(theme = settings.theme.theme) {
                AppNavigation(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reflect notification-access grants/revocations made in system settings.
        val vm = ViewModelProvider(
            this,
            HomeViewModel.factory(application as ChronosApp),
        )[HomeViewModel::class.java]
        vm.refreshNotificationAccess()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Never leave a keep-screen-on flag behind.
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun applyDisplayFlags(
        keepAwake: Boolean,
        fullscreen: Boolean,
        rotation: RotationPreference,
        windowBrightness: Float,
    ) {
        if (keepAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        requestedOrientation = when (rotation) {
            RotationPreference.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            RotationPreference.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            RotationPreference.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

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
}
