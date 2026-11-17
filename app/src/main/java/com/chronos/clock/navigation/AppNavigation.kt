package com.chronos.clock.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chronos.clock.presentation.home.HomeScreen
import com.chronos.clock.presentation.home.HomeViewModel
import com.chronos.clock.presentation.settings.SettingsScreen

/** Two destinations: the clock and settings. State survives rotation. */
enum class Destination { Home, Settings }

/**
 * Minimal navigation: state-driven with a quick cross-fade. No navigation
 * library is needed for a two-screen app (fewer deps, smaller APK, faster
 * build — see README).
 */
@Composable
fun AppNavigation(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Home.name) }

    // Settings is above Home in the back stack.
    BackHandler(enabled = destination == Destination.Settings.name) {
        destination = Destination.Home.name
    }

    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            fadeIn(tween(160)) togetherWith fadeOut(tween(160))
        },
        modifier = modifier,
        label = "navigation",
    ) { target ->
        when (target) {
            Destination.Home.name -> HomeScreen(
                viewModel = viewModel,
                onOpenSettings = { destination = Destination.Settings.name },
            )
            else -> SettingsScreen(
                viewModel = viewModel,
                onBack = { destination = Destination.Home.name },
            )
        }
    }
}
