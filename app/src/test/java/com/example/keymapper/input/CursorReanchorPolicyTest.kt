package com.example.keymapper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorReanchorPolicyTest {
    @Test fun movementInsideDisplayDoesNotReanchor() {
        val p = CursorReanchorPolicy.advance(500f, 300f, 20f, -10f, 500f, 300f, 0f, 1000f, 0f, 600f)!!
        assertEquals(520f, p.x, 0f)
        assertEquals(290f, p.y, 0f)
    }

    @Test fun crossingRightEdgeReappearsAtAnchorWithExcess() {
        val p = CursorReanchorPolicy.advance(950f, 300f, 100f, 0f, 500f, 300f, 0f, 1000f, 0f, 600f)!!
        assertEquals(550f, p.x, 0f)
        assertEquals(300f, p.y, 0f)
    }

    @Test fun crossingLeftEdgeReappearsAtAnchorWithExcess() {
        val p = CursorReanchorPolicy.advance(50f, 300f, -100f, 0f, 500f, 300f, 0f, 1000f, 0f, 600f)!!
        assertEquals(450f, p.x, 0f)
    }

    @Test fun manyScreenWidthsAreHandledInConstantTime() {
        val p = CursorReanchorPolicy.advance(500f, 300f, 1_000_500f, 0f, 500f, 300f, 0f, 1000f, 0f, 600f)!!
        assertEquals(1000f, p.x, 0f)
        assertTrue(p.x in 0f..1000f)
    }

    @Test fun repeatedMovementsContinueFromAnchorAfterCrossingEdges() {
        val first = CursorReanchorPolicy.advance(950f, 300f, 100f, 0f, 500f, 300f, 0f, 1000f, 0f, 600f)!!
        val second = CursorReanchorPolicy.advance(first.x, first.y, 100f, 0f, 500f, 300f, 0f, 1000f, 0f, 600f)!!
        val third = CursorReanchorPolicy.advance(second.x, second.y, -700f, 0f, 500f, 300f, 0f, 1000f, 0f, 600f)!!
        assertEquals(550f, first.x, 0f)
        assertEquals(650f, second.x, 0f)
        assertEquals(450f, third.x, 0f)
    }

    @Test fun advanceIntoUpdatesReticleWithoutAllocatingPositionObject() {
        val out = FloatArray(2)
        val accepted = CursorReanchorPolicy.advanceInto(
            950f, 300f, 100f, 0f, 500f, 300f,
            0f, 1000f, 0f, 600f, out
        )
        assertTrue(accepted)
        assertEquals(550f, out[0], 0f)
        assertEquals(300f, out[1], 0f)
    }

    @Test fun exactEdgeArrivalDoesNotWrapEarly() {
        val right = CursorReanchorPolicy.advance(500f, 300f, 500f, 0f, 500f, 300f, 0f, 1000f, 0f, 600f)!!
        val left = CursorReanchorPolicy.advance(500f, 300f, -500f, 0f, 500f, 300f, 0f, 1000f, 0f, 600f)!!
        assertEquals(1000f, right.x, 0f)
        assertEquals(0f, left.x, 0f)
    }

    @Test fun axesReanchorIndependently() {
        val p = CursorReanchorPolicy.advance(950f, 580f, 100f, 10f, 500f, 300f, 0f, 1000f, 0f, 600f)!!
        assertEquals(550f, p.x, 0f)
        assertEquals(590f, p.y, 0f)
    }

    @Test fun invalidInputIsRejected() {
        assertNull(CursorReanchorPolicy.advance(Float.NaN, 1f, 1f, 1f, 0f, 0f, 0f, 100f, 0f, 100f))
        assertNull(CursorReanchorPolicy.advance(1f, 1f, 1f, 1f, 0f, 0f, 10f, 10f, 0f, 100f))
    }
}
