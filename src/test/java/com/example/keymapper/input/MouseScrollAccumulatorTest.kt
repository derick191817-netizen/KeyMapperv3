package com.example.keymapper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MouseScrollAccumulatorTest {
    @Test fun fractionsFromDifferentMiceNeverCompleteEachOther() {
        val accumulator = MouseScrollAccumulator()
        val out = IntArray(2)
        assertFalse(accumulator.consumeInto(10, 0f, 0.5f, out))
        assertFalse(accumulator.consumeInto(20, 0f, 0.5f, out))
        assertEquals(2, accumulator.trackedDeviceCount())
        assertTrue(accumulator.consumeInto(10, 0f, 0.5f, out))
        assertEquals(1, out[0])
        assertEquals(0, out[1])
        assertTrue(accumulator.consumeInto(20, 0f, 0.5f, out))
        assertEquals(1, out[0])
        assertEquals(0, accumulator.trackedDeviceCount())
    }

    @Test fun removingOneMouseClearsOnlyItsFractionalScroll() {
        val accumulator = MouseScrollAccumulator()
        val out = IntArray(2)
        accumulator.consumeInto(10, 0f, 0.4f, out)
        accumulator.consumeInto(20, 0f, 0.4f, out)
        accumulator.resetDevice(10)
        assertFalse(accumulator.consumeInto(10, 0f, 0.6f, out))
        assertTrue(accumulator.consumeInto(20, 0f, 0.6f, out))
        assertEquals(1, out[0])
        assertEquals(1, accumulator.trackedDeviceCount())
    }

    @Test fun verticalAndHorizontalDirectionsAreIndependent() {
        val accumulator = MouseScrollAccumulator()
        val out = IntArray(2)
        assertTrue(accumulator.consumeInto(1, 1.25f, -2.5f, out))
        assertEquals(-2, out[0])
        assertEquals(1, out[1])
        assertFalse(accumulator.consumeInto(1, 0f, 0f, out))
    }

    @Test fun perEventStepLimitLeavesRemainderForNextScrollSample() {
        val accumulator = MouseScrollAccumulator(maxStepsPerAxisPerEvent = 2)
        val out = IntArray(2)
        assertTrue(accumulator.consumeInto(1, 0f, 5f, out))
        assertEquals(2, out[0])
        assertTrue(accumulator.consumeInto(1, 0f, 0.25f, out))
        assertEquals(2, out[0])
        assertTrue(accumulator.consumeInto(1, 0f, 0f + 0.1f, out))
        assertEquals(1, out[0])
    }

    @Test fun invalidInputDoesNotCreateDeviceState() {
        val accumulator = MouseScrollAccumulator()
        val out = IntArray(2)
        assertFalse(accumulator.consumeInto(7, Float.NaN, 1f, out))
        assertFalse(accumulator.consumeInto(7, 0f, Float.POSITIVE_INFINITY, out))
        assertEquals(0, accumulator.trackedDeviceCount())
        accumulator.consumeInto(7, 0f, 0.25f, out)
        accumulator.reset()
        assertEquals(0, accumulator.trackedDeviceCount())
    }
}
