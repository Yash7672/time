package com.chronos.clock.presentation.background

import com.chronos.clock.domain.model.StarDensity
import com.chronos.clock.domain.model.StarSpeed
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Precomputed star particle.
 *
 * Fields are primitives so the whole field is a flat, allocation-free array
 * during drawing. Positions are normalized (0..1) so the field survives
 * rotation/resizing without regeneration.
 *
 * AMOLED notes: radii are deliberately tiny (0.6–1.8 px at density 1) and
 * alpha is capped low, keeping the illuminated pixel area minimal.
 */
class StarField(
    val count: Int,
    val xs: FloatArray,
    val ys: FloatArray,
    val radii: FloatArray,
    val baseAlphas: FloatArray,
    /** Base velocities in normalized units per second (very slow drift). */
    val vxs: FloatArray,
    val vys: FloatArray,
    /** Twinkle phase offset and speed (rad/s). */
    val phases: FloatArray,
    val twinkleSpeeds: FloatArray,
    /** Packed ARGB colours: 0 = white, 1 = blue, 2 = purple. */
    val colorKinds: ByteArray,
    val twinkling: Boolean,
    val animated: Boolean,
) {
    companion object {
        private const val RADIUS_MIN = 0.6f
        private const val RADIUS_MAX = 1.8f
        private const val ALPHA_MIN = 0.18f
        private const val ALPHA_MAX = 0.55f

        /**
         * Builds a deterministic field. Allocation happens once per setting
         * change (remembered), never inside the draw loop.
         *
         * @param maxParticles hard ceiling from performance settings.
         */
        fun generate(
            density: StarDensity,
            speed: StarSpeed,
            twinkling: Boolean,
            animated: Boolean,
            maxParticles: Int,
            seed: Long = 42L,
        ): StarField {
            val base = when (density) {
                StarDensity.LOW -> 60
                StarDensity.MEDIUM -> 120
                StarDensity.HIGH -> 220
            }
            val count = base.coerceIn(1, maxParticles.coerceAtLeast(1))
            val rng = Random(seed)
            val speedScale = when (speed) {
                StarSpeed.SLOW -> 0.0045f // full screen ≈ 3.7 minutes
                StarSpeed.NORMAL -> 0.012f
            }

            val xs = FloatArray(count)
            val ys = FloatArray(count)
            val radii = FloatArray(count)
            val alphas = FloatArray(count)
            val vxs = FloatArray(count)
            val vys = FloatArray(count)
            val phases = FloatArray(count)
            val twinkleSpeeds = FloatArray(count)
            val kinds = ByteArray(count)

            for (i in 0 until count) {
                xs[i] = rng.nextFloat()
                ys[i] = rng.nextFloat()
                // Mostly tiny stars; ~12% slightly larger for depth.
                radii[i] = if (rng.nextFloat() < 0.88f) {
                    rng.nextFloat() * (RADIUS_MAX - RADIUS_MIN) * 0.55f + RADIUS_MIN
                } else {
                    rng.nextFloat() * (RADIUS_MAX - RADIUS_MIN) + RADIUS_MIN
                }
                alphas[i] = rng.nextFloat() * (ALPHA_MAX - ALPHA_MIN) + ALPHA_MIN

                // Slow diagonal drift with per-star variance.
                val angle = rng.nextFloat() * (Math.PI.toFloat() * 2f)
                val magnitude = speedScale * (0.5f + rng.nextFloat())
                vxs[i] = cos(angle) * magnitude
                vys[i] = sin(angle) * magnitude * 0.6f

                phases[i] = rng.nextFloat() * (Math.PI.toFloat() * 2f)
                // ~0.15–0.45 rad/s: a gentle 14–40 s twinkle cycle.
                twinkleSpeeds[i] = 0.15f + rng.nextFloat() * 0.30f

                val roll = rng.nextFloat()
                kinds[i] = when {
                    roll < 0.80f -> 0 // white (dim)
                    roll < 0.92f -> 1 // subtle blue
                    else -> 2         // subtle purple
                }
            }

            return StarField(
                count = count,
                xs = xs,
                ys = ys,
                radii = radii,
                baseAlphas = alphas,
                vxs = vxs,
                vys = vys,
                phases = phases,
                twinkleSpeeds = twinkleSpeeds,
                colorKinds = kinds,
                twinkling = twinkling,
                animated = animated,
            )
        }
    }
}
