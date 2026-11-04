package com.example.keymapper.injection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchOutputGeometryPolicyTest {
    @Test
    fun validGeometryNormalizesCornersAndCenter() {
        val topLeft = TouchOutputGeometryPolicy.normalize(0f, 0f, 1600, 900)
        val bottomRight = TouchOutputGeometryPolicy.normalize(1599f, 899f, 1600, 900)
        val center = TouchOutputGeometryPolicy.normalize(799.5f, 449.5f, 1600, 900)

        assertNotNull(topLeft)
        assertNotNull(bottomRight)
        assertNotNull(center)
        assertEquals(0f, topLeft!!.x, 0.001f)
        assertEquals(0f, topLeft.y, 0.001f)
        assertEquals(65535f, bottomRight!!.x, 0.001f)
        assertEquals(65535f, bottomRight.y, 0.001f)
        assertEquals(32767.5f, center!!.x, 1f)
        assertEquals(32767.5f, center.y, 1f)
    }

    @Test
    fun coordinatesAreClampedToPhysicalScreenBounds() {
        val point = TouchOutputGeometryPolicy.normalize(-50f, 1200f, 1600, 900)
        assertNotNull(point)
        assertEquals(0f, point!!.x, 0.001f)
        assertEquals(65535f, point.y, 0.001f)
    }

    @Test
    fun invalidGeometryIsRejectedInsteadOfCollapsingToOrigin() {
        assertFalse(TouchOutputGeometryPolicy.isUsable(1, 900))
        assertFalse(TouchOutputGeometryPolicy.isUsable(1600, 1))
        assertFalse(TouchOutputGeometryPolicy.isUsable(0, 0))
        assertNull(TouchOutputGeometryPolicy.normalize(100f, 100f, 1, 1))
        assertNull(TouchOutputGeometryPolicy.normalize(100f, 100f, 0, 900))
    }

    @Test
    fun nonFiniteCoordinatesAreRejected() {
        assertNull(TouchOutputGeometryPolicy.normalize(Float.NaN, 100f, 1600, 900))
        assertNull(TouchOutputGeometryPolicy.normalize(100f, Float.POSITIVE_INFINITY, 1600, 900))
    }

    @Test
    fun normalGeometryIsAccepted() {
        assertTrue(TouchOutputGeometryPolicy.isUsable(1600, 900))
    }
}
