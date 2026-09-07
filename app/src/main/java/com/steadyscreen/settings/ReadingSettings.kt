package com.steadyscreen.settings

import com.steadyscreen.stabilization.StabilizationConfig

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
