package com.example.keymapper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualCursorControllerTest {
    @Test fun movementAccumulatesAndKeepsAxesIndependent() {
        val cursor = VirtualCursorController(100f, 200f)
        val p = cursor.moveBy(2.5f, -4f)
        assertNotNull(p)
        assertEquals(102.5f, p!!.x, 0f)
        assertEquals(196f, p.y, 0f)
    }

    @Test fun multipleWrapsAreHandledForVeryLargeFiniteDeltas() {
        val cursor = VirtualCursorController(0f, 0f)
        val p = cursor.moveBy(300_010f, -250_010f)!!
        assertEquals(10f, p.x, 0f)
        assertEquals(49_990f, p.y, 0f)
        assertTrue(p.x >= -50_000f && p.x < 50_000f)
        assertTrue(p.y >= -50_000f && p.y < 50_000f)
    }

    @Test fun floatMaxDoesNotOverflowLogicalPosition() {
        val cursor = VirtualCursorController(0f, 0f)
        val p = cursor.moveBy(Float.MAX_VALUE, Float.MAX_VALUE)!!
        assertTrue(p.x.isFinite() && p.y.isFinite())
        assertTrue(p.x >= -50_000f && p.x < 50_000f)
        assertTrue(p.y >= -50_000f && p.y < 50_000f)
    }

    @Test fun invalidAndZeroDeltasDoNotMoveCursor() {
        val cursor = VirtualCursorController(10f, 20f)
        assertNull(cursor.moveBy(0f, 0f))
        assertNull(cursor.moveBy(Float.NaN, 1f))
        assertNull(cursor.moveBy(1f, Float.POSITIVE_INFINITY))
        assertEquals(VirtualCursorController.Position(10f, 20f), cursor.position())
    }

    @Test fun setAndResetNormalizeStateAndIgnoreInvalidSet() {
        val cursor = VirtualCursorController(1f, 2f)
        cursor.set(75_000f, -75_000f)
        assertEquals(VirtualCursorController.Position(-25_000f, 25_000f), cursor.position())
        cursor.set(Float.NaN, 0f)
        assertEquals(VirtualCursorController.Position(-25_000f, 25_000f), cursor.position())
        cursor.reset(3f, 4f)
        assertEquals(VirtualCursorController.Position(3f, 4f), cursor.position())
    }

    @Test fun movementAcrossLogicalEdgesDoesNotClampOrLoseDelta() {
        val cursor = VirtualCursorController(49_990f, -49_990f)
        val p = cursor.moveBy(25f, -25f)!!
        assertEquals(-49_985f, p.x, 0f)
        assertEquals(49_985f, p.y, 0f)
    }

    @Test fun rapidDirectionChangesRemainAdditive() {
        val cursor = VirtualCursorController(0f, 0f)
        cursor.moveBy(125f, -80f)
        cursor.moveBy(-300f, 180f)
        val p = cursor.moveBy(175f, -100f)!!
        assertEquals(0f, p.x, 0f)
        assertEquals(0f, p.y, 0f)
    }

}
