package com.chronos.clock.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chronos.clock.domain.model.AmoledMode
import com.chronos.clock.domain.model.AppSettings
import com.chronos.clock.domain.model.ChronosTheme
import com.chronos.clock.domain.model.ClockColor
import com.chronos.clock.domain.model.ClockSettings
import com.chronos.clock.domain.model.DisplaySettings
import com.chronos.clock.domain.model.NotificationPrivacy
import com.chronos.clock.domain.model.NotificationSettings
import com.chronos.clock.domain.model.PerformanceSettings
import com.chronos.clock.domain.model.StarDensity
import com.chronos.clock.domain.model.StarSettings
import com.chronos.clock.domain.model.StarSpeed
import com.chronos.clock.domain.model.ThemeSettings
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chronos_settings")

/**
 * DataStore Preferences persistence for every CHRONOS setting.
 *
 * Each key has a safe default so a fresh install (or an upgrade that adds a
 * key) always restores a valid configuration.
 */
class SettingsDataStore(
    private val context: Context,
    private val use24HourBySystem: Boolean = systemUses24Hour(context),
) {

    private object Keys {
        val USE_24H = booleanPreferencesKey("clock_use_24h")
        val SHOW_SECONDS = booleanPreferencesKey("clock_show_seconds")
        val SHOW_DATE = booleanPreferencesKey("clock_show_date")
        val SHOW_DAY = booleanPreferencesKey("clock_show_day")
        val FONT_SCALE = floatPreferencesKey("clock_font_scale")
        val BRIGHTNESS = floatPreferencesKey("clock_brightness")

        val THEME = stringPreferencesKey("theme_name")
        val AMOLED_MODE = stringPreferencesKey("amoled_mode")
        val BURN_IN = booleanPreferencesKey("burn_in_protection")
        val MINIMAL_CLOCK = booleanPreferencesKey("minimal_clock_mode")
        val WINDOW_BRIGHTNESS = floatPreferencesKey("window_brightness")
        val CLOCK_COLOR = stringPreferencesKey("clock_color")

        val STARS_ENABLED = booleanPreferencesKey("stars_enabled")
        val STARS_DENSITY = stringPreferencesKey("stars_density")
        val STARS_SPEED = stringPreferencesKey("stars_speed")
        val STARS_TWINKLE = booleanPreferencesKey("stars_twinkle")

        val NOTIF_ENABLED = booleanPreferencesKey("notif_integration")
        val NOTIF_PACKAGES = stringSetPreferencesKey("notif_packages")
        val NOTIF_ICONS = booleanPreferencesKey("notif_icons")
        val NOTIF_INDICATORS = booleanPreferencesKey("notif_indicators")
        val NOTIF_PRIVACY = stringPreferencesKey("notif_privacy")
        val NOTIF_QUICK_REPLY = booleanPreferencesKey("notif_quick_reply")
        val NOTIF_BUBBLES = booleanPreferencesKey("notif_bubbles")
        val NOTIF_FLOATING = booleanPreferencesKey("notif_floating_windows")
        val NOTIF_FLOATING_PREFER = booleanPreferencesKey("notif_floating_prefer")
        val NOTIF_FLOATING_FULLSCREEN = booleanPreferencesKey("notif_floating_fullscreen")
        val NOTIF_FLOATING_INSTRUCTIONS = booleanPreferencesKey("notif_floating_instructions")

        val KEEP_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val FULLSCREEN = booleanPreferencesKey("fullscreen_mode")
        val AUTO_BRIGHTNESS = booleanPreferencesKey("auto_brightness")

        val BATTERY_FRIENDLY = booleanPreferencesKey("battery_friendly")
        val REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
        val PAUSE_INACTIVE = booleanPreferencesKey("pause_when_inactive")
        val LIGHTWEIGHT = booleanPreferencesKey("lightweight_render")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver")
    }

    val settings: Flow<AppSettings> =
        context.dataStore.data.map { p -> p.toAppSettings(use24HourBySystem) }

    suspend fun edit(transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { transform(it) }
    }

    companion object {
        /**
         * Defaults are computed once from the device locale so a first launch
         * matches the system 12/24-hour preference.
         */
        fun defaultSettings(use24HourBySystem: Boolean): AppSettings = AppSettings(
            clock = ClockSettings(use24Hour = use24HourBySystem),
            theme = ThemeSettings(),
            stars = StarSettings(),
            notifications = NotificationSettings(),
            display = DisplaySettings(),
            performance = PerformanceSettings(),
        )

        internal fun Preferences.toAppSettings(
            use24HourBySystem: Boolean = true,
        ): AppSettings {
            val defaults = defaultSettings(use24HourBySystem)
            fun bool(key: Preferences.Key<Boolean>, def: Boolean) = this[key] ?: def
            fun float(key: Preferences.Key<Float>, def: Float) = this[key] ?: def
            fun <T : Enum<T>> enum(key: Preferences.Key<String>, values: Array<T>, def: T): T =
                values.firstOrNull { it.name == this[key] } ?: def

            return AppSettings(
                clock = ClockSettings(
                    use24Hour = bool(Keys.USE_24H, defaults.clock.use24Hour),
                    showSeconds = bool(Keys.SHOW_SECONDS, defaults.clock.showSeconds),
                    showDate = bool(Keys.SHOW_DATE, defaults.clock.showDate),
                    showDay = bool(Keys.SHOW_DAY, defaults.clock.showDay),
                    fontScale = float(Keys.FONT_SCALE, defaults.clock.fontScale).coerceIn(0.75f, 1.5f),
                    brightness = float(Keys.BRIGHTNESS, defaults.clock.brightness).coerceIn(0.35f, 1f),
                ),
                theme = ThemeSettings(
                    theme = enum(Keys.THEME, ChronosTheme.entries.toTypedArray(), defaults.theme.theme),
                    amoledMode = enum(
                        Keys.AMOLED_MODE,
                        AmoledMode.entries.toTypedArray(),
                        defaults.theme.amoledMode,
                    ),
                    burnInProtection = bool(Keys.BURN_IN, defaults.theme.burnInProtection),
                    minimalClockMode = bool(Keys.MINIMAL_CLOCK, defaults.theme.minimalClockMode),
                    windowBrightness = float(Keys.WINDOW_BRIGHTNESS, defaults.theme.windowBrightness)
                        .let { if (it in 0.1f..1f) it else -1f },
                    clockColor = enum(
                        Keys.CLOCK_COLOR,
                        ClockColor.entries.toTypedArray(),
                        defaults.theme.clockColor,
                    ),
                ),
                stars = StarSettings(
                    enabled = bool(Keys.STARS_ENABLED, defaults.stars.enabled),
                    density = enum(
                        Keys.STARS_DENSITY,
                        StarDensity.entries.toTypedArray(),
                        defaults.stars.density,
                    ),
                    speed = enum(Keys.STARS_SPEED, StarSpeed.entries.toTypedArray(), defaults.stars.speed),
                    twinkling = bool(Keys.STARS_TWINKLE, defaults.stars.twinkling),
                ),
                notifications = NotificationSettings(
                    integrationEnabled = bool(Keys.NOTIF_ENABLED, defaults.notifications.integrationEnabled),
                    allowedPackages = this[Keys.NOTIF_PACKAGES] ?: emptySet(),
                    showAppIcons = bool(Keys.NOTIF_ICONS, defaults.notifications.showAppIcons),
                    showIndicators = bool(Keys.NOTIF_INDICATORS, defaults.notifications.showIndicators),
                    privacy = enum(
                        Keys.NOTIF_PRIVACY,
                        NotificationPrivacy.entries.toTypedArray(),
                        defaults.notifications.privacy,
                    ),
                    quickReplyEnabled = bool(Keys.NOTIF_QUICK_REPLY, defaults.notifications.quickReplyEnabled),
                    bubblesEnabled = bool(Keys.NOTIF_BUBBLES, defaults.notifications.bubblesEnabled),
                    floatingWindowsEnabled = bool(
                        Keys.NOTIF_FLOATING,
                        defaults.notifications.floatingWindowsEnabled,
                    ),
                    preferFloatingWindows = bool(
                        Keys.NOTIF_FLOATING_PREFER,
                        defaults.notifications.preferFloatingWindows,
                    ),
                    allowFullScreenFallback = bool(
                        Keys.NOTIF_FLOATING_FULLSCREEN,
                        defaults.notifications.allowFullScreenFallback,
                    ),
                    showFloatingInstructions = bool(
                        Keys.NOTIF_FLOATING_INSTRUCTIONS,
                        defaults.notifications.showFloatingInstructions,
                    ),
                ),
                display = DisplaySettings(
                    keepScreenAwake = bool(Keys.KEEP_AWAKE, defaults.display.keepScreenAwake),
                    fullscreen = bool(Keys.FULLSCREEN, defaults.display.fullscreen),
                    autoBrightness = bool(Keys.AUTO_BRIGHTNESS, defaults.display.autoBrightness),
                ),
                performance = PerformanceSettings(
                    batteryFriendly = bool(Keys.BATTERY_FRIENDLY, defaults.performance.batteryFriendly),
                    reduceAnimations = bool(Keys.REDUCE_ANIMATIONS, defaults.performance.reduceAnimations),
                    pauseWhenInactive = bool(Keys.PAUSE_INACTIVE, defaults.performance.pauseWhenInactive),
                    lightweightRender = bool(Keys.LIGHTWEIGHT, defaults.performance.lightweightRender),
                    batterySaver = bool(Keys.BATTERY_SAVER, defaults.performance.batterySaver),
                ),
            )
        }

        /** Serializes the settings changed by [transform] into preference edits. */
        internal fun writeTransform(
            current: AppSettings,
            next: AppSettings,
            edit: androidx.datastore.preferences.core.MutablePreferences,
        ) {
            fun putBool(key: Preferences.Key<Boolean>, a: Boolean, b: Boolean) {
                if (a != b) edit[key] = b
            }
            fun putFloat(key: Preferences.Key<Float>, a: Float, b: Float) {
                if (a != b) edit[key] = b
            }
            fun putEnum(key: Preferences.Key<String>, a: Enum<*>, b: Enum<*>) {
                if (a != b) edit[key] = b.name
            }

            val c = current.clock
            val n = next.clock
            putBool(Keys.USE_24H, c.use24Hour, n.use24Hour)
            putBool(Keys.SHOW_SECONDS, c.showSeconds, n.showSeconds)
            putBool(Keys.SHOW_DATE, c.showDate, n.showDate)
            putBool(Keys.SHOW_DAY, c.showDay, n.showDay)
            putFloat(Keys.FONT_SCALE, c.fontScale, n.fontScale)
            putFloat(Keys.BRIGHTNESS, c.brightness, n.brightness)

            val t = current.theme
            val nt = next.theme
            putEnum(Keys.THEME, t.theme, nt.theme)
            putEnum(Keys.AMOLED_MODE, t.amoledMode, nt.amoledMode)
            putBool(Keys.BURN_IN, t.burnInProtection, nt.burnInProtection)
            putBool(Keys.MINIMAL_CLOCK, t.minimalClockMode, nt.minimalClockMode)
            putFloat(Keys.WINDOW_BRIGHTNESS, t.windowBrightness, nt.windowBrightness)
            putEnum(Keys.CLOCK_COLOR, t.clockColor, nt.clockColor)

            val s = current.stars
            val ns = next.stars
            putBool(Keys.STARS_ENABLED, s.enabled, ns.enabled)
            putEnum(Keys.STARS_DENSITY, s.density, ns.density)
            putEnum(Keys.STARS_SPEED, s.speed, ns.speed)
            putBool(Keys.STARS_TWINKLE, s.twinkling, ns.twinkling)

            val no = current.notifications
            val nn = next.notifications
            putBool(Keys.NOTIF_ENABLED, no.integrationEnabled, nn.integrationEnabled)
            if (no.allowedPackages != nn.allowedPackages) edit[Keys.NOTIF_PACKAGES] = nn.allowedPackages
            putBool(Keys.NOTIF_ICONS, no.showAppIcons, nn.showAppIcons)
            putBool(Keys.NOTIF_INDICATORS, no.showIndicators, nn.showIndicators)
            putEnum(Keys.NOTIF_PRIVACY, no.privacy, nn.privacy)
            putBool(Keys.NOTIF_QUICK_REPLY, no.quickReplyEnabled, nn.quickReplyEnabled)
            putBool(Keys.NOTIF_BUBBLES, no.bubblesEnabled, nn.bubblesEnabled)
            putBool(Keys.NOTIF_FLOATING, no.floatingWindowsEnabled, nn.floatingWindowsEnabled)
            putBool(Keys.NOTIF_FLOATING_PREFER, no.preferFloatingWindows, nn.preferFloatingWindows)
            putBool(Keys.NOTIF_FLOATING_FULLSCREEN, no.allowFullScreenFallback, nn.allowFullScreenFallback)
            putBool(Keys.NOTIF_FLOATING_INSTRUCTIONS, no.showFloatingInstructions, nn.showFloatingInstructions)

            val d = current.display
            val nd = next.display
            putBool(Keys.KEEP_AWAKE, d.keepScreenAwake, nd.keepScreenAwake)
            putBool(Keys.FULLSCREEN, d.fullscreen, nd.fullscreen)
            putBool(Keys.AUTO_BRIGHTNESS, d.autoBrightness, nd.autoBrightness)

            val p = current.performance
            val np = next.performance
            putBool(Keys.BATTERY_FRIENDLY, p.batteryFriendly, np.batteryFriendly)
            putBool(Keys.REDUCE_ANIMATIONS, p.reduceAnimations, np.reduceAnimations)
            putBool(Keys.PAUSE_INACTIVE, p.pauseWhenInactive, np.pauseWhenInactive)
            putBool(Keys.LIGHTWEIGHT, p.lightweightRender, np.lightweightRender)
            putBool(Keys.BATTERY_SAVER, p.batterySaver, np.batterySaver)
        }
    }
}

/** Locale-derived 12/24h default for a first launch. */
fun systemUses24Hour(context: Context): Boolean =
    android.text.format.DateFormat.is24HourFormat(context) ||
        Locale.getDefault().language in setOf("fr", "de", "es", "pt", "it", "nl", "ru", "zh", "ja", "ko")
