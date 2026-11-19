package com.chronos.clock

import com.chronos.clock.domain.model.NotificationSettings
import com.chronos.clock.notifications.FloatingAppLauncher
import com.chronos.clock.notifications.FloatingAppLauncher.Step
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Settings → launch-ladder contract for Settings → Notifications →
 * Floating App Windows. `planFor` is pure, so the behaviour that decides
 * how a tapped app opens is verifiable without a device.
 */
class FloatingAppLauncherTest {

    @Test
    fun `defaults prefer floating then split then full screen then handle`() {
        val plan = FloatingAppLauncher.planFor(NotificationSettings())
        assertEquals(
            listOf(Step.FLOAT, Step.SPLIT, Step.FULL_SCREEN, Step.HANDLE),
            plan,
        )
    }

    @Test
    fun `prefer off puts split screen before floating`() {
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(preferFloatingWindows = false),
        )
        assertEquals(
            listOf(Step.SPLIT, Step.FLOAT, Step.FULL_SCREEN, Step.HANDLE),
            plan,
        )
    }

    @Test
    fun `floating disabled removes the float step entirely`() {
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(floatingWindowsEnabled = false),
        )
        assertEquals(listOf(Step.SPLIT, Step.FULL_SCREEN, Step.HANDLE), plan)
    }

    @Test
    fun `full screen fallback off skips plain launch but keeps handle`() {
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(allowFullScreenFallback = false),
        )
        assertEquals(listOf(Step.FLOAT, Step.SPLIT, Step.HANDLE), plan)
    }

    @Test
    fun `no floating and no fallback leaves split then handle only`() {
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(
                floatingWindowsEnabled = false,
                allowFullScreenFallback = false,
            ),
        )
        assertEquals(listOf(Step.SPLIT, Step.HANDLE), plan)
    }

    @Test
    fun `prefer off with fallback off floats only after split screen`() {
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(
                preferFloatingWindows = false,
                allowFullScreenFallback = false,
            ),
        )
        assertEquals(listOf(Step.SPLIT, Step.FLOAT, Step.HANDLE), plan)
    }

    @Test
    fun `instructions toggle does not change the launch ladder`() {
        val withInstructions = FloatingAppLauncher.planFor(
            NotificationSettings(showFloatingInstructions = true),
        )
        val without = FloatingAppLauncher.planFor(
            NotificationSettings(showFloatingInstructions = false),
        )
        assertEquals(withInstructions, without)
    }
}
