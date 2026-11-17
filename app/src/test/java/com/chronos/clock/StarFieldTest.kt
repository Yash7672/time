package com.chronos.clock

import com.chronos.clock.presentation.background.StarField
import com.chronos.clock.domain.model.StarDensity
import com.chronos.clock.domain.model.StarSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarFieldTest {

    @Test
    fun `density presets map to expected particle counts`() {
        val low = StarField.generate(StarDensity.LOW, StarSpeed.SLOW, true, true, 300)
        val medium = StarField.generate(StarDensity.MEDIUM, StarSpeed.SLOW, true, true, 300)
        val high = StarField.generate(StarDensity.HIGH, StarSpeed.SLOW, true, true, 300)

        assertEquals(60, low.count)
        assertEquals(120, medium.count)
        assertEquals(220, high.count)
    }

    @Test
    fun `maxParticles ceiling is honoured`() {
        val capped = StarField.generate(StarDensity.HIGH, StarSpeed.NORMAL, true, true, 40)
        assertEquals(40, capped.count)
    }

    @Test
    fun `same seed produces identical field`() {
        val a = StarField.generate(StarDensity.MEDIUM, StarSpeed.SLOW, true, true, 300)
        val b = StarField.generate(StarDensity.MEDIUM, StarSpeed.SLOW, true, true, 300)

        assertTrue(a.xs.contentEquals(b.xs))
        assertTrue(a.ys.contentEquals(b.ys))
        assertTrue(a.radii.contentEquals(b.radii))
        assertTrue(a.baseAlphas.contentEquals(b.baseAlphas))
    }

    @Test
    fun `positions are normalized and radii are tiny`() {
        val f = StarField.generate(StarDensity.HIGH, StarSpeed.SLOW, true, true, 300)
        for (i in 0 until f.count) {
            assertTrue(f.xs[i] in 0f..1f)
            assertTrue(f.ys[i] in 0f..1f)
            // AMOLED: stars stay small so illuminated area stays minimal.
            assertTrue(f.radii[i] in 0.6f..1.8f)
            assertTrue("alpha=${f.baseAlphas[i]}", f.baseAlphas[i] in 0.15f..0.6f)
        }
    }

    @Test
    fun `static field marks itself not animated`() {
        val f = StarField.generate(StarDensity.MEDIUM, StarSpeed.SLOW, true, false, 300)
        assertTrue(!f.animated)
    }

    @Test
    fun `color kinds are only white blue or purple`() {
        val f = StarField.generate(StarDensity.HIGH, StarSpeed.SLOW, true, true, 300)
        for (i in 0 until f.count) {
            assertTrue(f.colorKinds[i].toInt() in 0..2)
        }
    }
}
