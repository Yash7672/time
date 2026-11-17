package com.chronos.clock.data.repository

import android.content.Context
import com.chronos.clock.data.preferences.SettingsDataStore
import com.chronos.clock.domain.model.AppSettings
import com.chronos.clock.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * SettingsRepository backed by DataStore Preferences.
 *
 * [update] reads the current snapshot, applies [transform], then persists only
 * the keys that actually changed — cheap and race-free for UI-driven updates.
 *
 * DI note: CHRONOS uses a tiny manual service locator
 * ([com.chronos.clock.ChronosApp.container]) instead of Hilt — the app has
 * exactly three singletons and Hilt would add a compiler plugin, codegen and
 * build time for no benefit here.
 */
class SettingsRepositoryImpl(context: Context) : SettingsRepository {

    private val dataStore = SettingsDataStore(context.applicationContext)

    override val settings: Flow<AppSettings> = dataStore.settings

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = dataStore.settings.first()
        val next = transform(current)
        if (current == next) return
        dataStore.edit { prefs ->
            SettingsDataStore.writeTransform(current, next, prefs)
        }
    }
}
