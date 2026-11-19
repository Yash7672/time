package com.chronos.clock

import com.chronos.clock.domain.model.NotificationSettings
import com.chronos.clock.notifications.FloatingAppLauncher
import com.chronos.clock.notifications.FloatingAppLauncher.Capabilities
import com.chronos.clock.notifications.FloatingAppLauncher.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Settings × device-capability → launch-ladder contract for
 * Settings → Notifications → Floating App Windows. `planFor` is pure, so
 * the behaviour that decides how a tapped app opens is verifiable without
 * a device — including the iQOO/vivo Small Window guided path.
 */
class FloatingAppLauncherTest {

    private val freeformPhone = Capabilities(freeform = true, smallWindow = false)
    private val iqooPhone = Capabilities(freeform = false, smallWindow = true)
    private val plainPhone = Capabilities(freeform = false, smallWindow = false)

    @Test
    fun `freeform device prefers floating then split then full screen then handle`() {
        val plan = FloatingAppLauncher.planFor(NotificationSettings(), freeformPhone)
        assertEquals(
            listOf(Step.FLOAT, Step.SPLIT, Step.FULL_SCREEN, Step.HANDLE),
            plan,
        )
    }

    @Test
    fun `prefer off puts split screen before floating`() {
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(preferFloatingWindows = false),
            freeformPhone,
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
            freeformPhone,
        )
        assertEquals(listOf(Step.SPLIT, Step.FULL_SCREEN, Step.HANDLE), plan)
    }

    @Test
    fun `full screen fallback off skips plain launch but keeps handle`() {
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(allowFullScreenFallback = false),
            freeformPhone,
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
            freeformPhone,
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
            freeformPhone,
        )
        assertEquals(listOf(Step.SPLIT, Step.FLOAT, Step.HANDLE), plan)
    }

    // ——— iQOO / vivo Small Window (the guided path) ———

    @Test
    fun `iqoo device with defaults gets the guided small window step alone`() {
        val plan = FloatingAppLauncher.planFor(NotificationSettings(), iqooPhone)
        assertEquals(listOf(Step.GUIDE_SMALL_WINDOW), plan)
    }

    @Test
    fun `iqoo device never plans a freeform float attempt`() {
        // Even when the OS happens to advertise freeform, the Small Window
        // device must not fall into the generic float ladder.
        val caps = Capabilities(freeform = true, smallWindow = true)
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(preferFloatingWindows = true),
            caps,
        )
        assertEquals(listOf(Step.GUIDE_SMALL_WINDOW), plan)
        assertFalse(plan.contains(Step.FLOAT))
    }

    @Test
    fun `iqoo with instructions off falls back to split then full screen`() {
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(showFloatingInstructions = false),
            iqooPhone,
        )
        assertEquals(listOf(Step.SPLIT, Step.FULL_SCREEN, Step.HANDLE), plan)
        assertFalse(plan.contains(Step.FLOAT))
        assertFalse(plan.contains(Step.GUIDE_SMALL_WINDOW))
    }

    @Test
    fun `iqoo with full screen fallback off gets no guided step`() {
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(allowFullScreenFallback = false),
            iqooPhone,
        )
        assertEquals(listOf(Step.SPLIT, Step.HANDLE), plan)
        assertFalse(plan.contains(Step.GUIDE_SMALL_WINDOW))
    }

    @Test
    fun `iqoo with floating master switch off gets no guided step`() {
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(floatingWindowsEnabled = false),
            iqooPhone,
        )
        assertEquals(listOf(Step.SPLIT, Step.FULL_SCREEN, Step.HANDLE), plan)
        assertFalse(plan.contains(Step.GUIDE_SMALL_WINDOW))
    }

    // ——— Generic devices ———

    @Test
    fun `device without freeform never plans a float step`() {
        val plan = FloatingAppLauncher.planFor(NotificationSettings(), plainPhone)
        assertEquals(listOf(Step.SPLIT, Step.FULL_SCREEN, Step.HANDLE), plan)
        assertFalse(plan.contains(Step.FLOAT))
    }

    @Test
    fun `instructions toggle does not change the generic ladder`() {
        val withInstructions = FloatingAppLauncher.planFor(
            NotificationSettings(showFloatingInstructions = true),
            plainPhone,
        )
        val without = FloatingAppLauncher.planFor(
            NotificationSettings(showFloatingInstructions = false),
            plainPhone,
        )
        assertEquals(withInstructions, without)
    }
}
