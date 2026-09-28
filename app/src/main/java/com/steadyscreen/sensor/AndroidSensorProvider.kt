package com.steadyscreen.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import com.steadyscreen.stabilization.Quaternion

/** Main-thread callbacks serialize acquisition with controls and frame reads; no queues or logging. */
class AndroidSensorProvider(
    context: Context,
    samplingPeriodUs: Int,
    private val onAcceleration: (Long, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    private val onGyroscope: (Long, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    private val onOrientation: (Long, Quaternion) -> Unit,
) : SensorEventListener {
    private var samplingPeriodUs = samplingPeriodUs
    private val manager = context.getSystemService(SensorManager::class.java)
    private val rotationSensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val accelerationSensor = manager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroSensor = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val handler = Handler(Looper.getMainLooper())
    private val quaternion = FloatArray(4)

    var accelerationAvailable = false
        private set
    var accelerationX = 0f
        private set
    var accelerationY = 0f
        private set
    var accelerationZ = 0f
        private set
    var accelerationTimestampNanos = 0L
        private set

    var running = false
        private set
    var status = "Sensors stopped"
        private set
    var gyroX = 0f
        private set
    var gyroY = 0f
        private set
    var gyroZ = 0f
        private set
    var gyroTimestampNanos = 0L
        private set

    fun setSamplingPeriod(periodUs: Int) {
        require(periodUs >= 5_000)
        if (periodUs == samplingPeriodUs) return
        samplingPeriodUs = periodUs
        // Registration captures the requested period; changing a field alone has no effect.
        if (running) {
            stop()
            start()
        }
    }

    fun start() {
        if (running) return
        if (manager == null || rotationSensor == null || gyroSensor == null) {
            status = "Unavailable: game rotation vector and gyroscope are both required"
            return
        }
        try {
            val rotationRegistered = manager.registerListener(this, rotationSensor, samplingPeriodUs, 0, handler)
            val gyroRegistered = manager.registerListener(this, gyroSensor, samplingPeriodUs, 0, handler)
            running = rotationRegistered && gyroRegistered
            accelerationAvailable = running && accelerationSensor?.let {
                manager.registerListener(this, it, samplingPeriodUs, 0, handler)
            } == true
            if (!running) manager.unregisterListener(this)
            status = if (running) "Waiting for sensor samples" else "Sensor registration failed"
        } catch (_: SecurityException) {
            manager.unregisterListener(this)
            running = false
            status = "Sensor access unavailable"
        }
    }

    fun stop() {
        running = false
        manager?.unregisterListener(this)
        accelerationAvailable = false
        accelerationTimestampNanos = 0L
        accelerationX = 0f
        accelerationY = 0f
        accelerationZ = 0f
        gyroTimestampNanos = 0L
        gyroX = 0f
        gyroY = 0f
        gyroZ = 0f
        status = "Sensors stopped"
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getQuaternionFromVector(quaternion, event.values)
                onOrientation(event.timestamp, Quaternion(quaternion[0].toDouble(),
                    quaternion[1].toDouble(), quaternion[2].toDouble(), quaternion[3].toDouble()))
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                if (event.timestamp <= accelerationTimestampNanos ||
                    !event.values[0].isFinite() || !event.values[1].isFinite() || !event.values[2].isFinite()) return
                accelerationX = event.values[0]
                accelerationY = event.values[1]
                accelerationZ = event.values[2]
                accelerationTimestampNanos = event.timestamp
                onAcceleration(event.timestamp, accelerationX, accelerationY, accelerationZ)
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (event.timestamp <= gyroTimestampNanos || !event.values[0].isFinite() ||
                    !event.values[1].isFinite() || !event.values[2].isFinite()) return
                gyroX = event.values[0]
                gyroY = event.values[1]
                gyroZ = event.values[2]
                gyroTimestampNanos = event.timestamp
                onGyroscope(event.timestamp, gyroX, gyroY, gyroZ)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
