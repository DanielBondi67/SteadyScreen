package com.steadyscreen.settings

import com.steadyscreen.stabilization.StabilizationConfig
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TuningProfileTest {
    private val tuned = StabilizationConfig(
        gain = 1.3f, maxVerticalTranslationPx = 125f, deadZoneRadians = 0.003123456789,
        referenceTimeConstantSeconds = 0.8, smoothingTimeConstantSeconds = 0.0,
        returnTimeConstantSeconds = 0.2, pixelsPerRadian = 2300f, compensationDirection = 1f,
        overscanScale = 1.2f, sensorTimeoutSeconds = 0.6, sensorSamplingPeriodUs = 10_000,
        debugIntervalNanos = 500_000_001L,
    )
    private val profile = TuningProfile(name = "Bus 🐝", notes = "Window seat\nTest \"A\" — Grüße", config = tuned)

    @Test fun jsonRoundTripPreservesEveryParameterAndUnicodeNotes() {
        val json = ProfileJson.export(profile)
        val restored = ProfileJson.import(json)
        assertEquals(profile.name, restored.name)
        assertEquals(profile.notes, restored.notes)
        assertEquals(tuned, restored.config)
        assertNotEquals(profile.id, restored.id)
        assertTrue(json.contains("\n"))
        val root = JSONObject(json)
        assertEquals(12, root.getJSONObject("config").length())
        assertEquals(12, root.getJSONObject("units").length())
        assertFalse(root.has("readingText"))
        assertFalse(root.getJSONObject("config").has("enabled"))
    }

    @Test fun profileLibraryAndSelectionSurviveFreshStore() {
        val file = PreferenceFile()
        val second = TuningProfile(name = "Train", config = StabilizationConfig())
        val library = ProfileLibrary().save(profile).save(second).select(profile.id)
        ProfileStore(file.open()).save(library)
        assertEquals(library, ProfileStore(file.open()).load())
    }

    @Test fun updatingOneProfileDoesNotChangeOthers() {
        val second = TuningProfile(name = "Train", config = StabilizationConfig())
        val original = ProfileLibrary().save(profile).save(second)
        val updated = original.save(profile.copy(name = "Walking", notes = "New notes", config = tuned.copy(gain = 0.5f)))
        assertEquals(second, updated.profiles.last())
        assertEquals(2, updated.profiles.size)
        assertEquals("Walking", updated.selected?.name)
        assertEquals(profile, original.profiles.first())
    }

    @Test fun liveEditsAndDefaultResetDoNotOverwriteSavedSnapshot() {
        val library = ProfileLibrary().save(profile)
        var current = library.selected!!.config
        current = current.copy(gain = 0f)
        assertNotEquals(current, library.selected!!.config)
        current = StabilizationConfig()
        assertNotEquals(current, library.selected!!.config)
        assertEquals(tuned, library.selected!!.config)
    }

    @Test fun duplicateNamesAreRejectedWithoutOverwriting() {
        val library = ProfileLibrary().save(profile)
        assertThrows(IllegalArgumentException::class.java) {
            library.save(profile.copy(id = "another", name = profile.name.uppercase()))
        }
        assertEquals(listOf(profile), library.profiles)
    }

    @Test fun deletionClearsSelectionAndPersists() {
        val file = PreferenceFile()
        val library = ProfileLibrary().save(profile)
        val store = ProfileStore(file.open())
        store.save(library)
        store.save(library.delete(profile.id))
        assertEquals(ProfileLibrary(), ProfileStore(file.open()).load())
    }

    @Test fun exportImportsBothFloatSliderEndpoints() {
        val endpoints = listOf(
            tuned.copy(gain = 0f, deadZoneRadians = 0.0, referenceTimeConstantSeconds = 0.05f.toDouble(),
                smoothingTimeConstantSeconds = 0.0, returnTimeConstantSeconds = 0.01f.toDouble(),
                sensorTimeoutSeconds = 0.05f.toDouble()),
            tuned.copy(gain = 2f, deadZoneRadians = 0.02f.toDouble(), smoothingTimeConstantSeconds = 0.2f.toDouble(),
                overscanScale = 1.3f),
        )
        endpoints.forEach { config ->
            assertEquals(config, ProfileJson.import(ProfileJson.export(profile.copy(config = config))).config)
        }
    }

    @Test fun missingWrongTypeFractionalAndExtremeValuesAreRejected() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.remove("gain") },
            { it.put("gain", "1.2") },
            { it.put("gain", true) },
            { it.put("gain", JSONObject.NULL) },
            { it.put("gain", 1e100) },
            { it.put("gain", -1) },
            { it.put("gain", 3) },
            { it.put("returnTimeConstantSeconds", 0) },
            { it.put("compensationDirection", 1.00000001) },
            { it.put("sensorSamplingPeriodUs", 5000.5) },
            { it.put("sensorSamplingPeriodUs", 2147483648L) },
            { it.put("debugIntervalNanos", 500000000.5) },
        )
        mutations.forEach { mutate ->
            val root = JSONObject(ProfileJson.export(profile))
            mutate(root.getJSONObject("config"))
            assertThrows(Exception::class.java) { ProfileJson.import(root.toString()) }
        }
    }

    @Test fun wrongVersionFormatUnitsAndTrailingDataAreRejected() {
        val original = ProfileJson.export(profile)
        for ((key, value) in mapOf("version" to 2, "format" to "another-app", "name" to " ")) {
            assertThrows(Exception::class.java) { ProfileJson.import(JSONObject(original).put(key, value).toString()) }
        }
        val changedUnits = JSONObject(original)
        changedUnits.getJSONObject("units").put("deadZoneRadians", "degrees")
        assertThrows(Exception::class.java) { ProfileJson.import(changedUnits.toString()) }
        assertThrows(Exception::class.java) { ProfileJson.import(original + "{}") }
        assertThrows(Exception::class.java) { ProfileJson.import("[]") }
        assertThrows(Exception::class.java) { ProfileJson.import("{broken") }
    }

    @Test fun oversizedImportsAndNamesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ProfileJson.import(" ".repeat(ProfileJson.MaxImportBytes + 1)) }
        assertThrows(IllegalArgumentException::class.java) { profile.copy(name = "x".repeat(81)) }
        assertThrows(IllegalArgumentException::class.java) { profile.copy(notes = "x".repeat(2001)) }
    }

    @Test fun unreadableStoredLibraryIsReportedAndNotOverwrittenByLoading() {
        val file = PreferenceFile(mapOf("library" to "broken"))
        assertThrows(Exception::class.java) { ProfileStore(file.open()).load() }
        assertEquals("broken", file.open().all["library"])
        assertEquals(0, file.applyCount)
    }

    @Test fun freshLibraryIsEmptyAndProfilesHaveACap() {
        assertEquals(ProfileLibrary(), ProfileStore(PreferenceFile().open()).load())
        val profiles = (1..100).map { profile.copy(id = "$it", name = "Profile $it") }
        assertThrows(IllegalArgumentException::class.java) { ProfileLibrary(profiles).save(profile) }
    }
}
