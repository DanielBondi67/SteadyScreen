package com.steadyscreen.settings

import com.steadyscreen.stabilization.StabilizationConfig
import com.steadyscreen.stabilization.StabilizationMode
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Portable, versioned format. Imports validate every value instead of using preference fallback. */
internal object ProfileJson {
    const val MaxImportBytes = 32_768
    private const val Format = "steadyscreen-tuning-profile"
    private const val Version = 2

    private val legacyUnits = linkedMapOf(
        "gain" to "multiplier",
        "maxVerticalTranslationPx" to "px",
        "deadZoneRadians" to "rad",
        "referenceTimeConstantSeconds" to "s",
        "smoothingTimeConstantSeconds" to "s",
        "returnTimeConstantSeconds" to "s",
        "pixelsPerRadian" to "px/rad",
        "compensationDirection" to "sign (-1 or +1)",
        "overscanScale" to "multiplier",
        "sensorTimeoutSeconds" to "s",
        "sensorSamplingPeriodUs" to "µs",
        "debugIntervalNanos" to "ns",
    )

    private val units = legacyUnits + linkedMapOf(
        "predictionEnabled" to "boolean",
        "predictionHorizonSeconds" to "s",
        "maxPredictionHorizonSeconds" to "s",
        "predictionGyroTimeoutSeconds" to "s",
        "maxPredictionAngularVelocity" to "rad/s",
        "mode" to "Manual or Adaptive",
        "gyroNoiseFloor" to "rad/s",
        "gyroFullScale" to "rad/s",
        "gyroShakeWeight" to "multiplier",
        "accelerationShakeWeight" to "multiplier",
        "adaptiveMinMultiplier" to "multiplier",
        "adaptiveMaxMultiplier" to "multiplier",
        "adaptiveAttackSeconds" to "s",
        "adaptiveReleaseSeconds" to "s",
        "accelerationNoiseFloor" to "m/s²",
        "accelerationFullScale" to "m/s²",
        "motionAttackSeconds" to "s",
        "motionReleaseSeconds" to "s",
        "horizontalGain" to "multiplier",
        "maxHorizontalTranslationPx" to "px",
        "horizontalDeadZoneRadians" to "rad",
        "horizontalCompensationDirection" to "sign (-1 or +1)",
        "maxOverscanScale" to "multiplier",
    )

    fun export(profile: TuningProfile): String = document(profile).toString(2) + "\n"

    fun import(text: String): TuningProfile {
        require(text.toByteArray(Charsets.UTF_8).size <= MaxImportBytes) { "Profile file is too large (32 KiB maximum)." }
        return parseDocument(parseObject(text))
    }

    fun encodeLibrary(library: ProfileLibrary): String = JSONObject().apply {
        put("version", Version)
        put("selectedId", library.selectedId ?: JSONObject.NULL)
        put("profiles", JSONArray().apply {
            library.profiles.forEach { profile ->
                put(document(profile).put("id", profile.id))
            }
        })
    }.toString()

    fun decodeLibrary(text: String): ProfileLibrary {
        val root = parseObject(text)
        require(root.get("version") in listOf(1, Version)) { "Unsupported profile library version." }
        val profiles = root.getJSONArray("profiles")
        require(profiles.length() <= 100) { "Too many saved profiles." }
        return ProfileLibrary(
            profiles = (0 until profiles.length()).map {
                val item = profiles.getJSONObject(it)
                parseDocument(item, enforceControlRanges = false).copy(id = string(item, "id"))
            },
            selectedId = if (root.isNull("selectedId")) null else string(root, "selectedId"),
        )
    }

    private fun document(profile: TuningProfile) = JSONObject().apply {
        put("format", Format)
        put("version", Version)
        put("name", profile.name)
        put("notes", profile.notes)
        put("units", JSONObject(units as Map<*, *>))
        put("config", JSONObject().apply {
            SettingsCodec.encode(ReadingSettings(config = profile.config)).forEach { (key, value) ->
                if (key in units) {
                    when (key) {
                        "predictionEnabled" -> put(key, value.toBooleanStrict())
                        "mode" -> put(key, value)
                        "sensorSamplingPeriodUs" -> put(key, value.toInt())
                        "debugIntervalNanos" -> put(key, value.toLong())
                        else -> put(key, value.toDouble())
                    }
                }
            }
        })
    }

