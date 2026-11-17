package com.chronos.clock

import com.chronos.clock.core.utils.TimeUtils
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeUtilsTest {

    private val utc = ZoneId.of("UTC")
    private val locale = Locale.US

    @Test
    fun `clock formats as 24 hour with seconds`() {
        // 2026-11-17 10:45:32 UTC
        val millis = java.time.ZonedDateTime.of(
            2026, 11, 17, 10, 45, 32, 0, utc,
        ).toInstant().toEpochMilli()

        val result = TimeUtils.formatClock(
            epochMillis = millis,
            use24Hour = true,
            showSeconds = true,
            zone = utc,
            locale = locale,
        )

        assertEquals("10", result.hours)
        assertEquals("45", result.minutes)
        assertEquals("32", result.seconds)
        assertNull(result.meridiem)
        assertEquals("10:45:32", result.display)
    }

    @Test
    fun `clock formats as 12 hour with meridiem`() {
        val millis = java.time.ZonedDateTime.of(
            2026, 11, 17, 23, 5, 9, 0, utc,
        ).toInstant().toEpochMilli()

        val result = TimeUtils.formatClock(
            epochMillis = millis,
            use24Hour = false,
            showSeconds = true,
            zone = utc,
            locale = locale,
        )

        assertEquals("11", result.hours)
        assertEquals("PM", result.meridiem)
        assertEquals("11:05:09 PM", result.display)
    }

    @Test
    fun `midnight maps to 12 AM`() {
        val millis = java.time.ZonedDateTime.of(
            2026, 11, 17, 0, 0, 0, 0, utc,
        ).toInstant().toEpochMilli()

        val result = TimeUtils.formatClock(millis, use24Hour = false, showSeconds = true, utc, locale)
        assertEquals("12", result.hours)
        assertEquals("AM", result.meridiem)
    }

    @Test
    fun `noon maps to 12 PM`() {
        val millis = java.time.ZonedDateTime.of(
            2026, 11, 17, 12, 0, 0, 0, utc,
        ).toInstant().toEpochMilli()

        val result = TimeUtils.formatClock(millis, use24Hour = false, showSeconds = true, utc, locale)
        assertEquals("12", result.hours)
        assertEquals("PM", result.meridiem)
    }

    @Test
    fun `seconds hidden drops seconds field`() {
        val millis = 1_700_000_000_000L
        val result = TimeUtils.formatClock(millis, use24Hour = true, showSeconds = false, utc, locale)
        assertNull(result.seconds)
        assertEquals(5, result.display.length) // "HH:MM"
    }

    @Test
    fun `date and day format deterministically`() {
        val millis = java.time.ZonedDateTime.of(
            2026, 11, 17, 10, 45, 32, 0, utc,
        ).toInstant().toEpochMilli()

        assertEquals("17 Nov 2026", TimeUtils.formatDate(millis, utc, locale))
        assertEquals("Tuesday", TimeUtils.formatDay(millis, utc, locale))
    }

    @Test
    fun `next tick delay aligns to second boundary`() {
        // 1500 ms into a second → 500 ms remain.
        assertEquals(500L, TimeUtils.nextTickDelay(1_500L, 1_000L))
        // Exactly on the boundary → full period (emit immediately next tick).
        assertEquals(1_000L, TimeUtils.nextTickDelay(2_000L, 1_000L))
        // Minute granularity.
        assertEquals(30_000L, TimeUtils.nextTickDelay(30_000L, 60_000L))
        assertEquals(1L, TimeUtils.nextTickDelay(59_999L, 60_000L))
    }

    @Test
    fun `next tick delay is always within range`() {
        for (now in 0L..2_000L step 7L) {
            val d = TimeUtils.nextTickDelay(now, 1_000L)
            assertTrue("delay=$d now=$now", d in 1L..1_000L)
        }
    }

    @Test
    fun `hour rollover is correct across midnight`() {
        val millis = java.time.ZonedDateTime.of(
            2026, 11, 17, 23, 59, 59, 0, utc,
        ).toInstant().toEpochMilli()
        val result = TimeUtils.formatClock(millis, true, true, utc, locale)
        assertEquals("23", result.hours)
        assertEquals("59", result.minutes)
        assertEquals("59", result.seconds)
    }
}
