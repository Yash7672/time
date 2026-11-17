package com.chronos.clock.core.lifecycle

import com.chronos.clock.core.utils.TimeUtils
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Battery-efficient wall-clock ticker.
 *
 * - Emits the current time immediately, then sleeps precisely until the next
 *   second (or minute, when seconds are hidden) boundary — no polling loop,
 *   no wake lock, no background service.
 * - [invalidate] lets the owner force an immediate re-emit on system time,
 *   time-zone or locale changes, so correctness never depends on the timer.
 * - The flow is cold: it only runs while a lifecycle-aware collector is
 *   subscribed (the UI uses `collectAsStateWithLifecycle`, which stops at
 *   ON_PAUSE), so nothing ticks while the app is not visible.
 */
class ClockTicker {

    private val invalidations = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Ask the ticker to re-emit as soon as possible (time/zone changed). */
    fun invalidate() {
        invalidations.tryEmit(Unit)
    }

    /**
     * @param showSeconds true → tick every second; false → tick on minute
     * boundaries (the displayed value only changes once per minute anyway).
     */
    fun ticks(showSeconds: Boolean): Flow<Long> = flow {
        val period = if (showSeconds) 1_000L else 60_000L
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            // Sleep until the exact boundary, but wake early if the system
            // clock / time zone changed underneath us.
            withTimeoutOrNull(TimeUtils.nextTickDelay(now, period)) {
                invalidations.first()
            }
        }
    }
}
