package com.chronos.clock.domain.repository

import com.chronos.clock.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/** Read/write access to persisted app settings (DataStore-backed). */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** Atomically transform the current settings and persist the result. */
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
