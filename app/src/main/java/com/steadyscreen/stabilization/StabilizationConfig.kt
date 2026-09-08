package com.steadyscreen.stabilization

enum class StabilizationMode { Manual, Adaptive }

/** Experimental values in radians, seconds and physical screen pixels (not dp). */
data class StabilizationConfig(
    val predictionEnabled: Boolean = false,
    val predictionHorizonSeconds: Double = 0.012,
    val maxPredictionHorizonSeconds: Double = 0.02,
    val predictionGyroTimeoutSeconds: Double = 0.05,
    val maxPredictionAngularVelocity: Double = 4.0,
    val mode: StabilizationMode = StabilizationMode.Manual,
    val gyroNoiseFloor: Double = 0.02,
    val gyroFullScale: Double = 1.5,
    val gyroShakeWeight: Double = 0.6,
    val accelerationShakeWeight: Double = 0.4,
    val adaptiveMinMultiplier: Double = 0.25,
    val adaptiveMaxMultiplier: Double = 1.5,
    val adaptiveAttackSeconds: Double = 0.08,
    val adaptiveReleaseSeconds: Double = 0.6,
    val accelerationNoiseFloor: Double = 0.15,
    val accelerationFullScale: Double = 3.0,
    val motionAttackSeconds: Double = 0.015,
    val motionReleaseSeconds: Double = 0.25,
    val horizontalGain: Float = 0.4f,
    val maxHorizontalTranslationPx: Float = 60f,
    val horizontalDeadZoneRadians: Double = 0.0015,
    val horizontalCompensationDirection: Float = 1f,
    val gain: Float = 0.6f,
    val maxVerticalTranslationPx: Float = 80f,
    val deadZoneRadians: Double = 0.0015,
    val referenceTimeConstantSeconds: Double = 0.45,
    val smoothingTimeConstantSeconds: Double = 0.018,
    val returnTimeConstantSeconds: Double = 0.08,
    val pixelsPerRadian: Float = 1_000f,
    val compensationDirection: Float = -1f,
    val overscanScale: Float = 1.08f,
    val sensorTimeoutSeconds: Double = 0.25,
    val sensorSamplingPeriodUs: Int = 5_000,
    val debugIntervalNanos: Long = 200_000_000L,
) {
    init {
        require(predictionHorizonSeconds.isFinite() && predictionHorizonSeconds >= 0.0)
        require(maxPredictionHorizonSeconds.isFinite() && maxPredictionHorizonSeconds in 0.0..0.05)
        require(predictionGyroTimeoutSeconds.isFinite() && predictionGyroTimeoutSeconds > 0.0 && predictionGyroTimeoutSeconds <= 0.25)
        require(maxPredictionAngularVelocity.isFinite() && maxPredictionAngularVelocity > 0.0 && maxPredictionAngularVelocity <= 20.0)
        require(gyroNoiseFloor.isFinite() && gyroNoiseFloor >= 0.0)
        require(gyroFullScale.isFinite() && gyroFullScale > gyroNoiseFloor)
        require(gyroShakeWeight.isFinite() && gyroShakeWeight in 0.0..1.0)
        require(accelerationShakeWeight.isFinite() && accelerationShakeWeight in 0.0..1.0)
        require(adaptiveMinMultiplier.isFinite() && adaptiveMinMultiplier >= 0.0)
        require(adaptiveMaxMultiplier.isFinite() && adaptiveMaxMultiplier >= adaptiveMinMultiplier && adaptiveMaxMultiplier <= 4.0)
        require(adaptiveAttackSeconds.isFinite() && adaptiveAttackSeconds > 0.0)
        require(adaptiveReleaseSeconds.isFinite() && adaptiveReleaseSeconds > 0.0)
        require(accelerationNoiseFloor.isFinite() && accelerationNoiseFloor >= 0.0)
        require(accelerationFullScale.isFinite() && accelerationFullScale > accelerationNoiseFloor)
        require(motionAttackSeconds.isFinite() && motionAttackSeconds > 0.0)
        require(motionReleaseSeconds.isFinite() && motionReleaseSeconds > 0.0)
        require(horizontalGain.isFinite() && horizontalGain >= 0f)
        require(maxHorizontalTranslationPx.isFinite() && maxHorizontalTranslationPx > 0f)
        require(horizontalDeadZoneRadians.isFinite() && horizontalDeadZoneRadians >= 0.0)
        require(horizontalCompensationDirection == -1f || horizontalCompensationDirection == 1f)
        require(gain.isFinite() && gain >= 0f)
        require(maxVerticalTranslationPx.isFinite() && maxVerticalTranslationPx > 0f)
        require(deadZoneRadians.isFinite() && deadZoneRadians >= 0.0)
        require(referenceTimeConstantSeconds.isFinite() && referenceTimeConstantSeconds > 0.0)
        require(smoothingTimeConstantSeconds.isFinite() && smoothingTimeConstantSeconds >= 0.0)
        require(returnTimeConstantSeconds.isFinite() && returnTimeConstantSeconds > 0.0)
        require(pixelsPerRadian.isFinite() && pixelsPerRadian > 0f)
        require(compensationDirection == -1f || compensationDirection == 1f)
        require(overscanScale.isFinite() && overscanScale >= 1f)
        require(sensorTimeoutSeconds.isFinite() && sensorTimeoutSeconds > 0.0)
        require(sensorSamplingPeriodUs >= 5_000)
        require(debugIntervalNanos > 0L)
    }
}
