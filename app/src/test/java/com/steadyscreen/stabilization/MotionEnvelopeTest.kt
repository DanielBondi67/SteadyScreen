package com.steadyscreen.stabilization

import org.junit.Assert.*
import org.junit.Test

class MotionEnvelopeTest {
    private val config = StabilizationConfig()
    private val envelope = MotionEnvelope()
    private var time = 1_000_000_000L
    private fun sample(value: Double): Double {
        time += 5_000_000
        envelope.sample(time, value, config.accelerationNoiseFloor, config.accelerationFullScale, config)
        return envelope.valueAt(time, config)
    }
    @Test fun noiseStaysZero() { repeat(200) { assertEquals(0.0, sample(0.1), 0.0) } }
    @Test fun spikeAttacksFastAndDecaysSmoothly() {
        sample(0.0)
        var previous = sample(8.0)
        assertTrue(previous > 0.25)
        repeat(400) {
            val next = sample(0.0)
            assertTrue(next < previous && next >= 0.0)
            previous = next
        }
        assertTrue(previous < 0.001)
    }
    @Test fun sustainedVibrationAndExtremeSamplesStayBounded() {
        repeat(400) { assertTrue(sample(Double.MAX_VALUE) in 0.0..1.0) }
        assertEquals(1.0, envelope.valueAt(time, config), 0.00001)
        repeat(200) { sample(1.575) }
        assertEquals(0.5, envelope.valueAt(time, config), 0.01)
    }
    @Test fun missingStreamDecaysAndInvalidSamplesAreIgnored() {
        sample(3.0)
        val before = envelope.valueAt(time, config)
        envelope.sample(time, 0.0, 0.15, 3.0, config)
        envelope.sample(time + 1, Double.NaN, 0.15, 3.0, config)
        assertEquals(before, envelope.valueAt(time, config), 0.0)
        assertTrue(envelope.valueAt(time + 2_000_000_000, config) < 0.001)
        assertEquals(0.0, envelope.valueAt(time - 1, config), 0.0)
        envelope.reset()
        assertEquals(0.0, envelope.magnitude, 0.0)
    }
    @Test fun magnitudeUsesAllAxesAndDoesNotOverflowForLargeFiniteVectors() {
        assertEquals(13.0, vectorMagnitude(3.0, 4.0, 12.0), 0.0)
        assertTrue(vectorMagnitude(1e200, 1e200, 1e200).isFinite())
    }
}
