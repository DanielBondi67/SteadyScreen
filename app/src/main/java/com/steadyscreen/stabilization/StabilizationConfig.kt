package com.steadyscreen.stabilization

/** Experimental values in radians, seconds and physical screen pixels (not dp). */
data class StabilizationConfig(
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
