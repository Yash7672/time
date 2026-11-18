package com.chronos.clock

import android.app.Application
import com.chronos.clock.data.repository.SettingsRepositoryImpl
import com.chronos.clock.notifications.NotificationManager
import com.chronos.clock.notifications.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide service locator.
 *
 * CHRONOS deliberately uses a ~30 line manual container instead of Hilt: the
 * object graph is three singletons, and skipping a DI compiler plugin keeps
 * cold start and build time lower for a personal-use app.
 */
class ChronosApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: ChronosApp) {
    val settings = SettingsRepositoryImpl(context)

    /** Single shared in-memory notification store (never persisted). */
    val notificationStore = NotificationRepository(context)

    /** UI-facing facade over the shared store. */
    val notifications = NotificationManager(context, notificationStore)

    /** System-aligned, lifecycle-aware clock ticker. */
    val clockTicker = com.chronos.clock.core.lifecycle.ClockTicker()

    init {
        // Single source of truth: mirror the persisted notification
        // preferences into the in-memory store from the very first DataStore
        // emission. Without this the store keeps its own defaults
        // (integration OFF) after every process restart, so the icon row
        // stayed empty even with notification access granted.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.settings.collect { s ->
                notificationStore.updateSettings(s.notifications)
            }
        }
    }
}
