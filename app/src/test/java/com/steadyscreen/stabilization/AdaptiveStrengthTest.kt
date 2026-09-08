package com.steadyscreen.stabilization

import org.junit.Assert.*
import org.junit.Test

class AdaptiveStrengthTest {
    private val config = StabilizationConfig(mode = StabilizationMode.Adaptive)
    @Test fun calmAndManualHaveDefinedStrength() {
        val a = AdaptiveStrength()
        repeat(200) { assertEquals(0.25, a.update(0.0, 0.005, config), 0.0) }
        assertEquals(1.0, a.update(1.0, 0.01, config.copy(mode = StabilizationMode.Manual)), 0.0)
    }
    @Test fun onsetAttacksMonotonicallyAndSustainedMotionSettles() {
        val a = AdaptiveStrength()
        var previous = 0.25
        repeat(100) {
            val next = a.update(0.5, 0.01, config)
            assertTrue(next >= previous && next <= 0.875)
            previous = next
        }
        assertEquals(0.875, previous, 0.00001)
        repeat(100) { previous = a.update(1.0, 0.01, config) }
        assertEquals(1.5, previous, 0.00001)
    }
    @Test fun releaseIsSlowerThanAttackAndReturnsToCalm() {
        val a = AdaptiveStrength()
        repeat(1000) { a.update(1.0, 0.01, config) }
        val after80ms = a.update(0.0, 0.08, config)
        assertTrue(after80ms > 1.3)
        var previous = after80ms
        repeat(1000) {
            val next = a.update(0.0, 0.01, config)
            assertTrue(next <= previous)
            previous = next
        }
        assertEquals(0.25, previous, 0.00001)
    }
    @Test fun boundsInvalidInputsAndLiveBoundsStayFinite() {
        val a = AdaptiveStrength()
        for (score in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1e100, 1e100, 1.0)) {
            for (dt in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 100.0)) {
                val value = a.update(score, dt, config)
                assertTrue(value.isFinite() && value in 0.25..1.5)
            }
        }
        assertEquals(0.5, a.update(1.0, 10.0, config.copy(adaptiveMaxMultiplier = 0.5)), 0.00001)
    }
    @Test fun gyroOnlyAccelerationOnlyCombinedAndWeightedNormalization() {
        fun score(gyro: Double, accel: Double, c: StabilizationConfig = config): Double {
            val e = StabilizationEngine(c)
            repeat(400) {
                val t = 1_000_000_000L + it * 5_000_000L
                e.onGyroscope(t, gyro, 0.0, 0.0)
                e.onAcceleration(t, 0.0, accel, 0.0)
                e.frame(t)
            }
            return e.shakeScore
        }
        assertEquals(0.0, score(0.0, 0.0), 0.0)
        assertEquals(0.6, score(3.0, 0.0), 0.00001)
        assertEquals(0.4, score(0.0, 8.0), 0.00001)
        assertEquals(1.0, score(3.0, 8.0), 0.00001)
        assertEquals(1.0, score(3.0, 8.0, config.copy(gyroShakeWeight = 1.0, accelerationShakeWeight = 1.0)), 0.0)
    }
    @Test fun adaptiveGainsCannotBypassEitherClamp() {
        val e = StabilizationEngine(config.copy(gain = 20f, horizontalGain = 20f, smoothingTimeConstantSeconds = 0.0))
        e.onOrientation(1_000_000_000, Quaternion.Identity)
        e.frame(1_000_000_000)
        e.onOrientation(1_010_000_000, Quaternion(0.9, 0.2, 0.2, 0.0))
        val frame = e.frame(1_010_000_000)
        assertEquals(-80f, frame.translationY, 0f)
        assertEquals(60f, frame.translationX, 0f)
    }
}
