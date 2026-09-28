package com.steadyscreen.render

import org.junit.Assert.*
import org.junit.Test

class FrameTimingTest {
    @Test fun active60And120HzAreMeasuredWithoutAClockEpochAssumption() {
        for (step in listOf(16_666_667L, 8_333_333L)) {
            val t = FrameTiming()
            repeat(200) { assertTrue(t.accept(1_000_000_000L + it * step, 90_000_000_000L + it * step)) }
            assertEquals(1e9 / step, t.framesPerSecond, 0.001)
        }
    }
    @Test fun refreshTransitionsConvergeInBothDirections() {
        val t = FrameTiming()
        var now = 1_000_000_000L
        for (hz in listOf(60, 120, 60)) {
            repeat(hz) { now += 1_000_000_000L / hz; assertTrue(t.accept(now, now + 500_000_000)) }
            assertEquals(hz.toDouble(), t.framesPerSecond, 0.02)
        }
    }
    @Test fun duplicatesReversalsAndInvalidTimesDoNotPublish() {
        val t = FrameTiming()
        assertFalse(t.accept(-1, 1))
        assertTrue(t.accept(100, 1000))
        assertFalse(t.accept(100, 1001))
        assertFalse(t.accept(99, 1002))
        assertFalse(t.accept(101, 999))
        assertTrue(t.accept(200, 1100))
        assertEquals(1e7, t.framesPerSecond, 0.001)
        t.reset()
        assertEquals(0.0, t.framesPerSecond, 0.0)
        assertTrue(t.accept(1, 1))
    }
}
