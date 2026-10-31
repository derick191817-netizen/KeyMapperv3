package com.example.keymapper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualCursorControllerTest {
    @Test fun movementAccumulatesAndKeepsAxesIndependent() {
        val cursor = VirtualCursorController(100.0, 200.0)
        val motion = cursor.advance(2.5f, -4f)!!
        assertEquals(2.5, motion.deltaX, 0.0)
        assertEquals(-4.0, motion.deltaY, 0.0)
        assertEquals(102.5, motion.position.x, 0.0)
        assertEquals(196.0, motion.position.y, 0.0)
    }

    @Test fun positionIsNotWrappedAtScreenOrArbitraryCanvasEdges() {
        val cursor = VirtualCursorController(49_990.0, -49_990.0)
        val p = cursor.moveBy(300_010f, -250_010f)!!
        assertEquals(350_000.0, p.x, 0.0)
        assertEquals(-300_000.0, p.y, 0.0)
    }

    @Test fun longSessionCanAccumulateManyFullTurnsWithoutWrapping() {
        val cursor = VirtualCursorController()
        repeat(10_000) { cursor.moveBy(360f, 0f) }
        assertEquals(3_600_000.0, cursor.position().x, 0.0)
        assertEquals(0.0, cursor.position().y, 0.0)
    }

    @Test fun extremeValuesRemainFiniteAndBoundedForIntegrity() {
        val cursor = VirtualCursorController()
        val p = cursor.moveBy(Float.MAX_VALUE, Float.MAX_VALUE)!!
        assertTrue(p.x.isFinite() && p.y.isFinite())
        assertTrue(p.x <= 1_000_000_000_000.0 && p.y <= 1_000_000_000_000.0)
    }

    @Test fun invalidAndZeroDeltasDoNotMoveCursor() {
        val cursor = VirtualCursorController(10.0, 20.0)
        assertNull(cursor.moveBy(0f, 0f))
        assertNull(cursor.moveBy(Float.NaN, 1f))
        assertNull(cursor.moveBy(1f, Float.POSITIVE_INFINITY))
        assertEquals(VirtualCursorController.Position(10.0, 20.0), cursor.position())
    }

    @Test fun setAndResetKeepUnboundedLogicalCoordinates() {
        val cursor = VirtualCursorController(1.0, 2.0)
        cursor.set(75_000.0, -75_000.0)
        assertEquals(VirtualCursorController.Position(75_000.0, -75_000.0), cursor.position())
        cursor.set(Double.NaN, 0.0)
        assertEquals(VirtualCursorController.Position(75_000.0, -75_000.0), cursor.position())
        cursor.reset(3.0, 4.0)
        assertEquals(VirtualCursorController.Position(3.0, 4.0), cursor.position())
    }

    @Test fun invalidResetIsIgnored() {
        val cursor = VirtualCursorController(3.0, 4.0)
        cursor.reset(Double.NaN, Double.POSITIVE_INFINITY)
        assertEquals(VirtualCursorController.Position(3.0, 4.0), cursor.position())
    }
}
