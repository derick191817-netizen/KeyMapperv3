package com.example.keymapper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MouseDeltaPolicyTest {
    @Test fun capturedPointerUsesRelativeXyEvenWhenOneAxisIsExactlyZero() {
        val result = MouseDeltaPolicy.select(
            captured = true,
            pointerX = 12.5f,
            pointerY = 0f,
            relativeAxisX = 12.5f,
            relativeAxisY = 900f // must not replace the zero Y delta
        )
        assertEquals(12.5f, result?.x ?: Float.NaN, 0f)
        assertEquals(0f, result?.y ?: Float.NaN, 0f)
    }

    @Test fun capturedPointerDoesNotFallbackToAxisValues() {
        val result = MouseDeltaPolicy.select(
            captured = true,
            pointerX = 0f,
            pointerY = 0f,
            relativeAxisX = 300f,
            relativeAxisY = 200f
        )
        assertNull(result)
    }

    @Test fun uncapturedMouseUsesOnlyExplicitRelativeAxes() {
        val result = MouseDeltaPolicy.select(
            captured = false,
            pointerX = 800f, // absolute pointer coordinate must be ignored
            pointerY = 450f,
            relativeAxisX = -3f,
            relativeAxisY = 2f
        )
        assertEquals(-3f, result?.x ?: Float.NaN, 0f)
        assertEquals(2f, result?.y ?: Float.NaN, 0f)
    }

    @Test fun uncapturedAbsoluteCoordinatesDoNotBecomeCameraMotion() {
        assertNull(MouseDeltaPolicy.select(true.not(), 700f, 420f, 0f, 0f))
    }

    @Test fun reusableBufferSelectsDeltasWithoutAllocatingResultObjects() {
        val buffer = floatArrayOf(99f, 99f)
        val accepted = MouseDeltaPolicy.selectInto(
            captured = true,
            pointerX = 0f,
            pointerY = -7f,
            relativeAxisX = 500f,
            relativeAxisY = 500f,
            out = buffer
        )
        assertEquals(true, accepted)
        assertEquals(0f, buffer[0], 0f)
        assertEquals(-7f, buffer[1], 0f)

        val rejected = MouseDeltaPolicy.selectInto(false, 300f, 200f, 0f, 0f, buffer)
        assertEquals(false, rejected)
    }

    @Test fun nonFiniteDeltasAreRejected() {
        assertNull(MouseDeltaPolicy.select(true, Float.NaN, 0f, 0f, 0f))
        assertNull(MouseDeltaPolicy.select(false, 0f, 0f, 1f, Float.POSITIVE_INFINITY))
    }
}
