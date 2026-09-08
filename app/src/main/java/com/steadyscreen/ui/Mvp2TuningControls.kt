package com.steadyscreen.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.steadyscreen.stabilization.StabilizationConfig
import com.steadyscreen.stabilization.StabilizationMode

@Composable
internal fun Mvp2TuningControls(c: StabilizationConfig, change: (StabilizationConfig) -> Unit) {
    Text("Two-axis testing", style = MaterialTheme.typography.titleMedium)
    TextButton(onClick = { change(c.copy(horizontalGain = 0f, mode = StabilizationMode.Manual, predictionEnabled = false)) }) {
        Text("Use vertical Manual comparison")
    }
    TuningSlider("Horizontal gain", c.horizontalGain, 0f..2f, "%.2f") { change(c.copy(horizontalGain = it)) }
    TuningSlider("Horizontal clamp", c.maxHorizontalTranslationPx, 1f..200f, "±%.0f px") {
        change(c.copy(maxHorizontalTranslationPx = it))
    }
    TuningSlider("Horizontal dead zone", c.horizontalDeadZoneRadians.toFloat(), 0f..0.02f, "%.4f rad") {
        change(c.copy(horizontalDeadZoneRadians = it.toDouble()))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Invert horizontal direction (−1)")
        Switch(c.horizontalCompensationDirection < 0f,
            { change(c.copy(horizontalCompensationDirection = if (it) -1f else 1f)) })
    }
    Text("Shake and adaptive strength", style = MaterialTheme.typography.titleMedium)
    TuningSlider("Acceleration noise floor", c.accelerationNoiseFloor.toFloat(), 0f..1f, "%.2f m/s²") {
        change(c.copy(accelerationNoiseFloor = it.toDouble(), accelerationFullScale = maxOf(c.accelerationFullScale, it + 0.01)))
    }
    TuningSlider("Acceleration full scale", c.accelerationFullScale.toFloat(), 1.01f..10f, "%.2f m/s²") {
        change(c.copy(accelerationFullScale = it.toDouble()))
    }
    TuningSlider("Gyro noise floor", c.gyroNoiseFloor.toFloat(), 0f..0.2f, "%.3f rad/s") {
        change(c.copy(gyroNoiseFloor = it.toDouble(), gyroFullScale = maxOf(c.gyroFullScale, it + 0.01)))
    }
    TuningSlider("Gyro full scale", c.gyroFullScale.toFloat(), 0.21f..6f, "%.2f rad/s") {
        change(c.copy(gyroFullScale = it.toDouble()))
    }
    TuningSlider("Gyro shake weight", c.gyroShakeWeight.toFloat(), 0f..1f, "%.2f") {
        change(c.copy(gyroShakeWeight = it.toDouble()))
    }
    TuningSlider("Acceleration shake weight", c.accelerationShakeWeight.toFloat(), 0f..1f, "%.2f") {
        change(c.copy(accelerationShakeWeight = it.toDouble()))
    }
    TuningSlider("Motion envelope attack", c.motionAttackSeconds.toFloat(), 0.005f..0.2f, "%.3f s") {
        change(c.copy(motionAttackSeconds = it.toDouble()))
    }
    TuningSlider("Motion envelope release", c.motionReleaseSeconds.toFloat(), 0.05f..2f, "%.3f s") {
        change(c.copy(motionReleaseSeconds = it.toDouble()))
    }
    TuningSlider("Adaptive minimum", c.adaptiveMinMultiplier.toFloat(), 0f..4f, "%.2f×") {
        change(c.copy(adaptiveMinMultiplier = it.toDouble(), adaptiveMaxMultiplier = maxOf(c.adaptiveMaxMultiplier, it.toDouble())))
    }
    TuningSlider("Adaptive maximum", c.adaptiveMaxMultiplier.toFloat(), 0f..4f, "%.2f×") {
        change(c.copy(adaptiveMaxMultiplier = it.toDouble(), adaptiveMinMultiplier = minOf(c.adaptiveMinMultiplier, it.toDouble())))
    }
    TuningSlider("Adaptive attack", c.adaptiveAttackSeconds.toFloat(), 0.01f..1f, "%.3f s") {
        change(c.copy(adaptiveAttackSeconds = it.toDouble()))
    }
    TuningSlider("Adaptive release", c.adaptiveReleaseSeconds.toFloat(), 0.05f..3f, "%.3f s") {
        change(c.copy(adaptiveReleaseSeconds = it.toDouble()))
    }
    Text("Prediction and overscan", style = MaterialTheme.typography.titleMedium)
    TuningSlider("Prediction lead", (c.predictionHorizonSeconds * 1000).toFloat(), 0f..50f, "%.1f ms") {
        change(c.copy(predictionHorizonSeconds = it.toDouble() / 1000))
    }
    TuningSlider("Maximum prediction horizon", (c.maxPredictionHorizonSeconds * 1000).toFloat(), 0f..50f, "%.1f ms") {
        change(c.copy(maxPredictionHorizonSeconds = it.toDouble() / 1000))
    }
    TuningSlider("Prediction gyro freshness", (c.predictionGyroTimeoutSeconds * 1000).toFloat(), 10f..250f, "%.0f ms") {
        change(c.copy(predictionGyroTimeoutSeconds = it.toDouble() / 1000))
    }
    TuningSlider("Prediction angular speed limit", c.maxPredictionAngularVelocity.toFloat(), 0.1f..20f, "%.1f rad/s") {
        change(c.copy(maxPredictionAngularVelocity = it.toDouble()))
    }
    TuningSlider("Maximum overscan zoom", c.maxOverscanScale, 1f..2f, "%.2f×") {
        change(c.copy(maxOverscanScale = it))
    }
    Text("Zoom is fixed for the reader size and clamps. Small viewports limit visible motion when the zoom cap is reached. " +
        "The base overscan below is always retained.", style = MaterialTheme.typography.bodySmall)
}
