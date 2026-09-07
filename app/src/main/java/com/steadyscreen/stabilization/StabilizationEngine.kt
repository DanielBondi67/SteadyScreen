package com.steadyscreen.stabilization

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/** Pure Kotlin, single-thread confined. Sensor timestamps and frame time use elapsed realtime. */
class StabilizationEngine(var config: StabilizationConfig = StabilizationConfig()) {
    var enabled: Boolean = true
    private var reference: Quaternion? = null
    private var lastSampleNanos: Long? = null
    private var lastFrameNanos: Long? = null
    private var screenRotation = 0
    private var screenBasis = Quaternion.Identity
    private var smoothedPitch = 0.0
    private var outputY = 0f

    var relativePitchRadians: Double = 0.0
        private set
    var orientationHz: Double = 0.0
        private set

    val rawTranslationY: Float
        get() = (relativePitchRadians * config.pixelsPerRadian * config.gain *
            config.compensationDirection).toFloat()

    fun onOrientation(timestampNanos: Long, orientation: Quaternion, displayQuarterTurns: Int = 0) {
        if (timestampNanos < 0 || displayQuarterTurns !in 0..3) return
        val previous = lastSampleNanos
        if (previous != null && timestampNanos <= previous) return
        val unit = orientation.normalizedOrNull() ?: return
        val rotationChanged = displayQuarterTurns != screenRotation
        if (rotationChanged) {
            screenRotation = displayQuarterTurns
            screenBasis = Quaternion.screenBasis(displayQuarterTurns)
        }
        val current = unit * screenBasis
        val dt = if (previous == null) 0.0 else (timestampNanos - previous) * 1e-9
        lastSampleNanos = timestampNanos
        if (reference == null || rotationChanged || dt > config.sensorTimeoutSeconds) {
            reference = current
            relativePitchRadians = 0.0
            smoothedPitch = 0.0
            orientationHz = 0.0
            return
        }
        val hz = 1.0 / dt
        orientationHz = if (orientationHz == 0.0) hz else
            orientationHz + alpha(dt, 0.5) * (hz - orientationHz)

        reference = reference!!.follow(current, alpha(dt, config.referenceTimeConstantSeconds))
        relativePitchRadians = (reference!!.inverseUnit() * current).pitchRadians()
        if (!enabled) {
            // OFF is also a new baseline: re-enabling does not replay motion made while OFF.
            reference = current
            smoothedPitch = 0.0
        } else {
            smoothedPitch += alpha(dt, config.smoothingTimeConstantSeconds) *
                (relativePitchRadians - smoothedPitch)
        }
    }

    fun hasFreshOrientation(nowNanos: Long): Boolean {
        val last = lastSampleNanos ?: return false
        return nowNanos >= last && (nowNanos - last) * 1e-9 <= config.sensorTimeoutSeconds
    }

    /** Call once per display frame; no Compose state changes are required on sensor events. */
    fun frame(nowNanos: Long, sensorsAvailable: Boolean = true): StabilizationTransform {
        val previous = lastFrameNanos
        if (previous != null && nowNanos <= previous) return StabilizationTransform(translationY = outputY)
        lastFrameNanos = nowNanos
        if (enabled && sensorsAvailable && hasFreshOrientation(nowNanos)) {
            // A continuous dead zone avoids a step at the threshold.
            val pitch = sign(smoothedPitch) * (abs(smoothedPitch) - config.deadZoneRadians).coerceAtLeast(0.0)
            outputY = (pitch * config.pixelsPerRadian * config.gain * config.compensationDirection).toFloat()
        } else {
            val dt = if (previous == null) 0.0 else (nowNanos - previous) * 1e-9
            outputY *= exp(-dt / config.returnTimeConstantSeconds).toFloat()
            if (abs(outputY) < 0.01f) outputY = 0f
        }
        outputY = outputY.coerceIn(-config.maxVerticalTranslationPx, config.maxVerticalTranslationPx)
        return StabilizationTransform(translationY = outputY)
    }

    fun reset() {
        reference = null
        lastSampleNanos = null
        lastFrameNanos = null
        relativePitchRadians = 0.0
        smoothedPitch = 0.0
        outputY = 0f
        orientationHz = 0.0
    }

    private fun alpha(dt: Double, tau: Double) = if (tau == 0.0) 1.0 else 1 - exp(-dt / tau)
}
