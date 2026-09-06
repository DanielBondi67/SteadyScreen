package com.steadyscreen.stabilization

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class StabilizationEngineTest {
    private fun pitch(radians: Double) = Quaternion(cos(radians / 2), sin(radians / 2), 0.0, 0.0)
    private class Motion(val engine: StabilizationEngine = StabilizationEngine()) {
        var time = 1_000_000_000L
        fun sample(q: Quaternion, step: Long = 5_000_000L, rotation: Int = 0): Float {
            time += step
            engine.onOrientation(time, q, rotation)
            return engine.frame(time).translationY
        }
    }

    @Test fun stationaryAtArbitraryOrientationIsNeutral() {
        val motion = Motion()
        repeat(500) { assertEquals(0f, motion.sample(pitch(0.8)), 0.0001f) }
        assertEquals(200.0, motion.engine.orientationHz, 0.01)
    }

    @Test fun slowMovementHasTemporaryResponseThenRecenters() {
        val motion = Motion()
        motion.sample(pitch(0.0))
        var peak = 0f
        repeat(400) { peak = maxOf(peak, abs(motion.sample(pitch(it * 0.0005)))) }
        assertTrue(peak > 1f)
        assertTrue(peak < 35f)
        repeat(1000) { motion.sample(pitch(0.2)) }
        assertEquals(0f, motion.engine.frame(motion.time).translationY, 0.01f)
    }

    @Test fun fastPitchProducesOppositeOutputAndDecaysWhenHeld() {
        val motion = Motion()
        motion.sample(pitch(0.0))
        assertTrue(motion.sample(pitch(0.06)) < -1f)
        repeat(1000) { motion.sample(pitch(0.06)) }
        assertEquals(0f, motion.engine.frame(motion.time).translationY, 0.01f)
    }

    @Test fun impulseAndReboundStayBoundedAndSettle() {
        val motion = Motion()
        motion.sample(pitch(0.0))
        repeat(10) { assertTrue(motion.sample(pitch(0.15)) < 0f) }
        repeat(1000) { assertTrue(abs(motion.sample(pitch(0.0))) <= 80f) }
        assertEquals(0f, motion.engine.frame(motion.time).translationY, 0.01f)
    }

    @Test fun noiseInsideDeadZoneDoesNotMoveText() {
        val motion = Motion()
        motion.sample(pitch(0.0))
        repeat(500) { assertEquals(0f, motion.sample(pitch(if (it % 2 == 0) 0.0005 else -0.0005)), 0f) }
    }

    @Test fun gainChangesLiveAndProportionally() {
        val motion = Motion()
        motion.sample(pitch(0.0))
        val low = motion.sample(pitch(0.03))
        motion.engine.config = motion.engine.config.copy(gain = 1.2f)
        assertEquals(low * 2, motion.engine.frame(motion.time + 1).translationY, 0.0001f)
    }

    @Test fun clampHoldsInBothDirections() {
        val motion = Motion(StabilizationEngine(StabilizationConfig(gain = 20f, smoothingTimeConstantSeconds = 0.0)))
        motion.sample(pitch(0.0))
        assertEquals(-80f, motion.sample(pitch(0.8)), 0f)
        assertEquals(80f, motion.sample(pitch(-0.8)), 0f)
    }

    @Test fun resetClearsStateAndAcceptsNewBaseline() {
        val motion = Motion()
        motion.sample(pitch(0.0))
        motion.sample(pitch(0.2))
        motion.engine.reset()
        assertEquals(StabilizationTransform(), motion.engine.frame(motion.time))
        assertEquals(0.0, motion.engine.relativePitchRadians, 0.0)
        assertEquals(0.0, motion.engine.orientationHz, 0.0)
        assertEquals(0f, motion.sample(pitch(1.0)), 0f)
    }

    @Test fun disablingSmoothlyReturnsToZeroWithoutNewSensors() {
        val motion = Motion()
        motion.sample(pitch(0.0))
        var previous = abs(motion.sample(pitch(0.2)))
        motion.engine.enabled = false
        val first = abs(motion.engine.frame(motion.time + 16_000_000L).translationY)
        assertTrue(first > 0f && first < previous)
        repeat(100) {
            val next = abs(motion.engine.frame(motion.time + (it + 2) * 16_000_000L).translationY)
            assertTrue(next <= previous)
            previous = next
        }
        assertEquals(0f, previous, 0f)
    }

    @Test fun disablingIgnoresOngoingMotionAndReenablesFromCurrentPose() {
        val motion = Motion()
        motion.sample(pitch(0.0))
        var previous = abs(motion.sample(pitch(0.2)))
        motion.engine.enabled = false
        repeat(400) {
            val next = abs(motion.sample(pitch(if (it % 2 == 0) -0.2 else 0.2)))
            assertTrue(next <= previous)
            previous = next
        }
        assertEquals(0f, previous, 0f)
        motion.engine.enabled = true
        assertEquals(0f, motion.sample(pitch(0.2)), 0f)
        assertTrue(motion.sample(pitch(0.25)) < 0f)
    }

    @Test fun zeroGainSuppressesOngoingMotionAndGainCanBeRestored() {
        val motion = Motion()
        motion.sample(pitch(0.0))
        assertTrue(motion.sample(pitch(0.1)) < 0f)
        motion.engine.config = motion.engine.config.copy(gain = 0f)
        repeat(40) {
            assertEquals(0f, motion.sample(pitch(if (it % 2 == 0) -0.1 else 0.1)), 0f)
        }
        motion.engine.config = motion.engine.config.copy(gain = 0.6f)
        assertTrue(motion.sample(pitch(0.2)) < 0f)
    }

    @Test fun staleOrUnavailableSensorsReturnToZeroAndResumeWithNewBaseline() {
        for (available in listOf(true, false)) {
            val motion = Motion()
            motion.sample(pitch(0.0))
            val start = abs(motion.sample(pitch(0.1)))
            val next = abs(motion.engine.frame(motion.time + 300_000_000L, available).translationY)
            assertTrue(next < start)
            assertEquals(0f, motion.engine.frame(motion.time + 2_000_000_000L, available).translationY, 0.01f)
            assertEquals(0f, motion.sample(pitch(0.7), step = 2_100_000_000L), 0f)
        }
    }

    @Test fun equivalentQuaternionSignsDoNotCreateMotion() {
        val motion = Motion()
        val q = pitch(0.5)
        motion.sample(q)
        repeat(100) {
            assertEquals(0f, motion.sample(if (it % 2 == 0) q else Quaternion(-q.w, -q.x, -q.y, -q.z)), 0.001f)
        }
    }

    @Test fun invalidAndOutOfOrderSamplesDoNotPoisonState() {
        val motion = Motion()
        motion.sample(pitch(0.0))
        val valid = motion.sample(pitch(0.1))
        motion.engine.onOrientation(motion.time - 1, pitch(1.0))
        motion.engine.onOrientation(motion.time, pitch(1.0))
        motion.engine.onOrientation(motion.time + 1, Quaternion(Double.NaN, 0.0, 0.0, 0.0))
        motion.engine.onOrientation(motion.time + 2, Quaternion(0.0, 0.0, 0.0, 0.0))
        assertEquals(valid, motion.engine.frame(motion.time + 3).translationY, 0.0001f)
    }

    @Test fun displayPitchWorksInAllFourRotations() {
        for (rotation in 0..3) {
            val motion = Motion()
            val basis = Quaternion.screenBasis(rotation)
            motion.sample(Quaternion.Identity, rotation = rotation)
            val deviceMotion = basis * pitch(0.06) * basis.inverseUnit()
            assertTrue(motion.sample(deviceMotion, rotation = rotation) < -1f)
        }
    }

    @Test fun changingDisplayRotationRebasesWithoutAPitchJump() {
        val motion = Motion()
        motion.sample(pitch(0.0))
        motion.sample(pitch(0.1))
        assertEquals(0f, motion.sample(pitch(0.1), rotation = 1), 0f)
    }

    @Test fun pureYawAndRollDoNotCompensate() {
        for (q in listOf(Quaternion(cos(0.05), 0.0, sin(0.05), 0.0),
            Quaternion(cos(0.05), 0.0, 0.0, sin(0.05)))) {
            val motion = Motion()
            motion.sample(Quaternion.Identity)
            assertEquals(0f, motion.sample(q), 0.001f)
        }
    }

    @Test fun timeConstantsAreConsistentAcrossSensorRates() {
        fun response(step: Long): Float {
            val motion = Motion()
            motion.sample(pitch(0.0))
            repeat((100_000_000L / step).toInt()) { motion.sample(pitch(0.04), step) }
            return motion.engine.frame(motion.time).translationY
        }
        assertEquals(response(5_000_000L), response(10_000_000L), 0.4f)
    }

    @Test fun directionCanBeReversedForPhysicalCalibration() {
        val normal = Motion()
        val reversed = Motion(StabilizationEngine(StabilizationConfig(compensationDirection = 1f)))
        normal.sample(pitch(0.0))
        reversed.sample(pitch(0.0))
        assertEquals(-normal.sample(pitch(0.1)), reversed.sample(pitch(0.1)), 0.0001f)
    }
}
