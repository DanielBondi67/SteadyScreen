package com.steadyscreen.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.steadyscreen.render.OverscanGeometry
import com.steadyscreen.render.FrameTiming
import com.steadyscreen.sensor.AndroidSensorProvider
import com.steadyscreen.stabilization.StabilizationConfig
import com.steadyscreen.stabilization.StabilizationEngine
import com.steadyscreen.stabilization.StabilizationTransform

data class DebugInfo(
    val config: StabilizationConfig = StabilizationConfig(),
    val horizontalDegrees: Double = 0.0,
    val accelerationX: Float = 0f,
    val accelerationY: Float = 0f,
    val accelerationZ: Float = 0f,
    val accelerationMagnitude: Double = 0.0,
    val bumpIntensity: Double = 0.0,
    val rotationalShake: Double = 0.0,
    val shakeScore: Double = 0.0,
    val adaptiveMultiplier: Double = 1.0,
    val effectiveVerticalGain: Double = 0.0,
    val effectiveHorizontalGain: Double = 0.0,
    val predictionHorizonSeconds: Double = 0.0,
    val overscan: OverscanGeometry = OverscanGeometry(1.08f, 0f, 0f),
    val rawX: Float = 0f,
    val finalX: Float = 0f,
    val displayHz: Float = 0f,
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
class ReadingController(context: Context, private val displayRefreshRate: () -> Float = { 0f }, displayRotation: () -> Int) {
    val engine = StabilizationEngine()
    private val sensors = AndroidSensorProvider(context.applicationContext,
        engine.config.sensorSamplingPeriodUs,
        onGyroscope = { time, x, y, z -> engine.onGyroscope(time, x.toDouble(), y.toDouble(), z.toDouble()) },
        onAcceleration = { time, x, y, z -> engine.onAcceleration(time, x.toDouble(), y.toDouble(), z.toDouble()) },
    ) { time, q ->
        engine.onOrientation(time, q, displayRotation())
    }
    private var viewportWidth = 0
    private var viewportHeight = 0
    var overscan by mutableStateOf(OverscanGeometry.forViewport(0, 0, engine.config))
        private set

    fun setViewport(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        overscan = OverscanGeometry.forViewport(width, height, engine.config)
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
        setViewport(viewportWidth, viewportHeight)
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
        transform = overscan.constrain(engine.frame(now, available, frameTiming.periodSeconds))
        if (now - lastDebugNanos >= engine.config.debugIntervalNanos) {
            lastDebugNanos = now
            val accelFresh = sensors.accelerationTimestampNanos > 0 && now >= sensors.accelerationTimestampNanos &&
                (now - sensors.accelerationTimestampNanos) * 1e-9 <= engine.config.sensorTimeoutSeconds
            debug = DebugInfo(
                config = engine.config,
                horizontalDegrees = Math.toDegrees(engine.relativeHorizontalRadians),
                accelerationX = sensors.accelerationX, accelerationY = sensors.accelerationY,
                accelerationZ = sensors.accelerationZ, accelerationMagnitude = engine.bump.magnitude,
                bumpIntensity = engine.bumpIntensity, rotationalShake = engine.rotationalShake,
                shakeScore = engine.shakeScore, adaptiveMultiplier = engine.adaptiveMultiplier,
                effectiveVerticalGain = engine.effectiveVerticalGain, effectiveHorizontalGain = engine.effectiveHorizontalGain,
                predictionHorizonSeconds = engine.effectivePredictionHorizonSeconds,
                overscan = overscan, rawX = engine.rawTranslationX, finalX = transform.translationX,
                displayHz = displayRefreshRate(),
                pitchDegrees = Math.toDegrees(engine.relativePitchRadians),
                gyroX = sensors.gyroX, gyroY = sensors.gyroY, gyroZ = sensors.gyroZ,
                rawY = engine.rawTranslationY, finalY = transform.translationY,
                renderHz = frameTiming.framesPerSecond,
                orientationHz = if (orientationFresh) engine.orientationHz else 0.0,
                status = when {
                    !sensors.running -> sensors.status
                    !orientationFresh -> "Waiting for game rotation vector / stream stale"
                    !gyroFresh -> "Waiting for gyroscope / stream stale"
                    !sensors.accelerationAvailable -> "Orientation + gyro active; no linear acceleration"
                    !accelFresh -> "Orientation + gyro active; linear acceleration stale"
                    else -> "All three sensors active"
                },
            )
        }
    }
}
