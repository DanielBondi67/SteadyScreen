package com.steadyscreen.settings

import com.steadyscreen.stabilization.StabilizationConfig
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Portable, versioned format. Imports validate every value instead of using preference fallback. */
internal object ProfileJson {
    const val MaxImportBytes = 32_768
    private const val Format = "steadyscreen-tuning-profile"
    private const val Version = 1

    private val units = linkedMapOf(
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
        require(root.get("version") == Version) { "Unsupported profile library version." }
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
        require(root.get("version") == Version) { "Unsupported profile version." }
        val suppliedUnits = root.getJSONObject("units")
        require(units.all { (key, value) -> suppliedUnits.opt(key) == value }) { "Profile units do not match this format." }
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
        val parsed = StabilizationConfig(
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
