package com.steadyscreen.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.steadyscreen.render.FrameTiming
import com.steadyscreen.sensor.AndroidSensorProvider
import com.steadyscreen.stabilization.StabilizationConfig
import com.steadyscreen.stabilization.StabilizationEngine
import com.steadyscreen.stabilization.StabilizationTransform

data class DebugInfo(
    val pitchDegrees: Double = 0.0,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val rawY: Float = 0f,
    val finalY: Float = 0f,
    val renderHz: Double = 0.0,
    val orientationHz: Double = 0.0,
    val status: String = "Waiting for sensors",
)

/** UI bridge: sensor events only touch plain engine state; Compose publication follows frames. */
class ReadingController(context: Context, displayRotation: () -> Int) {
    val engine = StabilizationEngine()
    private val sensors = AndroidSensorProvider(context.applicationContext,
        engine.config.sensorSamplingPeriodUs,
        onGyroscope = { time, x, y, z -> engine.onGyroscope(time, x.toDouble(), y.toDouble(), z.toDouble()) },
        onAcceleration = { time, x, y, z -> engine.onAcceleration(time, x.toDouble(), y.toDouble(), z.toDouble()) },
    ) { time, q ->
        engine.onOrientation(time, q, displayRotation())
    }
    private val frameTiming = FrameTiming()
    private var lastDebugNanos = 0L
    var transform by mutableStateOf(StabilizationTransform())
        private set
    var debug by mutableStateOf(DebugInfo())
        private set

    fun configure(enabled: Boolean, config: StabilizationConfig) {
        if (engine.enabled != enabled || engine.config != config) lastDebugNanos = 0L
        engine.enabled = enabled
        engine.config = config
        sensors.setSamplingPeriod(config.sensorSamplingPeriodUs)
    }

    fun start() {
        engine.reset()
        frameTiming.reset()
        lastDebugNanos = 0L
        sensors.start()
    }

    fun stop() {
        sensors.stop()
        transform = StabilizationTransform()
    }

    fun onFrame(frameNanos: Long) {
        // SensorEvent.timestamp uses elapsedRealtimeNanos; Compose's frame clock need not.
        val now = SystemClock.elapsedRealtimeNanos()
        if (!frameTiming.accept(frameNanos, now)) return
        val gyroFresh = sensors.gyroTimestampNanos > 0 && now >= sensors.gyroTimestampNanos &&
            (now - sensors.gyroTimestampNanos) * 1e-9 <= engine.config.sensorTimeoutSeconds
        val orientationFresh = engine.hasFreshOrientation(now)
        val available = sensors.running && gyroFresh && orientationFresh
        transform = engine.frame(now, available)
        if (now - lastDebugNanos >= engine.config.debugIntervalNanos) {
            lastDebugNanos = now
            debug = DebugInfo(
                pitchDegrees = Math.toDegrees(engine.relativePitchRadians),
                gyroX = sensors.gyroX, gyroY = sensors.gyroY, gyroZ = sensors.gyroZ,
                rawY = engine.rawTranslationY, finalY = transform.translationY,
                renderHz = frameTiming.framesPerSecond,
                orientationHz = if (orientationFresh) engine.orientationHz else 0.0,
                status = when {
                    !sensors.running -> sensors.status
                    !orientationFresh -> "Waiting for game rotation vector / stream stale"
                    !gyroFresh -> "Waiting for gyroscope / stream stale"
                    else -> "Both sensors active"
                },
            )
        }
    }
}
