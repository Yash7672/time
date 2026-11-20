package com.chronos.clock.core.utils

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Rendered pieces of the wall clock, split so separators can be styled. */
data class ClockText(
    val hours: String,
    val minutes: String,
    val seconds: String?,
    val meridiem: String?,
) {
    /** Full string, used for semantics and for width estimation. */
    val display: String
        get() = buildString {
            append(hours).append(':').append(minutes)
            seconds?.let { append(':').append(it) }
            meridiem?.let { append(' ').append(it) }
        }
}

/**
 * Pure, allocation-light time formatting helpers.
 *
 * All functions take an explicit [ZoneId] and [Locale] so they are deterministic
 * in unit tests. `java.time` is available natively from API 26 (our minSdk),
 * so no desugaring or extra dependency is needed.
 */
object TimeUtils {

    /**
     * Date/day formatters are expensive to build, and the date block is
     * recomposed every tick — cache one formatter per pattern + locale.
     */
    private val formatters =
        java.util.concurrent.ConcurrentHashMap<String, java.time.format.DateTimeFormatter>()

    private fun formatter(
        pattern: String,
        locale: Locale,
    ): java.time.format.DateTimeFormatter =
        formatters.getOrPut("$pattern|${locale.toLanguageTag()}") {
            java.time.format.DateTimeFormatter.ofPattern(pattern, locale)
        }

    fun formatClock(
        epochMillis: Long,
        use24Hour: Boolean,
        showSeconds: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): ClockText {
        val ldt = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val hour24 = ldt.hour
        val minutes = String.format(locale, "%02d", ldt.minute)
        val seconds = if (showSeconds) String.format(locale, "%02d", ldt.second) else null
        return if (use24Hour) {
            ClockText(
                hours = String.format(locale, "%02d", hour24),
                minutes = minutes,
                seconds = seconds,
                meridiem = null,
            )
        } else {
            val hour12 = when (hour24 % 12) {
                0 -> 12
                else -> hour24 % 12
            }
            ClockText(
                hours = String.format(locale, "%02d", hour12),
                minutes = minutes,
                seconds = seconds,
                meridiem = if (hour24 < 12) "AM" else "PM",
            )
        }
    }

    /** e.g. "17 Nov 2026" */
    fun formatDate(
        epochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val ldt = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val pattern = when (locale.language) {
            "de", "fr", "es", "pt", "it" -> "dd.MM.yyyy"
            "ja" -> "yyyy年MM月dd日"
            else -> "dd MMM yyyy"
        }
        return formatter(pattern, locale).format(ldt.toLocalDate())
    }

    /** e.g. "Tuesday" */
    fun formatDay(
        epochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val ldt = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return formatter("EEEE", locale).format(ldt.toLocalDate())
    }

    /**
     * Milliseconds until the next tick of [periodMillis], aligned to the wall
     * clock so seconds change exactly when the device clock rolls over.
     * Result is always in `1..periodMillis`.
     */
    fun nextTickDelay(nowMillis: Long, periodMillis: Long): Long {
        val remainder = nowMillis % periodMillis
        val delay = periodMillis - remainder
        return if (delay <= 0) periodMillis else delay
    }
}
