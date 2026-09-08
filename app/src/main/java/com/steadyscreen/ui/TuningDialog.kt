package com.steadyscreen.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.steadyscreen.stabilization.StabilizationConfig
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
internal fun TuningDialog(
    config: StabilizationConfig,
    onConfig: (StabilizationConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stabilization tuning") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TuningProfiles(config, onConfig)
                Text("Changes apply live and save automatically on this device. Reset defaults restores tuning values.")
                Mvp2TuningControls(config, onConfig)
                TuningSlider("Vertical gain", config.gain, 0f..2f, "%.2f") { onConfig(config.copy(gain = it)) }
                TuningSlider("Vertical clamp", config.maxVerticalTranslationPx, 1f..200f, "±%.0f px") {
                    onConfig(config.copy(maxVerticalTranslationPx = it))
                }
                Text("Gain cannot increase motion beyond the clamp.", style = MaterialTheme.typography.bodySmall)
                TuningSlider("Pixel conversion", config.pixelsPerRadian, 100f..5000f, "%.0f px/rad") {
                    onConfig(config.copy(pixelsPerRadian = it))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Reverse direction (+1)")
                    Switch(config.compensationDirection > 0f,
                        { onConfig(config.copy(compensationDirection = if (it) 1f else -1f)) },
                        Modifier.semantics { contentDescription = "Reverse compensation direction" })
                }
                TuningSlider("Dead zone", config.deadZoneRadians.toFloat(), 0f..0.02f, "%.4f rad") {
                    onConfig(config.copy(deadZoneRadians = it.toDouble()))
                }
                TuningSlider("Reference follow time", config.referenceTimeConstantSeconds.toFloat(),
                    0.05f..2f, "%.3f s") { onConfig(config.copy(referenceTimeConstantSeconds = it.toDouble())) }
                TuningSlider("Smoothing time", config.smoothingTimeConstantSeconds.toFloat(),
                    0f..0.2f, "%.3f s") { onConfig(config.copy(smoothingTimeConstantSeconds = it.toDouble())) }
                TuningSlider("Return to zero time", config.returnTimeConstantSeconds.toFloat(),
                    0.01f..0.5f, "%.3f s") { onConfig(config.copy(returnTimeConstantSeconds = it.toDouble())) }
                TuningSlider("Overscan scale", config.overscanScale, 1f..1.3f, "%.2f×") {
                    onConfig(config.copy(overscanScale = it))
                }
                TuningSlider("Sensor timeout", config.sensorTimeoutSeconds.toFloat(),
                    0.05f..1f, "%.3f s") { onConfig(config.copy(sensorTimeoutSeconds = it.toDouble())) }
                TuningSlider("Sensor sampling period", config.sensorSamplingPeriodUs / 1000f,
                    5f..10f, "%.0f ms", steps = 4) {
                    onConfig(config.copy(sensorSamplingPeriodUs = it.roundToInt() * 1000))
                }
                Text("Requests 100–200 Hz. Changing the period restarts active sensors.",
                    style = MaterialTheme.typography.bodySmall)
                TuningSlider("Diagnostics interval", config.debugIntervalNanos / 1_000_000f,
                    100f..1000f, "%.0f ms") {
                    onConfig(config.copy(debugIntervalNanos = it.roundToLong() * 1_000_000L))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = { onConfig(StabilizationConfig()) }) { Text("Reset defaults") }
        },
    )
}

@Composable
internal fun TuningSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    steps: Int = 0,
    onValue: (Float) -> Unit,
) {
    Text("$label: ${String.format(Locale.US, format, value)}", style = MaterialTheme.typography.labelLarge)
    Slider(value = value, onValueChange = onValue, valueRange = range, steps = steps,
        modifier = Modifier.semantics { contentDescription = label })
}
