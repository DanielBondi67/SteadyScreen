package com.steadyscreen.render

import kotlin.math.exp

/** Frame-clock timestamps measure cadence only; sensor age uses elapsed realtime separately. */
class FrameTiming {
    private var lastFrame: Long? = null
    private var lastElapsed: Long? = null
    var periodSeconds = 0.0
        private set
    val framesPerSecond get() = if (periodSeconds > 0.0) 1.0 / periodSeconds else 0.0

    fun accept(frameNanos: Long, elapsedNanos: Long): Boolean {
        if (frameNanos < 0 || elapsedNanos < 0) return false
        val previous = lastFrame
        val elapsed = lastElapsed
        if ((previous != null && frameNanos <= previous) || (elapsed != null && elapsedNanos <= elapsed)) return false
        if (previous != null) {
            val dt = (frameNanos - previous) * 1e-9
            periodSeconds = if (periodSeconds == 0.0) dt else
                periodSeconds + (1 - exp(-dt / CadenceSmoothingSeconds)) * (dt - periodSeconds)
        }
        lastFrame = frameNanos
        lastElapsed = elapsedNanos
        return true
    }

    fun reset() { lastFrame = null; lastElapsed = null; periodSeconds = 0.0 }

    companion object {
        // Diagnostic smoothing only; never schedules frames or assumes a display frequency.
        private const val CadenceSmoothingSeconds = 0.1
    }
}
