package com.steadyscreen.settings

import android.content.SharedPreferences
import com.steadyscreen.stabilization.StabilizationConfig
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingSettingsStoreTest {
    private val changedSettings = ReadingSettings(
        enabled = false,
        config = StabilizationConfig(
            gain = 1.3f, maxVerticalTranslationPx = 125f, deadZoneRadians = 0.003123456789,
            referenceTimeConstantSeconds = 0.8, smoothingTimeConstantSeconds = 0.0,
            returnTimeConstantSeconds = 0.2, pixelsPerRadian = 2300f,
            compensationDirection = 1f, overscanScale = 1.2f,
            sensorTimeoutSeconds = 0.6, sensorSamplingPeriodUs = 10_000,
            debugIntervalNanos = 500_000_000L,
        ),
        showDebug = false,
        readingText = "A paragraph with punctuation: <>&\"\n\nGrüße 🐝 日本語\n".repeat(1500),
    )

    @Test fun firstLaunchUsesDefaults() {
        assertEquals(ReadingSettings(), ReadingSettingsStore(PreferenceFile().open(), PreferenceFile().open()).load())
    }

    @Test fun newStoreRestoresEverySettingAndLongUnicodeText() {
        val preferences = PreferenceFile()
        val text = PreferenceFile()
        ReadingSettingsStore(preferences.open(), text.open()).save(changedSettings)

        // A new store and preference handles must restore persisted values, not UI or store memory.
        assertEquals(changedSettings, ReadingSettingsStore(preferences.open(), text.open()).load())
    }

    @Test fun resetTuningPersistsWithoutChangingTextOrSwitches() {
        val preferences = PreferenceFile()
        val text = PreferenceFile()
        val store = ReadingSettingsStore(preferences.open(), text.open())
        store.save(changedSettings)
        val reset = store.load().copy(config = StabilizationConfig())
        store.save(reset)

        assertEquals(reset, ReadingSettingsStore(preferences.open(), text.open()).load())
        assertEquals(1, text.applyCount)
    }

    @Test fun originalSampleSelectionPersists() {
        val preferences = PreferenceFile()
        val text = PreferenceFile()
        val store = ReadingSettingsStore(preferences.open(), text.open())
        store.save(changedSettings)
        store.save(store.load().copy(readingText = ""))

        assertEquals(changedSettings.copy(readingText = ""),
            ReadingSettingsStore(preferences.open(), text.open()).load())
    }

    @Test fun sliderChangesDoNotRewriteReadingMaterialOrUnchangedPreferences() {
        val preferences = PreferenceFile()
        val text = PreferenceFile()
        val store = ReadingSettingsStore(preferences.open(), text.open())
        store.save(changedSettings)
        store.save(changedSettings)
        assertEquals(1, preferences.applyCount)
        repeat(20) {
            store.save(changedSettings.copy(config = changedSettings.config.copy(gain = it / 20f)))
        }
        assertEquals(1, text.applyCount)
        val restored = ReadingSettingsStore(preferences.open(), text.open()).load()
        assertEquals(0.95f, restored.config.gain, 0f)
        assertEquals(changedSettings.readingText, restored.readingText)
    }

    @Test fun missingAndMalformedValuesUseDefaults() {
        val preferences = PreferenceFile(mapOf(
            "gain" to "not a number", "enabled" to "not a boolean", "overscanScale" to 42,
            "showDebug" to "false", "unknownFutureSetting" to "keep loading",
        ))
        val text = PreferenceFile(mapOf("text" to 42))
        assertEquals(ReadingSettings(showDebug = false),
            ReadingSettingsStore(preferences.open(), text.open()).load())
    }

    @Test fun invalidConfigDoesNotCrashOrLoseReadingMaterialAndSwitches() {
        for ((key, value) in mapOf("gain" to "NaN", "returnTimeConstantSeconds" to "0",
            "sensorSamplingPeriodUs" to "-1", "debugIntervalNanos" to "0")) {
            val preferences = PreferenceFile(SettingsCodec.encode(changedSettings) + (key to value))
            val text = PreferenceFile(mapOf("text" to changedSettings.readingText))
            assertEquals(changedSettings.copy(config = StabilizationConfig()),
                ReadingSettingsStore(preferences.open(), text.open()).load())
        }
    }

    /** In-memory preference file for JVM tests; changes become visible only through Editor.apply(). */
    private class PreferenceFile(initial: Map<String, Any?> = emptyMap()) {
        private val saved = initial.toMutableMap()
        var applyCount = 0
            private set

        fun open(): SharedPreferences = proxy(SharedPreferences::class.java) { _, method, _ ->
            when (method) {
                "getAll" -> saved.toMap()
                "edit" -> editor()
                else -> error("Unexpected preference method: $method")
            }
        }

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            return proxy(SharedPreferences.Editor::class.java) { editor, method, args ->
                when (method) {
                    "putString" -> {
                        pending[args!![0] as String] = args[1]
                        editor
                    }
                    "apply" -> {
                        saved.putAll(pending)
                        applyCount++
                        null
                    }
                    else -> error("Unexpected editor method: $method")
                }
            }
        }

        private fun <T : Any> proxy(type: Class<T>, call: (Any, String, Array<out Any?>?) -> Any?): T =
            checkNotNull(type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
                call(proxy, method.name, args)
            }))
    }
}
