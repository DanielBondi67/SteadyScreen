package com.steadyscreen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.steadyscreen.stabilization.StabilizationConfig
import com.steadyscreen.stabilization.StabilizationMode
import com.steadyscreen.render.StabilizedContent
import com.steadyscreen.settings.ReadingSettings
import com.steadyscreen.settings.ReadingSettingsStore
import java.util.Locale
import kotlinx.coroutines.isActive

@Composable
fun ReadingTestScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val controller = remember(context, view) { ReadingController(context, { view.display?.refreshRate ?: 0f }) { view.display?.rotation ?: 0 } }
    val settingsStore = remember(context) { ReadingSettingsStore(context) }
    var settings by remember(settingsStore) { mutableStateOf(settingsStore.load()) }
    val updateSettings: (ReadingSettings) -> Unit = {
        settingsStore.saveUserChoices(it)
        settings = settingsStore.load()
    }
    DisposableEffect(settingsStore) {
        val unsubscribe = settingsStore.observeEnabled { settings = settings.copy(enabled = it) }
        onDispose { unsubscribe() }
    }
    val enabled = settings.enabled
    val config = settings.config
    val showDebug = settings.showDebug
    val customText = settings.readingText
    var showTuning by rememberSaveable { mutableStateOf(false) }
    var showTextEditor by rememberSaveable { mutableStateOf(false) }
    // Read settings during composition so changes invalidate this scope, even when
    // the controls live in BoxWithConstraints' separate subcomposition.
    val currentEnabled = enabled
    val currentConfig = config
    SideEffect { controller.configure(currentEnabled, currentConfig) }
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(controller) {
        controller.start()
        resumed = true
        onPauseOrDispose { resumed = false; controller.stop() }
    }
    LaunchedEffect(controller, resumed) {
        if (resumed) while (isActive) withFrameNanos { controller.onFrame(it) }
    }
    if (showTuning) {
        TuningDialog(config, { updateSettings(settings.copy(config = it)) }, { showTuning = false })
    }
    if (showTextEditor) {
        ReadingTextDialog(customText, onApply = {
            updateSettings(settings.copy(readingText = it))
            showTextEditor = false
        }, onDismiss = { showTextEditor = false })
    }

    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(300.dp).verticalScroll(rememberScrollState())) {
                        Controls(enabled, config,
                            { settingsStore.setEnabled(it) },
                            { updateSettings(settings.copy(config = it)) },
                            onTuning = { showTuning = true }, onText = { showTextEditor = true })
                        DebugPanel(controller, enabled, showDebug) {
                            updateSettings(settings.copy(showDebug = !settings.showDebug))
                        }
                    }
                    ReadingText(controller, customText, Modifier.weight(1f).fillMaxSize())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Controls(enabled, config,
                        { settingsStore.setEnabled(it) },
                        { updateSettings(settings.copy(config = it)) },
                        onTuning = { showTuning = true }, onText = { showTextEditor = true })
                    HorizontalDivider()
                    ReadingText(controller, customText, Modifier.weight(1f).fillMaxWidth())
                    HorizontalDivider()
                    DebugPanel(controller, enabled, showDebug) {
                        updateSettings(settings.copy(showDebug = !settings.showDebug))
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(
    enabled: Boolean,
    config: StabilizationConfig,
    onEnabled: (Boolean) -> Unit,
    onConfig: (StabilizationConfig) -> Unit,
    onTuning: () -> Unit,
    onText: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("SteadyScreen", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (enabled) "Stabilization ON" else "Stabilization OFF")
            Switch(checked = enabled, onCheckedChange = onEnabled,
                modifier = Modifier.semantics { contentDescription = "Stabilization" })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onConfig(config.copy(mode = if (config.mode == StabilizationMode.Manual)
                StabilizationMode.Adaptive else StabilizationMode.Manual)) }) { Text("Mode: ${config.mode}") }
            Text("Prediction", style = MaterialTheme.typography.labelLarge)
            Switch(config.predictionEnabled, { onConfig(config.copy(predictionEnabled = it)) },
                Modifier.semantics { contentDescription = "Prediction" })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(String.format(Locale.US, "Vertical %.2f", config.gain), style = MaterialTheme.typography.labelLarge)
                Slider(value = config.gain, onValueChange = { onConfig(config.copy(gain = it)) }, valueRange = 0f..2f,
                    modifier = Modifier.semantics { contentDescription = "Vertical gain" })
            }
            Column(Modifier.weight(1f)) {
                Text(String.format(Locale.US, "Horizontal %.2f", config.horizontalGain), style = MaterialTheme.typography.labelLarge)
                Slider(value = config.horizontalGain, onValueChange = { onConfig(config.copy(horizontalGain = it)) }, valueRange = 0f..2f,
                    modifier = Modifier.semantics { contentDescription = "Horizontal gain" })
            }
        }
        Row {
            TextButton(onClick = onTuning) { Text("Tune settings") }
            TextButton(onClick = onText) { Text("Reading text") }
        }
    }
}

