package com.steadyscreen.render

import com.steadyscreen.stabilization.StabilizationConfig
import com.steadyscreen.stabilization.StabilizationTransform
import org.junit.Assert.*
import org.junit.Test

class OverscanGeometryTest {
    @Test fun bothAxesAreCoveredAtClampInPortraitAndLandscape() {
        for ((w, h) in listOf(1080 to 1200, 1600 to 600, 800 to 150, 0 to 0)) {
            val g = OverscanGeometry.forViewport(w, h, StabilizationConfig())
            assertTrue(g.scale in 1.08f..1.3f)
            for (sign in listOf(-1, 1)) {
                val t = g.constrain(StabilizationTransform(sign * 60f, sign * 80f))
                assertTrue(kotlin.math.abs(t.translationX) <= w * (g.scale - 1f) / 2f + 0.0001f)
                assertTrue(kotlin.math.abs(t.translationY) <= h * (g.scale - 1f) / 2f + 0.0001f)
            }
        }
    }
    @Test fun normalViewportPreservesFullEngineClampsAndScaleIsIndependentOfMotion() {
        val g = OverscanGeometry.forViewport(1080, 1200, StabilizationConfig())
        assertEquals(60f, g.limitX, 0.0001f)
        assertEquals(80f, g.limitY, 0.0001f)
        val scale = g.scale
        repeat(100) { g.constrain(StabilizationTransform(it.toFloat(), -it.toFloat())) }
        assertEquals(scale, g.scale, 0f)
    }
}
