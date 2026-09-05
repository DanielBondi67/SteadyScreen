package com.steadyscreen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.steadyscreen.render.StabilizedContent
import com.steadyscreen.stabilization.StabilizationConfig
import java.util.Locale
import kotlinx.coroutines.isActive

@Composable
fun ReadingTestScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val defaults = remember { StabilizationConfig() }
    val controller = remember(context, view) { ReadingController(context) { view.display?.rotation ?: 0 } }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var gain by rememberSaveable { mutableFloatStateOf(defaults.gain) }
    var showDebug by rememberSaveable { mutableStateOf(true) }
    SideEffect { controller.configure(enabled, defaults.copy(gain = gain)) }
    LifecycleResumeEffect(controller) {
        controller.start()
        onPauseOrDispose { controller.stop() }
    }
    LaunchedEffect(controller) {
        while (isActive) withFrameNanos { controller.onFrame() }
    }

    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(300.dp).verticalScroll(rememberScrollState())) {
                        Controls(enabled, gain, { enabled = it }, { gain = it })
                        DebugPanel(controller, enabled, showDebug) { showDebug = !showDebug }
                    }
                    ReadingText(controller, defaults.overscanScale, Modifier.weight(1f).fillMaxSize())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Controls(enabled, gain, { enabled = it }, { gain = it })
                    HorizontalDivider()
                    ReadingText(controller, defaults.overscanScale, Modifier.weight(1f).fillMaxWidth())
                    HorizontalDivider()
                    DebugPanel(controller, enabled, showDebug) { showDebug = !showDebug }
                }
            }
        }
    }
}

@Composable
private fun Controls(enabled: Boolean, gain: Float, onEnabled: (Boolean) -> Unit, onGain: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("SteadyScreen", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (enabled) "Stabilization ON" else "Stabilization OFF")
            Switch(checked = enabled, onCheckedChange = onEnabled,
                modifier = Modifier.semantics { contentDescription = "Stabilization" })
        }
        Text(String.format(Locale.US, "Gain %.2f", gain), style = MaterialTheme.typography.labelLarge)
        Slider(value = gain, onValueChange = onGain, valueRange = 0f..2f,
            modifier = Modifier.semantics { contentDescription = "Stabilization gain" })
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
                    "%s  •  Pitch %+.3f°  •  RV %.0f Hz\nGyro XYZ: %+.3f  %+.3f  %+.3f rad/s\nVertical raw %+.1f px  →  final %+.1f px",
                    if (enabled) "ON" else "OFF", info.pitchDegrees, info.orientationHz,
                    info.gyroX, info.gyroY, info.gyroZ, info.rawY, info.finalY),
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ReadingText(controller: ReadingController, overscanScale: Float, modifier: Modifier) {
    StabilizedContent(transform = { controller.transform }, overscanScale = overscanScale, modifier = modifier) {
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
            Text("Compare the same paragraph with stabilization ON and OFF. Test only as a passenger.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReadingParagraph(text: String) {
    Text(text, fontSize = 19.sp, lineHeight = 29.sp, fontFamily = FontFamily.Serif)
}
