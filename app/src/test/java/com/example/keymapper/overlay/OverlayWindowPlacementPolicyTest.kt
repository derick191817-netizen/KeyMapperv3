package com.example.keymapper.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayWindowPlacementPolicyTest {
    @Test
    fun landscapePanelUsesOppositeSideAndSafeMargins() {
        val left = OverlayWindowPlacementPolicy.forControl(1600, 900, 1f, controlX = 0.8f)
        val right = OverlayWindowPlacementPolicy.forControl(1600, 900, 1f, controlX = 0.2f)

        assertTrue(left.landscape)
        assertEquals(24, left.xPx)
        assertEquals(24, left.yPx)
        assertTrue(left.xPx + left.widthPx <= 1600 - 24)
        assertEquals(1600 - 24 - right.widthPx, right.xPx)
        assertTrue(right.xPx + right.widthPx <= 1600 - 24)
    }

    @Test
    fun portraitPanelMovesAwayFromSelectedControl() {
        val controlAtBottom = OverlayWindowPlacementPolicy.forControl(900, 1600, 1f, controlY = 0.8f)
        val controlAtTop = OverlayWindowPlacementPolicy.forControl(900, 1600, 1f, controlY = 0.2f)

        assertTrue(!controlAtBottom.landscape)
        assertEquals(24, controlAtBottom.yPx)
        assertEquals(1600 - 24 - controlAtTop.heightPx, controlAtTop.yPx)
        assertTrue(controlAtBottom.xPx >= 24)
        assertTrue(controlAtBottom.xPx + controlAtBottom.widthPx <= 900 - 24)
    }

    @Test
    fun everyPlacementRemainsInsideSmallDisplay() {
        val placement = OverlayWindowPlacementPolicy.forControl(320, 240, 3f, Float.NaN, Float.POSITIVE_INFINITY)
        assertTrue(placement.widthPx in 1..320)
        assertTrue("high density must not collapse a small panel", placement.heightPx > 100)
        assertTrue(placement.heightPx in 1..240)
        assertTrue(placement.xPx >= 0)
        assertTrue(placement.yPx >= 0)
        assertTrue(placement.xPx + placement.widthPx <= 320)
        assertTrue(placement.yPx + placement.heightPx <= 240)
    }

    @Test
    fun invalidDensityAndCoordinatesUseSafeDefaults() {
        val invalid = OverlayWindowPlacementPolicy.forControl(1000, 600, Float.NaN, Float.NaN, Float.NaN)
        val defaults = OverlayWindowPlacementPolicy.forControl(1000, 600, 1f, 0.5f, 0.5f)
        assertEquals(defaults, invalid)
    }

    @Test
    fun onePixelDisplayNeverProducesNegativeGeometry() {
        val result = OverlayWindowPlacementPolicy.forControl(1, 1, 4f)
        assertEquals(1, result.widthPx)
        assertEquals(1, result.heightPx)
        assertEquals(0, result.xPx)
        assertEquals(0, result.yPx)
    }
}
