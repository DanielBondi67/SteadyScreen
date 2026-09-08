package com.steadyscreen.stabilization

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/** Pure Kotlin, single-thread confined. Sensor timestamps and frame time use elapsed realtime. */
class StabilizationEngine(var config: StabilizationConfig = StabilizationConfig()) {
    val bump = MotionEnvelope()
    private val rotationShake = MotionEnvelope()
    private val predictor = OrientationPredictor()
    val effectivePredictionHorizonSeconds get() = predictor.effectiveHorizonSeconds
    private var latestOrientation: Quaternion? = null
    private val adaptive = AdaptiveStrength()
    var bumpIntensity = 0.0
        private set
    var rotationalShake = 0.0
        private set
    var shakeScore = 0.0
        private set
    var adaptiveMultiplier = 1.0
        private set
    val effectiveVerticalGain get() = config.gain * adaptiveMultiplier
    val effectiveHorizontalGain get() = config.horizontalGain * adaptiveMultiplier

    fun onGyroscope(time: Long, x: Double, y: Double, z: Double) {
        predictor.onGyroscope(time, x, y, z)
        rotationShake.sample(time, vectorMagnitude(x, y, z), config.gyroNoiseFloor, config.gyroFullScale, config)
    }

    fun onAcceleration(time: Long, x: Double, y: Double, z: Double) {
        bump.sample(time, vectorMagnitude(x, y, z), config.accelerationNoiseFloor,
            config.accelerationFullScale, config)
    }

    var enabled: Boolean = true
    private var reference: Quaternion? = null
    private var lastSampleNanos: Long? = null
    private var lastFrameNanos: Long? = null
    private var screenRotation = 0
    private var screenBasis = Quaternion.Identity
    private var smoothedPitch = 0.0
    private var smoothedHorizontal = 0.0
    private var outputX = 0f
    private var outputY = 0f

    var relativePitchRadians: Double = 0.0
        private set
    var orientationHz: Double = 0.0
        private set

    var relativeHorizontalRadians: Double = 0.0
        private set

    val rawTranslationX: Float
        get() = (relativeHorizontalRadians * config.pixelsPerRadian * config.horizontalGain *
            config.horizontalCompensationDirection).toFloat()

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
        latestOrientation = current
        val dt = if (previous == null) 0.0 else (timestampNanos - previous) * 1e-9
        lastSampleNanos = timestampNanos
        if (reference == null || rotationChanged || dt > config.sensorTimeoutSeconds) {
            reference = current
            relativePitchRadians = 0.0
            relativeHorizontalRadians = 0.0
            smoothedPitch = 0.0
            smoothedHorizontal = 0.0
            orientationHz = 0.0
            return
        }
        val hz = 1.0 / dt
        orientationHz = if (orientationHz == 0.0) hz else
            orientationHz + alpha(dt, 0.5) * (hz - orientationHz)

