package com.steadyscreen.stabilization

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class HorizontalStabilizationTest {
    private fun yaw(a: Double) = Quaternion(cos(a / 2), 0.0, sin(a / 2), 0.0)
    private class Motion(config: StabilizationConfig = StabilizationConfig()) {
        val engine = StabilizationEngine(config)
        var time = 1_000_000_000L
        fun sample(q: Quaternion, rotation: Int = 0): StabilizationTransform {
            time += 5_000_000L
            engine.onOrientation(time, q, rotation)
            return engine.frame(time)
        }
    }
    @Test fun stationaryAndNoiseAreNeutral() {
        val m = Motion()
        repeat(200) { assertEquals(0f, m.sample(yaw(0.8)).translationX, 0.0001f) }
        repeat(200) { assertEquals(0f, m.sample(yaw(0.8 + if (it % 2 == 0) 0.0004 else -0.0004)).translationX, 0.0001f) }
    }
    @Test fun slowMovementRecenters() {
        val m = Motion()
        m.sample(yaw(0.0))
        var peak = 0f
        repeat(400) { peak = maxOf(peak, abs(m.sample(yaw(it * 0.0005)).translationX)) }
        assertTrue(peak in 1f..25f)
        repeat(1000) { m.sample(yaw(0.2)) }
        assertEquals(0f, m.engine.frame(m.time).translationX, 0.01f)
    }
    @Test fun impulseAndReboundDecayWithoutVerticalOrRollOutput() {
        val m = Motion()
        m.sample(yaw(0.0))
        val first = m.sample(yaw(0.06))
        assertTrue(first.translationX > 1f)
        assertEquals(0f, first.translationY, 0f)
        assertEquals(0f, first.rotationZ, 0f)
        repeat(1000) { m.sample(yaw(0.0)) }
        assertEquals(0f, m.engine.frame(m.time).translationX, 0.01f)
    }
    @Test fun gainScalesLiveAndZeroDisablesOnlyHorizontal() {
        val m = Motion()
        m.sample(yaw(0.0))
        val first = m.sample(yaw(0.03)).translationX
        m.engine.config = m.engine.config.copy(horizontalGain = 0.8f)
        assertEquals(first * 2, m.engine.frame(++m.time).translationX, 0.0001f)
        m.engine.config = m.engine.config.copy(horizontalGain = 0f)
        assertEquals(0f, m.engine.frame(++m.time).translationX, 0f)
    }
    @Test fun clampsBothDirections() {
        val m = Motion(StabilizationConfig(horizontalGain = 20f, smoothingTimeConstantSeconds = 0.0))
        m.sample(yaw(0.0))
        assertEquals(60f, m.sample(yaw(0.8)).translationX, 0f)
        assertEquals(-60f, m.sample(yaw(-0.8)).translationX, 0f)
    }
    @Test fun displayAxesAndSignCanBeReversed() {
        for (rotation in 0..3) {
            val a = Motion()
            val b = Motion(StabilizationConfig(horizontalCompensationDirection = -1f))
            a.sample(Quaternion.Identity, rotation); b.sample(Quaternion.Identity, rotation)
            val basis = Quaternion.screenBasis(rotation)
            val q = basis * yaw(0.06) * basis.inverseUnit()
            val x = a.sample(q, rotation).translationX
            assertTrue(x > 1f)
            assertEquals(-x, b.sample(q, rotation).translationX, 0.0001f)
        }
    }
    @Test fun offDecaysBothAxesAndResetClearsState() {
        val m = Motion()
        m.sample(yaw(0.0))
        val x = m.sample(yaw(0.1)).translationX
        m.engine.enabled = false
        assertTrue(m.engine.frame(m.time + 16_000_000).translationX in 0f..x)
        m.engine.reset()
        assertEquals(StabilizationTransform(), m.engine.frame(m.time))
    }
}
