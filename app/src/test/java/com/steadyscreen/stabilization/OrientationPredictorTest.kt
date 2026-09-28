package com.steadyscreen.stabilization

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class OrientationPredictorTest {
    private val config = StabilizationConfig(predictionEnabled = true)
    private val time = 1_000_000_000L
    private fun predict(p: OrientationPredictor, now: Long = time, c: StabilizationConfig = config,
        period: Double = 1.0 / 60) = p.predict(Quaternion.Identity, Quaternion.Identity, time, now, period, c)

    @Test fun zeroAndNoiseVelocityLeaveOrientationUnchanged() {
        val p = OrientationPredictor()
        for (x in listOf(0.0, 0.001)) {
            p.reset(); p.onGyroscope(time, x, 0.0, 0.0)
            assertEquals(Quaternion.Identity, predict(p))
            assertEquals(0.0, p.effectiveHorizonSeconds, 0.0)
        }
    }
    @Test fun constantVelocityPredictsFromFusedPoseWithoutAccumulatingDrift() {
        val p = OrientationPredictor()
        p.onGyroscope(time, 1.0, 0.0, 0.0)
        repeat(200) { assertEquals(0.012, predict(p).pitchRadians(), 1e-10) }
        assertEquals(0.017, predict(p, time + 5_000_000).pitchRadians(), 1e-10)
        val q = Quaternion(0.9, 0.2, 0.0, 0.0).normalizedOrNull()!!
        assertEquals(q.pitchRadians() + 0.012,
            p.predict(q, Quaternion.Identity, time, time, 0.016, config).pitchRadians(), 1e-10)
    }
    @Test fun disabledStaleFutureAndInvalidSamplesDoNotPredict() {
        val p = OrientationPredictor()
        p.onGyroscope(time, 1.0, 0.0, 0.0)
        assertEquals(Quaternion.Identity, predict(p, c = config.copy(predictionEnabled = false)))
        assertEquals(Quaternion.Identity, predict(p, time + 51_000_000))
        assertEquals(Quaternion.Identity, predict(p, time - 1))
        p.onGyroscope(time + 1, Double.NaN, 0.0, 0.0)
        p.onGyroscope(time - 1, 100.0, 0.0, 0.0)
        assertEquals(0.012, predict(p).pitchRadians(), 1e-10)
    }
    @Test fun horizonClampCadenceAndExtremeVelocityRemainBounded() {
        val p = OrientationPredictor()
        p.onGyroscope(time, 1.0, 0.0, 0.0)
        assertEquals(1.0 / 120, predict(p, period = 1.0 / 120).pitchRadians(), 1e-10)
        assertEquals(0.02, predict(p, time + 19_000_000).pitchRadians(), 1e-10)
        assertEquals(0.02, predict(p, c = config.copy(predictionHorizonSeconds = 100.0), period = 100.0).pitchRadians(), 1e-10)
        p.onGyroscope(time + 1, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
        val q = predict(p, time + 1)
        assertNotNull(q.normalizedOrNull())
        assertTrue(abs(q.pitchRadians()) < 0.1)
        assertTrue(abs(q.horizontalRadians()) < 0.1)
    }
    @Test fun gyroAxesFollowAllDisplayRotations() {
        for (rotation in 0..3) {
            val p = OrientationPredictor()
            val basis = Quaternion.screenBasis(rotation)
            // Display X expressed in device axes via the quarter-turn basis.
            val x = kotlin.math.cos(rotation * Math.PI / 2)
            val y = kotlin.math.sin(rotation * Math.PI / 2)
            p.onGyroscope(time, x, y, 0.0)
            val q = p.predict(basis, basis, time, time, 0.016, config)
            assertEquals(0.012, (basis.inverseUnit() * q).pitchRadians(), 1e-10)
        }
    }
    @Test fun predictionDoesNotMutateReferenceAndOffRemainsSmooth() {
        val a = StabilizationEngine(config)
        val b = StabilizationEngine(config.copy(predictionEnabled = false))
        for (e in listOf(a, b)) {
            e.onOrientation(time, Quaternion.Identity)
            e.onGyroscope(time, 1.0, 0.0, 0.0)
            e.frame(time)
        }
        assertTrue(a.frame(time + 1).translationY < b.frame(time + 1).translationY)
        a.config = a.config.copy(predictionEnabled = false)
        assertEquals(b.frame(time + 2), a.frame(time + 2))
        a.config = config
        val y = abs(a.frame(time + 3).translationY)
        a.enabled = false
        assertTrue(abs(a.frame(time + 8_000_003).translationY) < y)
        assertEquals(0.0, a.effectivePredictionHorizonSeconds, 0.0)
    }
}