        reference = reference!!.follow(current, alpha(dt, config.referenceTimeConstantSeconds))
        val relative = reference!!.inverseUnit() * current
        relativePitchRadians = relative.pitchRadians()
        relativeHorizontalRadians = relative.horizontalRadians()
        if (!enabled) {
            // OFF is also a new baseline: re-enabling does not replay motion made while OFF.
            reference = current
            smoothedPitch = 0.0
            smoothedHorizontal = 0.0
        } else {
            smoothedPitch += alpha(dt, config.smoothingTimeConstantSeconds) *
                (relativePitchRadians - smoothedPitch)
            smoothedHorizontal += alpha(dt, config.smoothingTimeConstantSeconds) *
                (relativeHorizontalRadians - smoothedHorizontal)
        }
    }

    fun hasFreshOrientation(nowNanos: Long): Boolean {
        val last = lastSampleNanos ?: return false
        return nowNanos >= last && (nowNanos - last) * 1e-9 <= config.sensorTimeoutSeconds
    }

    /** Call once per display frame; no Compose state changes are required on sensor events. */
    fun frame(nowNanos: Long, sensorsAvailable: Boolean = true, framePeriodSeconds: Double = 0.0): StabilizationTransform {
        val previous = lastFrameNanos
        if (previous != null && nowNanos <= previous) return StabilizationTransform(translationX = outputX, translationY = outputY)
        lastFrameNanos = nowNanos
        val frameDt = if (previous == null) 0.0 else (nowNanos - previous) * 1e-9
        bumpIntensity = bump.valueAt(nowNanos, config)
        rotationalShake = rotationShake.valueAt(nowNanos, config)
        shakeScore = (config.gyroShakeWeight * rotationalShake +
            config.accelerationShakeWeight * bumpIntensity).coerceIn(0.0, 1.0)
        adaptiveMultiplier = adaptive.update(shakeScore, frameDt, config)
        predictor.clearHorizon()
        if (enabled && sensorsAvailable && hasFreshOrientation(nowNanos)) {
            var renderPitch = smoothedPitch
            var renderHorizontal = smoothedHorizontal
            if (config.predictionEnabled) {
                val current = latestOrientation!!
                val predicted = predictor.predict(current, screenBasis, lastSampleNanos!!,
                    nowNanos, framePeriodSeconds, config)
                val relativeNow = reference!!.inverseUnit() * current
                val relativePredicted = reference!!.inverseUnit() * predicted
                // Add only the short-horizon angular increment to the validated filter output.
                // Neither the moving reference nor sensor smoothing receives predicted samples.
                renderPitch += wrappedAngle(relativePredicted.pitchRadians() - relativeNow.pitchRadians())
                renderHorizontal += relativePredicted.horizontalRadians() - relativeNow.horizontalRadians()
            }
            // A continuous dead zone avoids a step at the threshold.
            val pitch = sign(renderPitch) * (abs(renderPitch) - config.deadZoneRadians).coerceAtLeast(0.0)
            val horizontal = sign(renderHorizontal) *
                (abs(renderHorizontal) - config.horizontalDeadZoneRadians).coerceAtLeast(0.0)
            outputX = (horizontal * config.pixelsPerRadian * effectiveHorizontalGain *
                config.horizontalCompensationDirection).toFloat()
            outputY = (pitch * config.pixelsPerRadian * effectiveVerticalGain * config.compensationDirection).toFloat()
        } else {
            val dt = if (previous == null) 0.0 else (nowNanos - previous) * 1e-9
            outputX *= exp(-dt / config.returnTimeConstantSeconds).toFloat()
            if (abs(outputX) < 0.01f) outputX = 0f
            outputY *= exp(-dt / config.returnTimeConstantSeconds).toFloat()
            if (abs(outputY) < 0.01f) outputY = 0f
        }
        outputX = outputX.coerceIn(-config.maxHorizontalTranslationPx, config.maxHorizontalTranslationPx)
        outputY = outputY.coerceIn(-config.maxVerticalTranslationPx, config.maxVerticalTranslationPx)
        return StabilizationTransform(translationX = outputX, translationY = outputY)
    }

    fun reset() {
        predictor.reset()
        latestOrientation = null
        bump.reset()
        rotationShake.reset()
        adaptive.reset()
        bumpIntensity = 0.0
        rotationalShake = 0.0
        shakeScore = 0.0
        adaptiveMultiplier = if (config.mode == StabilizationMode.Manual) 1.0 else config.adaptiveMinMultiplier
        reference = null
        lastSampleNanos = null
        lastFrameNanos = null
        relativePitchRadians = 0.0
        smoothedPitch = 0.0
        outputY = 0f
        outputX = 0f
        smoothedHorizontal = 0.0
        relativeHorizontalRadians = 0.0
        orientationHz = 0.0
    }

    private fun wrappedAngle(angle: Double): Double = kotlin.math.atan2(kotlin.math.sin(angle), kotlin.math.cos(angle))

    private fun alpha(dt: Double, tau: Double) = if (tau == 0.0) 1.0 else 1 - exp(-dt / tau)
}
