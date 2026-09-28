package com.steadyscreen.settings

import com.steadyscreen.stabilization.StabilizationConfig
import com.steadyscreen.stabilization.StabilizationMode

/** Only user choices are persisted. Sensor samples and orientation state stay in memory. */
internal data class ReadingSettings(
    val enabled: Boolean = true,
    val config: StabilizationConfig = StabilizationConfig(),
    val showDebug: Boolean = true,
    val readingText: String = "",
)

/** Named keys allow new settings to pick up defaults when loading older installations. */
internal object SettingsCodec {
    fun encode(settings: ReadingSettings): Map<String, String> = with(settings.config) {
        mapOf(
            "enabled" to settings.enabled.toString(),
            "showDebug" to settings.showDebug.toString(),
            "gain" to gain.toString(),
            "predictionEnabled" to predictionEnabled.toString(),
            "predictionHorizonSeconds" to predictionHorizonSeconds.toString(),
            "maxPredictionHorizonSeconds" to maxPredictionHorizonSeconds.toString(),
            "predictionGyroTimeoutSeconds" to predictionGyroTimeoutSeconds.toString(),
            "maxPredictionAngularVelocity" to maxPredictionAngularVelocity.toString(),
            "mode" to mode.toString(),
            "gyroNoiseFloor" to gyroNoiseFloor.toString(),
            "gyroFullScale" to gyroFullScale.toString(),
            "gyroShakeWeight" to gyroShakeWeight.toString(),
            "accelerationShakeWeight" to accelerationShakeWeight.toString(),
            "adaptiveMinMultiplier" to adaptiveMinMultiplier.toString(),
            "adaptiveMaxMultiplier" to adaptiveMaxMultiplier.toString(),
            "adaptiveAttackSeconds" to adaptiveAttackSeconds.toString(),
            "adaptiveReleaseSeconds" to adaptiveReleaseSeconds.toString(),
            "accelerationNoiseFloor" to accelerationNoiseFloor.toString(),
            "accelerationFullScale" to accelerationFullScale.toString(),
            "motionAttackSeconds" to motionAttackSeconds.toString(),
            "motionReleaseSeconds" to motionReleaseSeconds.toString(),
            "horizontalGain" to horizontalGain.toString(),
            "maxHorizontalTranslationPx" to maxHorizontalTranslationPx.toString(),
            "horizontalDeadZoneRadians" to horizontalDeadZoneRadians.toString(),
            "horizontalCompensationDirection" to horizontalCompensationDirection.toString(),
            "maxOverscanScale" to maxOverscanScale.toString(),

            "maxVerticalTranslationPx" to maxVerticalTranslationPx.toString(),
            "deadZoneRadians" to deadZoneRadians.toString(),
            "referenceTimeConstantSeconds" to referenceTimeConstantSeconds.toString(),
            "smoothingTimeConstantSeconds" to smoothingTimeConstantSeconds.toString(),
            "returnTimeConstantSeconds" to returnTimeConstantSeconds.toString(),
            "pixelsPerRadian" to pixelsPerRadian.toString(),
            "compensationDirection" to compensationDirection.toString(),
            "overscanScale" to overscanScale.toString(),
            "sensorTimeoutSeconds" to sensorTimeoutSeconds.toString(),
            "sensorSamplingPeriodUs" to sensorSamplingPeriodUs.toString(),
            "debugIntervalNanos" to debugIntervalNanos.toString(),
        )
    }

