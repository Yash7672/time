package com.chronos.clock

import com.chronos.clock.domain.model.NotificationSettings
import com.chronos.clock.notifications.FloatingAppLauncher
import com.chronos.clock.notifications.FloatingAppLauncher.Capabilities
import com.chronos.clock.notifications.FloatingAppLauncher.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings × device-capability → launch-ladder contract for
 * Settings → Notifications → Floating App Windows. `planFor` is pure, so
 * the behaviour that decides how a tapped app opens is verifiable without
 * a device — including the iQOO/vivo automatic Small Window attempt and
 * its last-resort guided fallback.
 */
class FloatingAppLauncherTest {

    private val freeformPhone = Capabilities(freeform = true, smallWindow = false)
    private val iqooPhone = Capabilities(freeform = false, smallWindow = true)
    private val plainPhone = Capabilities(freeform = false, smallWindow = false)

    // ——— Standard freeform devices ———

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
        // Freeform support is known-good on this device, so the float
        // attempt itself does not need the full-screen fallback.
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

    // ——— iQOO / vivo Small Window (automatic attempt, guide last) ———

    @Test
    fun `iqoo tap plans automatic float first and guide only as last resort`() {
        val plan = FloatingAppLauncher.planFor(NotificationSettings(), iqooPhone)
        assertEquals(
            listOf(Step.FLOAT, Step.SPLIT, Step.FULL_SCREEN, Step.HANDLE, Step.GUIDE_SMALL_WINDOW),
            plan,
        )
        // The guided workflow can never stand in the way of the tap:
        // it is always the final step, after every launch route.
        assertEquals(Step.GUIDE_SMALL_WINDOW, plan.last())
        assertTrue(plan.indexOf(Step.FLOAT) < plan.indexOf(Step.GUIDE_SMALL_WINDOW))
    }

    @Test
    fun `iqoo prefer off attempts split first then the small window float`() {
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(preferFloatingWindows = false),
            iqooPhone,
        )
        assertEquals(
            listOf(Step.SPLIT, Step.FLOAT, Step.FULL_SCREEN, Step.HANDLE, Step.GUIDE_SMALL_WINDOW),
            plan,
        )
    }

    @Test
    fun `iqoo with instructions off never plans the guide step`() {
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(showFloatingInstructions = false),
            iqooPhone,
        )
        assertEquals(listOf(Step.FLOAT, Step.SPLIT, Step.FULL_SCREEN, Step.HANDLE), plan)
        assertFalse(plan.contains(Step.GUIDE_SMALL_WINDOW))
    }

    @Test
    fun `iqoo with full screen fallback off skips the float attempt too`() {
        // Without fallback, a windowing hint the system ignores would
        // land full-screen — which this switch forbids — so no attempt.
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(allowFullScreenFallback = false),
            iqooPhone,
        )
        assertEquals(listOf(Step.SPLIT, Step.HANDLE, Step.GUIDE_SMALL_WINDOW), plan)
        assertFalse(plan.contains(Step.FLOAT))
        assertFalse(plan.contains(Step.FULL_SCREEN))
    }

    @Test
    fun `iqoo with floating master switch off gets neither float nor guide`() {
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(floatingWindowsEnabled = false),
            iqooPhone,
        )
        assertEquals(listOf(Step.SPLIT, Step.FULL_SCREEN, Step.HANDLE), plan)
        assertFalse(plan.contains(Step.FLOAT))
        assertFalse(plan.contains(Step.GUIDE_SMALL_WINDOW))
    }

    @Test
    fun `iqoo that also advertises freeform floats even with fallback off`() {
        val caps = Capabilities(freeform = true, smallWindow = true)
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(allowFullScreenFallback = false),
            caps,
        )
        assertEquals(listOf(Step.FLOAT, Step.SPLIT, Step.HANDLE, Step.GUIDE_SMALL_WINDOW), plan)
    }

    // ——— Generic devices ———

    @Test
    fun `device without freeform never plans a float step`() {
        val plan = FloatingAppLauncher.planFor(NotificationSettings(), plainPhone)
        assertEquals(listOf(Step.SPLIT, Step.FULL_SCREEN, Step.HANDLE), plan)
        assertFalse(plan.contains(Step.FLOAT))
    }

    @Test
    fun `generic device never plans the small window guide`() {
        val plan = FloatingAppLauncher.planFor(
            NotificationSettings(showFloatingInstructions = true),
            plainPhone,
        )
        assertFalse(plan.contains(Step.GUIDE_SMALL_WINDOW))
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
