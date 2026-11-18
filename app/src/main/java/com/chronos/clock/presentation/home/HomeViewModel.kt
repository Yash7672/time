package com.chronos.clock.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chronos.clock.ChronosApp
import com.chronos.clock.core.lifecycle.ClockTicker
import com.chronos.clock.domain.model.AppSettings
import com.chronos.clock.domain.model.NotificationGroup
import com.chronos.clock.domain.repository.SettingsRepository
import com.chronos.clock.notifications.NotificationManager
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single source of truth for the home screen.
 *
 * State is exposed as StateFlows so the UI can collect lifecycle-aware
 * (`collectAsStateWithLifecycle`) with zero work while stopped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val settingsRepository: SettingsRepository,
    private val notifications: NotificationManager,
    private val ticker: ClockTicker,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /**
     * Wall-clock time, aligned to second (or minute) boundaries by
     * [ClockTicker]. `WhileSubscribed` means zero work while the UI is gone;
     * the first emission after resume is always the current time, so the
     * clock is correct immediately after returning from background, after a
     * time-zone change, or after a manual system clock change.
     */
    private val showSeconds: Flow<Boolean> = settings
        .map { it.clock.showSeconds }
        .distinctUntilChanged()

    val now: StateFlow<Long> = showSeconds
        .flatMapLatest { seconds -> ticker.ticks(seconds) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    /** Notification groups for the icon ring (empty until access is granted). */
    val notificationGroups: StateFlow<List<NotificationGroup>> = notifications.groups

    /** Reflects listener access; refreshed by [refreshNotificationAccess]. */
    private val _notificationAccess = MutableStateFlow(notifications.hasAccess())
    val notificationAccess: StateFlow<Boolean> = _notificationAccess

    fun refreshNotificationAccess() {
        _notificationAccess.value = notifications.hasAccess()
    }

    /**
     * Ambient light (lux) from the activity's light sensor, EMA-smoothed and
     * throttled to perceptual changes. `null` = no reading yet (sensor
     * missing, app in background, or auto brightness just toggled); the UI
     * then falls back to the manual/system window brightness.
     */
    private val _ambientLux = MutableStateFlow<Float?>(null)
    val ambientLux: StateFlow<Float?> = _ambientLux

    private var smoothedLux: Float = -1f

    /** Called from MainActivity's sensor listener (main thread). */
    fun onAmbientLux(rawLux: Float) {
        val smoothed = if (smoothedLux < 0f) {
            rawLux
        } else {
            smoothedLux * 0.8f + rawLux * 0.2f
        }
        smoothedLux = smoothed
        val last = _ambientLux.value
        // Propagate only perceptual jumps (>=15 lux or >=12%) so a hand
        // passing over the sensor never flickers the window brightness.
        if (last == null || abs(smoothed - last) >= maxOf(15f, last * 0.12f)) {
            _ambientLux.value = smoothed
        }
    }

    /** Drops the current reading (sensor unregistered or setting off). */
    fun resetAmbientLux() {
        smoothedLux = -1f
        _ambientLux.value = null
    }

    /** Re-emits every 2 s while observed; Settings uses it to auto-detect a grant. */
    val notificationAccessTicker: Flow<Boolean> = flow {
        while (true) {
            emit(notifications.hasAccess())
            delay(2_000)
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            // Keep notification projections in sync with the new preferences.
            notifications.updateSettings(settings.value.notifications)
        }
    }

    fun openNotification(group: NotificationGroup) {
        notifications.open(group.latest, settings.value.notifications.bubblesEnabled)
    }

    /** Opens the app in a multi-window (split-screen) window beside CHRONOS. */
    fun openAppMultiWindow(group: NotificationGroup) {
        notifications.openInMultiWindow(group.latest)
    }

    fun dismissNotification(group: NotificationGroup) {
        notifications.dismiss(group.latest)
    }

    fun replyToNotification(group: NotificationGroup, text: String): Boolean =
        notifications.reply(group.latest, text)

    suspend fun loadIcon(packageName: String): android.graphics.Bitmap? =
        notifications.loadIcon(packageName)

    companion object {
        fun factory(app: ChronosApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(
                    settingsRepository = app.container.settings,
                    notifications = app.container.notifications,
                    ticker = app.container.clockTicker,
                ) as T
            }
    }
}