    fun decode(values: Map<String, *>, readingText: String): ReadingSettings {
        val defaults = StabilizationConfig()
        fun value(key: String) = values[key] as? String
        // Retain full Double precision; SharedPreferences has no native Double type.
        // If a stored config violates its invariants, recover defaults rather than fail startup.
        val config = try {
            StabilizationConfig(
                predictionEnabled = value("predictionEnabled")?.toBooleanStrictOrNull() ?: defaults.predictionEnabled,
                predictionHorizonSeconds = value("predictionHorizonSeconds")?.toDoubleOrNull() ?: defaults.predictionHorizonSeconds,
                maxPredictionHorizonSeconds = value("maxPredictionHorizonSeconds")?.toDoubleOrNull() ?: defaults.maxPredictionHorizonSeconds,
                predictionGyroTimeoutSeconds = value("predictionGyroTimeoutSeconds")?.toDoubleOrNull() ?: defaults.predictionGyroTimeoutSeconds,
                maxPredictionAngularVelocity = value("maxPredictionAngularVelocity")?.toDoubleOrNull() ?: defaults.maxPredictionAngularVelocity,
                mode = value("mode")?.let { StabilizationMode.valueOf(it) } ?: defaults.mode,
                gyroNoiseFloor = value("gyroNoiseFloor")?.toDoubleOrNull() ?: defaults.gyroNoiseFloor,
                gyroFullScale = value("gyroFullScale")?.toDoubleOrNull() ?: defaults.gyroFullScale,
                gyroShakeWeight = value("gyroShakeWeight")?.toDoubleOrNull() ?: defaults.gyroShakeWeight,
                accelerationShakeWeight = value("accelerationShakeWeight")?.toDoubleOrNull() ?: defaults.accelerationShakeWeight,
                adaptiveMinMultiplier = value("adaptiveMinMultiplier")?.toDoubleOrNull() ?: defaults.adaptiveMinMultiplier,
                adaptiveMaxMultiplier = value("adaptiveMaxMultiplier")?.toDoubleOrNull() ?: defaults.adaptiveMaxMultiplier,
                adaptiveAttackSeconds = value("adaptiveAttackSeconds")?.toDoubleOrNull() ?: defaults.adaptiveAttackSeconds,
                adaptiveReleaseSeconds = value("adaptiveReleaseSeconds")?.toDoubleOrNull() ?: defaults.adaptiveReleaseSeconds,
                accelerationNoiseFloor = value("accelerationNoiseFloor")?.toDoubleOrNull() ?: defaults.accelerationNoiseFloor,
                accelerationFullScale = value("accelerationFullScale")?.toDoubleOrNull() ?: defaults.accelerationFullScale,
                motionAttackSeconds = value("motionAttackSeconds")?.toDoubleOrNull() ?: defaults.motionAttackSeconds,
                motionReleaseSeconds = value("motionReleaseSeconds")?.toDoubleOrNull() ?: defaults.motionReleaseSeconds,
                horizontalGain = value("horizontalGain")?.toFloatOrNull() ?: defaults.horizontalGain,
                maxHorizontalTranslationPx = value("maxHorizontalTranslationPx")?.toFloatOrNull() ?: defaults.maxHorizontalTranslationPx,
                horizontalDeadZoneRadians = value("horizontalDeadZoneRadians")?.toDoubleOrNull() ?: defaults.horizontalDeadZoneRadians,
                horizontalCompensationDirection = value("horizontalCompensationDirection")?.toFloatOrNull() ?: defaults.horizontalCompensationDirection,
                maxOverscanScale = value("maxOverscanScale")?.toFloatOrNull() ?: defaults.maxOverscanScale,
                gain = value("gain")?.toFloatOrNull() ?: defaults.gain,
                maxVerticalTranslationPx = value("maxVerticalTranslationPx")?.toFloatOrNull()
                    ?: defaults.maxVerticalTranslationPx,
                deadZoneRadians = value("deadZoneRadians")?.toDoubleOrNull() ?: defaults.deadZoneRadians,
                referenceTimeConstantSeconds = value("referenceTimeConstantSeconds")?.toDoubleOrNull()
                    ?: defaults.referenceTimeConstantSeconds,
                smoothingTimeConstantSeconds = value("smoothingTimeConstantSeconds")?.toDoubleOrNull()
                    ?: defaults.smoothingTimeConstantSeconds,
                returnTimeConstantSeconds = value("returnTimeConstantSeconds")?.toDoubleOrNull()
                    ?: defaults.returnTimeConstantSeconds,
                pixelsPerRadian = value("pixelsPerRadian")?.toFloatOrNull() ?: defaults.pixelsPerRadian,
                compensationDirection = value("compensationDirection")?.toFloatOrNull()
                    ?: defaults.compensationDirection,
                overscanScale = value("overscanScale")?.toFloatOrNull() ?: defaults.overscanScale,
                sensorTimeoutSeconds = value("sensorTimeoutSeconds")?.toDoubleOrNull()
                    ?: defaults.sensorTimeoutSeconds,
                sensorSamplingPeriodUs = value("sensorSamplingPeriodUs")?.toIntOrNull()
                    ?: defaults.sensorSamplingPeriodUs,
                debugIntervalNanos = value("debugIntervalNanos")?.toLongOrNull() ?: defaults.debugIntervalNanos,
            )
        } catch (_: IllegalArgumentException) {
            defaults
        }
        return ReadingSettings(
            enabled = value("enabled")?.toBooleanStrictOrNull() ?: true,
            config = config,
            showDebug = value("showDebug")?.toBooleanStrictOrNull() ?: true,
            readingText = readingText,
        )
    }
}
