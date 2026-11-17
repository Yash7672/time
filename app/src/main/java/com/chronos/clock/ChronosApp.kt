package com.chronos.clock

import android.app.Application
import com.chronos.clock.core.permissions.PermissionHelper
import com.chronos.clock.data.repository.SettingsRepositoryImpl
import com.chronos.clock.notifications.NotificationManager
import com.chronos.clock.notifications.NotificationRepository

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
        PermissionHelper.bindPackageName(packageName)
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
}
