package com.steadyscreen.ui

import androidx.compose.runtime.saveable.SaverScope
import com.steadyscreen.stabilization.StabilizationConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class StabilizationConfigSaverTest {
    @Test fun allTuningValuesSurviveRecreation() {
        val config = StabilizationConfig(
            gain = 1.3f, maxVerticalTranslationPx = 125f, deadZoneRadians = 0.003,
            referenceTimeConstantSeconds = 0.8, smoothingTimeConstantSeconds = 0.0,
            returnTimeConstantSeconds = 0.2, pixelsPerRadian = 2300f,
            compensationDirection = 1f, overscanScale = 1.2f,
            sensorTimeoutSeconds = 0.6, sensorSamplingPeriodUs = 10_000,
            debugIntervalNanos = 500_000_000L,
        )
        val saved = with(StabilizationConfigSaver) { SaverScope { true }.save(config) }
        assertEquals(config, StabilizationConfigSaver.restore(checkNotNull(saved)))
    }
}
