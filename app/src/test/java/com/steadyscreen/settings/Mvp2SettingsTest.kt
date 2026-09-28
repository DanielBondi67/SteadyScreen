package com.steadyscreen.settings

import com.steadyscreen.stabilization.StabilizationConfig
import com.steadyscreen.stabilization.StabilizationMode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Mvp2SettingsTest {
    private val tuned = StabilizationConfig(
        horizontalGain = 0.73f, maxHorizontalTranslationPx = 43f, horizontalDeadZoneRadians = 0.0024,
        horizontalCompensationDirection = -1f, mode = StabilizationMode.Adaptive,
        accelerationNoiseFloor = 0.22, accelerationFullScale = 4.1, gyroNoiseFloor = 0.03, gyroFullScale = 1.2,
        gyroShakeWeight = 0.7, accelerationShakeWeight = 0.3, motionAttackSeconds = 0.02, motionReleaseSeconds = 0.31,
        adaptiveMinMultiplier = 0.3, adaptiveMaxMultiplier = 1.7, adaptiveAttackSeconds = 0.12, adaptiveReleaseSeconds = 0.8,
        predictionEnabled = true, predictionHorizonSeconds = 0.014, maxPredictionHorizonSeconds = 0.025,
        predictionGyroTimeoutSeconds = 0.06, maxPredictionAngularVelocity = 3.0, maxOverscanScale = 1.25f,
    )
    @Test fun everyNewValueSurvivesPreferencesLibraryAndJson() {
        val settings = ReadingSettings(config = tuned)
        assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings), ""))
        val profile = TuningProfile(name = "Adaptive ride", config = tuned)
        assertEquals(tuned, ProfileJson.import(ProfileJson.export(profile)).config)
        val library = ProfileLibrary().save(profile)
        assertEquals(library, ProfileJson.decodeLibrary(ProfileJson.encodeLibrary(library)))
    }
    @Test fun legacyDocumentsMigrateToVerticalManualWithoutLosingOldTuning() {
        val json = JSONObject(ProfileJson.export(TuningProfile(name = "Legacy", config = tuned)))
        val legacyKeys = setOf("gain", "maxVerticalTranslationPx", "deadZoneRadians", "referenceTimeConstantSeconds",
            "smoothingTimeConstantSeconds", "returnTimeConstantSeconds", "pixelsPerRadian", "compensationDirection",
            "overscanScale", "sensorTimeoutSeconds", "sensorSamplingPeriodUs", "debugIntervalNanos")
        for (section in listOf("units", "config")) {
            val o = json.getJSONObject(section)
            o.keys().asSequence().toList().filter { it !in legacyKeys }.forEach { o.remove(it) }
        }
        json.put("version", 1)
        val legacy = ProfileJson.import(json.toString())
        assertEquals(0f, legacy.config.horizontalGain, 0f)
        assertEquals(tuned.gain, legacy.config.gain, 0f)
        assertEquals(StabilizationMode.Manual, legacy.config.mode)
        assertFalse(legacy.config.predictionEnabled)
        json.put("id", "old")
        val library = JSONObject().put("version", 1).put("selectedId", "old")
            .put("profiles", org.json.JSONArray().put(json))
        assertEquals(legacy.config, ProfileJson.decodeLibrary(library.toString()).selected!!.config)
    }
    @Test fun documentedExamplesImportWithExpectedModes() {
        val old = ProfileJson.import(java.io.File("../docs/example-tuning-profile.json").readText())
        assertEquals(0f, old.config.horizontalGain, 0f)
        assertEquals(StabilizationMode.Manual, old.config.mode)
        val current = ProfileJson.import(java.io.File("../docs/example-mvp2-profile.json").readText())
        assertEquals(StabilizationConfig(), current.config)
    }

    @Test fun missingNewFieldsAndWrongUnitsAreRejected() {
        val original = ProfileJson.export(TuningProfile(name = "Test", config = tuned))
        for (section in listOf("units", "config")) {
            val json = JSONObject(original)
            json.getJSONObject(section).remove("predictionEnabled")
            assertThrows(Exception::class.java) { ProfileJson.import(json.toString()) }
        }
        val json = JSONObject(original)
        json.getJSONObject("units").put("gyroNoiseFloor", "degrees/s")
        assertThrows(Exception::class.java) { ProfileJson.import(json.toString()) }
    }

    @Test fun malformedNewValuesAreRejectedInsteadOfSilentlyResetting() {
        val profile = TuningProfile(name = "Test", config = tuned)
        for ((key, value) in listOf("mode" to "Auto", "predictionEnabled" to "true", "horizontalGain" to -1,
            "horizontalCompensationDirection" to 1.00000001, "adaptiveMaxMultiplier" to 0.1,
            "maxPredictionHorizonSeconds" to 100, "accelerationFullScale" to 0.01)) {
            val json = JSONObject(ProfileJson.export(profile))
            json.getJSONObject("config").put(key, value)
            assertThrows(Exception::class.java) { ProfileJson.import(json.toString()) }
        }
    }
}