    private fun parseObject(text: String): JSONObject {
        val parser = JSONTokener(text)
        val root = parser.nextValue()
        require(root is JSONObject && parser.nextClean() == '\u0000') { "Expected one JSON object." }
        return root
    }

    private fun parseDocument(root: JSONObject, enforceControlRanges: Boolean = true): TuningProfile {
        require(root.get("format") == Format) { "This is not a SteadyScreen tuning profile." }
        require(root.get("version") in listOf(1, Version)) { "Unsupported profile version." }
        val legacy = root.get("version") == 1
        val suppliedUnits = root.getJSONObject("units")
        require((if (legacy) legacyUnits else units).all { (key, value) -> suppliedUnits.opt(key) == value }) { "Profile units do not match this format." }
        val config = root.getJSONObject("config")
        fun number(key: String): Double {
            val value = config.get(key)
            require(value is Number && value.toDouble().isFinite()) { "$key must be a finite number." }
            return value.toDouble()
        }
        fun whole(key: String): Long {
            val value = config.get(key)
            // Preserve nanosecond integers without routing them through a lossy Double conversion.
            require(value is Number) { "$key must be a whole number." }
            return value.toString().toLongOrNull()
                ?: throw IllegalArgumentException("$key must be a whole number within range.")
        }
        val sampling = whole("sensorSamplingPeriodUs")
        require(sampling in 5_000..Int.MAX_VALUE.toLong()) { "Sensor sampling period is outside the allowed range." }
        require(number("compensationDirection") in listOf(-1.0, 1.0)) { "Compensation direction must be -1 or +1." }
        val defaults = StabilizationConfig(horizontalGain = 0f)
        require(legacy || number("horizontalCompensationDirection") in listOf(-1.0, 1.0)) {
            "Horizontal direction must be -1 or +1."
        }
        val parsed = StabilizationConfig(
            predictionEnabled = if (legacy) defaults.predictionEnabled else config.get("predictionEnabled").let {
                require(it is Boolean) { "predictionEnabled must be boolean." }
                it
            },
            predictionHorizonSeconds = if (legacy) defaults.predictionHorizonSeconds else number("predictionHorizonSeconds"),
            maxPredictionHorizonSeconds = if (legacy) defaults.maxPredictionHorizonSeconds else number("maxPredictionHorizonSeconds"),
            predictionGyroTimeoutSeconds = if (legacy) defaults.predictionGyroTimeoutSeconds else number("predictionGyroTimeoutSeconds"),
            maxPredictionAngularVelocity = if (legacy) defaults.maxPredictionAngularVelocity else number("maxPredictionAngularVelocity"),
            mode = if (legacy) defaults.mode else StabilizationMode.valueOf(string(config, "mode")),
            gyroNoiseFloor = if (legacy) defaults.gyroNoiseFloor else number("gyroNoiseFloor"),
            gyroFullScale = if (legacy) defaults.gyroFullScale else number("gyroFullScale"),
            gyroShakeWeight = if (legacy) defaults.gyroShakeWeight else number("gyroShakeWeight"),
            accelerationShakeWeight = if (legacy) defaults.accelerationShakeWeight else number("accelerationShakeWeight"),
            adaptiveMinMultiplier = if (legacy) defaults.adaptiveMinMultiplier else number("adaptiveMinMultiplier"),
            adaptiveMaxMultiplier = if (legacy) defaults.adaptiveMaxMultiplier else number("adaptiveMaxMultiplier"),
            adaptiveAttackSeconds = if (legacy) defaults.adaptiveAttackSeconds else number("adaptiveAttackSeconds"),
            adaptiveReleaseSeconds = if (legacy) defaults.adaptiveReleaseSeconds else number("adaptiveReleaseSeconds"),
            accelerationNoiseFloor = if (legacy) defaults.accelerationNoiseFloor else number("accelerationNoiseFloor"),
            accelerationFullScale = if (legacy) defaults.accelerationFullScale else number("accelerationFullScale"),
            motionAttackSeconds = if (legacy) defaults.motionAttackSeconds else number("motionAttackSeconds"),
            motionReleaseSeconds = if (legacy) defaults.motionReleaseSeconds else number("motionReleaseSeconds"),
            horizontalGain = if (legacy) defaults.horizontalGain else number("horizontalGain").toFloat(),
            maxHorizontalTranslationPx = if (legacy) defaults.maxHorizontalTranslationPx else number("maxHorizontalTranslationPx").toFloat(),
            horizontalDeadZoneRadians = if (legacy) defaults.horizontalDeadZoneRadians else number("horizontalDeadZoneRadians"),
            horizontalCompensationDirection = if (legacy) defaults.horizontalCompensationDirection else number("horizontalCompensationDirection").toFloat(),
            maxOverscanScale = if (legacy) defaults.maxOverscanScale else number("maxOverscanScale").toFloat(),
            gain = number("gain").toFloat(),
            maxVerticalTranslationPx = number("maxVerticalTranslationPx").toFloat(),
            deadZoneRadians = number("deadZoneRadians"),
            referenceTimeConstantSeconds = number("referenceTimeConstantSeconds"),
            smoothingTimeConstantSeconds = number("smoothingTimeConstantSeconds"),
            returnTimeConstantSeconds = number("returnTimeConstantSeconds"),
            pixelsPerRadian = number("pixelsPerRadian").toFloat(),
            compensationDirection = number("compensationDirection").toFloat(),
            overscanScale = number("overscanScale").toFloat(),
            sensorTimeoutSeconds = number("sensorTimeoutSeconds"),
            sensorSamplingPeriodUs = sampling.toInt(),
            debugIntervalNanos = whole("debugIntervalNanos"),
        )
        // Match Float slider endpoints when checking imported values against the UI ranges.
        require(!enforceControlRanges || (parsed.gain in 0f..2f && parsed.maxVerticalTranslationPx in 1f..200f &&
            parsed.horizontalGain in 0f..2f && parsed.maxHorizontalTranslationPx in 1f..200f &&
            parsed.horizontalDeadZoneRadians.toFloat() in 0f..0.02f &&
            parsed.accelerationNoiseFloor.toFloat() in 0f..1f && parsed.accelerationFullScale.toFloat() in 1.01f..10f &&
            parsed.gyroNoiseFloor.toFloat() in 0f..0.2f && parsed.gyroFullScale.toFloat() in 0.21f..6f &&
            parsed.motionAttackSeconds.toFloat() in 0.005f..0.2f && parsed.motionReleaseSeconds.toFloat() in 0.05f..2f &&
            parsed.adaptiveMinMultiplier.toFloat() in 0f..4f &&
            parsed.adaptiveAttackSeconds.toFloat() in 0.01f..1f && parsed.adaptiveReleaseSeconds.toFloat() in 0.05f..3f &&
            parsed.predictionHorizonSeconds.toFloat() in 0f..0.05f &&
            parsed.predictionGyroTimeoutSeconds.toFloat() in 0.01f..0.25f &&
            parsed.maxPredictionAngularVelocity.toFloat() in 0.1f..20f &&
            parsed.deadZoneRadians.toFloat() in 0f..0.02f &&
            parsed.referenceTimeConstantSeconds.toFloat() in 0.05f..2f &&
            parsed.smoothingTimeConstantSeconds.toFloat() in 0f..0.2f &&
            parsed.returnTimeConstantSeconds.toFloat() in 0.01f..0.5f &&
            parsed.pixelsPerRadian in 100f..5000f && parsed.overscanScale in 1f..1.3f &&
            parsed.sensorTimeoutSeconds.toFloat() in 0.05f..1f &&
            parsed.sensorSamplingPeriodUs in 5_000..10_000 &&
            parsed.debugIntervalNanos in 100_000_000L..1_000_000_000L)) {
            "Profile values must be within the ranges shown in Tune settings."
        }
        return TuningProfile(name = string(root, "name"), notes = string(root, "notes"), config = parsed)
    }

    private fun string(root: JSONObject, key: String): String {
        val value = root.get(key)
        require(value is String) { "$key must be text." }
        return value
    }
}
