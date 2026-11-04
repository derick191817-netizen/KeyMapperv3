package com.example.keymapper.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlBehaviorPolicyTest {
    @Test
    fun rapidTapAndSwipeDurationsAreBounded() {
        assertEquals(40, ControlBehaviorPolicy.rapidIntervalMs(-10))
        assertEquals(120, ControlBehaviorPolicy.rapidIntervalMs(120))
        assertEquals(2_000, ControlBehaviorPolicy.rapidIntervalMs(9_000))
        assertEquals(50, ControlBehaviorPolicy.swipeDurationMs(0))
        assertEquals(180, ControlBehaviorPolicy.swipeDurationMs(180))
        assertEquals(2_000, ControlBehaviorPolicy.swipeDurationMs(9_000))
    }

    @Test
    fun swipeProgressAndInterpolationAreDeterministic() {
        assertEquals(0f, ControlBehaviorPolicy.swipeProgress(0, 200), 0f)
        assertEquals(0.5f, ControlBehaviorPolicy.swipeProgress(100, 200), 0.0001f)
        assertEquals(1f, ControlBehaviorPolicy.swipeProgress(500, 200), 0f)
        assertEquals(0f, ControlBehaviorPolicy.swipeProgress(-1, 200), 0f)

        val halfway = ControlBehaviorPolicy.interpolateSwipe(
            ControlBehaviorPolicy.Point(100f, 200f),
            ControlBehaviorPolicy.Point(300f, 400f),
            0.5f
        )
        assertEquals(200f, halfway.x, 0f)
        assertEquals(300f, halfway.y, 0f)
        val invalid = ControlBehaviorPolicy.interpolateSwipe(
            ControlBehaviorPolicy.Point(10f, 20f),
            ControlBehaviorPolicy.Point(30f, 40f),
            Float.NaN
        )
        assertEquals(10f, invalid.x, 0f)
        assertEquals(20f, invalid.y, 0f)
    }
}
