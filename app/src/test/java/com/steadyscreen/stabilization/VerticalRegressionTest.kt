package com.steadyscreen.stabilization

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class VerticalRegressionTest {
    @Test fun validatedVerticalTrace() {
        val engine = StabilizationEngine()
        var time = 1_000_000_000L
        // Captured from the physically validated engine at f836859, before MVP 2 edits.
        val checkpoints = mapOf(0 to 0f, 1 to -7.7347927f, 5 to -25.084734f,
            20 to -28.825289f, 49 to -20.742037f, 50 to -20.502924f,
            65 to -18.543474f, 100 to -15.735466f, 149 to -13.260554f,
            150 to 0f, 160 to 35.48027f, 180 to 10.16527f, 200 to 2.912399f)
        repeat(201) { i ->
            time += 5_000_000L
            val angle = when { i == 0 -> 0.0; i < 50 -> 0.06; i < 150 -> 0.06 + (i - 50) * 0.0002; else -> -0.02 }
            engine.onOrientation(time, Quaternion(cos(angle / 2), sin(angle / 2), 0.0, 0.0))
            if (i == 160) engine.enabled = false
            val y = engine.frame(time).translationY
            checkpoints[i]?.let { assertEquals("sample $i", it, y, 0.00001f) }
        }
    }
}
