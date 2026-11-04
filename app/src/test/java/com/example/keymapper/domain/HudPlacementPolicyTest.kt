package com.example.keymapper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HudPlacementPolicyTest {
    @Test
    fun emptyCanvasUsesPreferredCenter() {
        val result = HudPlacementPolicy.findPosition(
            widthPx = 1000f,
            heightPx = 600f,
            candidateRadiusPx = 30f,
            occupied = emptyList()
        )
        assertEquals(0.5f, result.x, 0f)
        assertEquals(0.5f, result.y, 0f)
    }

    @Test
    fun placementMovesAwayFromOccupiedCenter() {
        val result = HudPlacementPolicy.findPosition(
            widthPx = 1000f,
            heightPx = 600f,
            candidateRadiusPx = 30f,
            occupied = listOf(HudPlacementPolicy.OccupiedCircle(0.5f, 0.5f, 50f))
        )
        val distance = kotlin.math.hypot((result.x - 0.5f) * 1000f, (result.y - 0.5f) * 600f)
        assertTrue("new control should not remain over the occupied center", distance >= 79f)
    }

    @Test
    fun candidateRadiusAlwaysRemainsInsideArea() {
        val result = HudPlacementPolicy.findPosition(
            widthPx = 1000f,
            heightPx = 600f,
            candidateRadiusPx = 80f,
            occupied = listOf(HudPlacementPolicy.OccupiedCircle(0f, 0f, 100f)),
            preferred = HudPlacementPolicy.Point(0f, 0f)
        )
        assertTrue(result.x >= 0.08f && result.x <= 0.92f)
        assertTrue(result.y >= 80f / 600f && result.y <= 1f - 80f / 600f)
    }

    @Test
    fun invalidGeometryFallsBackToCenter() {
        val result = HudPlacementPolicy.findPosition(
            widthPx = Float.NaN,
            heightPx = 0f,
            candidateRadiusPx = Float.NaN,
            occupied = emptyList()
        )
        assertEquals(0.5f, result.x, 0f)
        assertEquals(0.5f, result.y, 0f)
    }

    @Test
    fun invalidOccupiedEntriesAreIgnored() {
        val result = HudPlacementPolicy.findPosition(
            widthPx = 1000f,
            heightPx = 600f,
            candidateRadiusPx = 24f,
            occupied = listOf(HudPlacementPolicy.OccupiedCircle(Float.NaN, 0.5f, 100f))
        )
        assertEquals(0.5f, result.x, 0f)
        assertEquals(0.5f, result.y, 0f)
    }
}
