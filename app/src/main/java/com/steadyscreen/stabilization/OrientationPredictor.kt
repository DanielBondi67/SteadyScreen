package com.steadyscreen.stabilization

import kotlin.math.cos
import kotlin.math.sin

/** Short body-axis quaternion extrapolation, restarted from fused orientation on every frame. */
class OrientationPredictor {
    private var gyroTime: Long? = null
    private var x = 0.0
    private var y = 0.0
    private var z = 0.0
    var effectiveHorizonSeconds = 0.0
        private set

    fun onGyroscope(time: Long, x: Double, y: Double, z: Double) {
        if (time < 0 || (gyroTime != null && time <= gyroTime!!) ||
            !x.isFinite() || !y.isFinite() || !z.isFinite()) return
        gyroTime = time
        this.x = x; this.y = y; this.z = z
    }

    fun predict(currentScreen: Quaternion, screenBasis: Quaternion, orientationTime: Long,
        now: Long, framePeriodSeconds: Double, config: StabilizationConfig): Quaternion {
        clearHorizon()
        val time = gyroTime ?: return currentScreen
        if (!config.predictionEnabled || orientationTime < 0 || now < orientationTime || now < time ||
            (now - time) * 1e-9 > config.predictionGyroTimeoutSeconds ||
            (now - orientationTime) * 1e-9 > config.sensorTimeoutSeconds) return currentScreen
        // No assumed 60/120 Hz duration. Estimate the next presentation from observed cadence;
        // the user's lead caps that estimate, and the total includes fused-sample age.
        val lead = if (framePeriodSeconds.isFinite() && framePeriodSeconds > 0.0)
            minOf(framePeriodSeconds, config.predictionHorizonSeconds) else config.predictionHorizonSeconds
        val horizon = ((now - orientationTime) * 1e-9 + lead)
            .coerceIn(0.0, config.maxPredictionHorizonSeconds)
        val limit = config.maxPredictionAngularVelocity
        // Bound components first so even unrealistic finite inputs cannot overflow the norm.
        val bx = x.coerceIn(-limit, limit)
        val by = y.coerceIn(-limit, limit)
        val bz = z.coerceIn(-limit, limit)
        val speed = vectorMagnitude(bx, by, bz)
        if (speed <= config.gyroNoiseFloor || horizon == 0.0) return currentScreen
        effectiveHorizonSeconds = horizon
        val halfAngle = minOf(speed, limit) * horizon / 2
        val scale = sin(halfAngle) / speed
        val deltaDevice = Quaternion(cos(halfAngle), bx * scale, by * scale, bz * scale)
        // Device gyro axes do not rotate with the UI. Conjugation puts them in display axes.
        return (currentScreen * screenBasis.inverseUnit() * deltaDevice * screenBasis).normalizedOrNull()
            ?: currentScreen
    }

    fun clearHorizon() { effectiveHorizonSeconds = 0.0 }
    fun reset() { gyroTime = null; x = 0.0; y = 0.0; z = 0.0; clearHorizon() }
}