@Composable
private fun DebugPanel(controller: ReadingController, enabled: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    // This is the only composition scope that reads diagnostics, published at 5 Hz.
    val info = controller.debug
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        TextButton(onClick = onToggle) { Text(if (expanded) "Hide diagnostics" else "Show diagnostics") }
        Text(info.status, style = MaterialTheme.typography.labelSmall)
        if (expanded) {
            Text(
                String.format(Locale.US,
                    "%s • %s • Pitch %+.3f° • Horizontal %+.3f°\n" +
                    "Gyro XYZ %+.3f %+.3f %+.3f rad/s\n" +
                    "Accel XYZ %+.2f %+.2f %+.2f m/s² • |a| %.2f\n" +
                    "Bump %.3f • Rotational %.3f • Shake %.3f\n" +
                    "Multiplier %.2f • Effective gain V %.2f / H %.2f\n" +
                    "Raw X %+.1f / Y %+.1f px\nFinal X %+.1f / Y %+.1f px\n" +
                    "RV %.0f Hz • Render %.1f FPS • Display %.0f Hz\n" +
                    "Prediction %s • lead %.1f / effective %.1f ms\n" +
                    "Overscan base %.2f / actual %.2f×\nVisible limits X ±%.1f / Y ±%.1f px",
                    if (enabled) "ON" else "OFF", info.config.mode, info.pitchDegrees, info.horizontalDegrees,
                    info.gyroX, info.gyroY, info.gyroZ, info.accelerationX, info.accelerationY, info.accelerationZ,
                    info.accelerationMagnitude, info.bumpIntensity, info.rotationalShake, info.shakeScore,
                    info.adaptiveMultiplier, info.effectiveVerticalGain, info.effectiveHorizontalGain,
                    info.rawX, info.rawY, info.finalX, info.finalY, info.orientationHz, info.renderHz, info.displayHz,
                    if (!info.config.predictionEnabled) "OFF" else if (info.predictionHorizonSeconds > 0) "active" else "idle/stale",
                    info.config.predictionHorizonSeconds * 1000, info.predictionHorizonSeconds * 1000,
                    info.config.overscanScale, info.overscan.scale, info.overscan.limitX, info.overscan.limitY),
                modifier = Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ReadingText(controller: ReadingController, customText: String, modifier: Modifier) {
    val paragraphs = remember(customText) { customText.split(Regex("\\r?\\n\\s*\\r?\\n")) }
    StabilizedContent(transform = { controller.transform }, overscanScale = controller.overscan.scale,
        modifier = modifier.onSizeChanged { controller.setViewport(it.width, it.height) }) {
        if (customText.isNotEmpty()) {
            // Replacing the document starts at its beginning; frame transforms never lay it out again.
            key(customText) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    item { Text("Your reading text", style = MaterialTheme.typography.headlineSmall) }
                    items(paragraphs) { ReadingParagraph(it) }
                    item { ReadingHint() }
                }
            }
        } else {
            OriginalReadingSample()
        }
    }
}

@Composable
private fun OriginalReadingSample() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 32.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("A quieter journey", style = MaterialTheme.typography.headlineSmall)
        ReadingParagraph("The train left the station just as the morning light reached the rooftops. " +
            "Beyond the window, gardens gave way to fields, and the city slowly disappeared. " +
            "Mara opened her book at the folded corner and began the same paragraph again.")
        ReadingParagraph("There was no hurry in the story. A traveler had arrived in a small town " +
            "beside a river, carrying a notebook and a map that was several years out of date. " +
            "The bridge marked on the map was gone, but a footpath followed the water toward the hills.")
        ReadingParagraph("At the first bend, the traveler stopped to listen. Leaves moved above the " +
            "path, a bicycle bell sounded in the distance, and water passed quietly over the stones. " +
            "Every detail seemed ordinary until there was time to notice it.")
        ReadingParagraph("Mara looked up as the carriage crossed a set of points. The cup on the " +
            "opposite table trembled, then settled. She found her place once more and followed " +
            "the traveler along the river, one sentence at a time.")
        ReadingParagraph("By noon, the path reached a wooden gate. Someone had repaired its hinge " +
            "with a strip of bright new metal. On the other side stood an orchard, a low stone " +
            "wall, and a house with an open door. The map could offer no more advice.")
        ReadingParagraph("The traveler closed the notebook. For a moment, reaching the right place " +
            "seemed less important than paying attention to the place already underfoot. " +
            "The journey would continue after a short rest in the shade.")
        ReadingHint()
    }
}

@Composable
private fun ReadingHint() {
    Text("Compare the same paragraph with stabilization ON and OFF. Test only as a passenger.",
        style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ReadingParagraph(text: String) {
    Text(text, fontSize = 19.sp, lineHeight = 29.sp, fontFamily = FontFamily.Serif)
}
