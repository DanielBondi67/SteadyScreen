package com.steadyscreen.stabilization

import kotlin.math.exp
import kotlin.math.hypot

internal fun vectorMagnitude(x: Double, y: Double, z: Double): Double = hypot(hypot(x, y), z)

/** Constant-memory, time-based attack/release envelope. Never integrates position. */
class MotionEnvelope {
    private var timestamp: Long? = null
    private var level = 0.0
    var magnitude = 0.0
        private set

    fun sample(time: Long, value: Double, noiseFloor: Double, fullScale: Double, config: StabilizationConfig) {
        if (time < 0 || !value.isFinite() || value < 0) return
        val previous = timestamp
        if (previous != null && time <= previous) return
        // A resumed stream starts from its decayed state rather than holding an old bump.
        level = valueAt(time, config)
        val dt = if (previous == null || (time - previous) * 1e-9 > config.sensorTimeoutSeconds)
            config.sensorSamplingPeriodUs * 1e-6 else (time - previous) * 1e-9
        timestamp = time
        magnitude = value
        val target = ((value - noiseFloor) / (fullScale - noiseFloor)).coerceIn(0.0, 1.0)
        val tau = if (target > level) config.motionAttackSeconds else config.motionReleaseSeconds
        level += (1 - exp(-dt / tau)) * (target - level)
        level = level.coerceIn(0.0, 1.0)
    }

    fun valueAt(now: Long, config: StabilizationConfig): Double {
        val last = timestamp ?: return 0.0
        if (now < last) return 0.0
        val staleSeconds = ((now - last) * 1e-9 - config.sensorTimeoutSeconds).coerceAtLeast(0.0)
        return level * exp(-staleSeconds / config.motionReleaseSeconds)
    }

    fun reset() { timestamp = null; level = 0.0; magnitude = 0.0 }
}
