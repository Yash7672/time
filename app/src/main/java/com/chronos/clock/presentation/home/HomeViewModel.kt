package com.chronos.clock.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chronos.clock.ChronosApp
import com.chronos.clock.core.lifecycle.ClockTicker
import com.chronos.clock.domain.model.AppSettings
import com.chronos.clock.domain.model.NotificationGroup
import com.chronos.clock.domain.repository.SettingsRepository
import com.chronos.clock.notifications.FloatingAppLauncher
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

    /**
     * Grant-edge tracker. Starts `false` so the first refresh of a process
     * counts as an edge: if notification access is already granted (or was
     * just granted), integration is switched on once so the icon row works
     * immediately. After that first edge the user's own choice sticks —
     * turning integration off is never fought while the process lives.
     */
    private var hadNotificationAccess: Boolean = false

    fun refreshNotificationAccess() {
        val granted = notifications.hasAccess()
        _notificationAccess.value = granted
        if (granted && !hadNotificationAccess && !settings.value.notifications.integrationEnabled) {
            updateSettings {
                it.copy(notifications = it.notifications.copy(integrationEnabled = true))
            }
        }
        hadNotificationAccess = granted
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
            // No direct push into the notification store here: AppContainer
            // mirrors every persisted change via its collector. Pushing
            // settings.value directly could race that collector and write a
            // stale snapshot back.
        }
    }

    /**
     * User-facing explanation of a floating-window launch that could not
     * engage (device or app limitation), a tap that opened nothing, or the
     * guided Small Window walkthrough. `null` = hidden. Shown as a
     * dismissible dialog only when Settings → Floating App Windows →
     * "Show instructions" is on (the guided walkthrough itself is that
     * switch's iQOO/vivo behaviour).
     */
    data class FloatingWindowNotice(
        val appLabel: String,
        val outcome: FloatingAppLauncher.Outcome,
        val floatBlockReason: FloatingAppLauncher.FloatBlockReason?,
        /** True = guided Small Window walkthrough; nothing launched yet. */
        val guided: Boolean = false,
    )

    /**
     * Transient explanation for the last icon tap (see [FloatingWindowNotice]);
     * `null` hides it. The clock keeps running behind the dialog.
     */
    private val _floatingNotice = MutableStateFlow<FloatingWindowNotice?>(null)
    val floatingNotice: StateFlow<FloatingWindowNotice?> = _floatingNotice

    /** The group behind the guided walkthrough — consumed by its "Open app". */
    private var pendingGuideGroup: NotificationGroup? = null

    fun dismissFloatingNotice() {
        _floatingNotice.value = null
        pendingGuideGroup = null
    }

    /**
     * One tap on a notification icon: open the app on top of CHRONOS via
     * [FloatingAppLauncher]. On vivo/iQOO phones the tap **automatically
     * attempts the native Small Window** — no dialog, no instructions.
     * The guided walkthrough only appears as a last resort when nothing
     * could be opened at all; elsewhere an honest dialog explains when
     * freeform could not engage (and only when "Show instructions" is on —
     * the launcher decides via [FloatingAppLauncher.LaunchResult.showNotice]).
     */
    fun openAppMultiWindow(group: NotificationGroup) {
        val result = notifications.openInMultiWindow(group.latest)
        if (result.outcome == FloatingAppLauncher.Outcome.SMALL_WINDOW_GUIDE) {
            pendingGuideGroup = group
            _floatingNotice.value = FloatingWindowNotice(
                appLabel = group.appLabel,
                outcome = result.outcome,
                floatBlockReason = null,
                guided = true,
            )
            return
        }
        if (result.showNotice) {
            _floatingNotice.value = FloatingWindowNotice(
                appLabel = group.appLabel,
                outcome = result.outcome,
                floatBlockReason = result.floatBlockReason,
            )
        }
    }

    /**
     * The guided walkthrough's "Open app" button — the user's own explicit
     * step 1: open the app (bubble first when available) so it appears in
     * Recents, where the Small Window gesture finishes the job.
     */
    fun confirmSmallWindowGuide() {
        val group = pendingGuideGroup
        pendingGuideGroup = null
        _floatingNotice.value = null
        if (group != null) {
            notifications.openFromGuide(group.latest)
        }
    }

    fun dismissNotification(group: NotificationGroup) {
        // "Ignore this app": clear every notification it currently has —
        // the icon stands for the whole app, not just its newest line.
        val targets = group.items.ifEmpty { listOf(group.latest) }
        targets.forEach { notifications.dismiss(it) }
    }

    fun replyToNotification(group: NotificationGroup, text: String): Boolean {
        // Reply to the newest notification that actually offers RemoteInput:
        // the newest overall may be a second, action-less notification
        // (WhatsApp often keeps one), which would make the reply a no-op.
        val target = group.items.firstOrNull { it.canReply } ?: group.latest
        val sent = notifications.reply(target, text)
        if (sent) {
            // The text went out through the app's own handle as a normal
            // message — drop the answered notification so no stale "unseen"
            // line keeps tagging along in CHRONOS. The reply quiet window
            // also swallows the source app's immediate echo/read-receipt
            // re-post, which previously made the icon pop straight back.
            notifications.dismissAfterReply(target)
        }
        return sent
    }

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
