package com.example.keymapper.mapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookBoundaryPolicyTest {
    @Test fun zeroRadiusKeepsLegacyBoundaryMode() {
        assertEquals(0f, LookBoundaryPolicy.resolveRadiusPx(
            radiusPercent = 0f, width = 1000f, height = 500f,
            anchorX = 500f, anchorY = 250f,
            minX = 10f, maxX = 990f, minY = 10f, maxY = 490f
        ), 0f)
    }

    @Test fun radiusUsesSmallerGameDimension() {
        val radius = LookBoundaryPolicy.resolveRadiusPx(
            radiusPercent = 20f, width = 1000f, height = 500f,
            anchorX = 500f, anchorY = 250f,
            minX = 10f, maxX = 990f, minY = 10f, maxY = 490f
        )
        assertEquals(100f, radius, 0.001f)
    }

    @Test fun radiusIsClampedToSafeTouchBounds() {
        val radius = LookBoundaryPolicy.resolveRadiusPx(
            radiusPercent = 45f, width = 1000f, height = 500f,
            anchorX = 40f, anchorY = 250f,
            minX = 10f, maxX = 990f, minY = 10f, maxY = 490f
        )
        assertEquals(30f, radius, 0.001f)
    }

    @Test fun invalidGeometryAndNonFiniteValuesDisableRadius() {
        val invalid = listOf(
            floatArrayOf(Float.NaN, 1000f, 500f, 500f, 250f, 10f, 990f, 10f, 490f),
            floatArrayOf(10f, 1000f, 500f, 500f, 250f, 10f, 10f, 10f, 490f),
            floatArrayOf(10f, Float.POSITIVE_INFINITY, 500f, 500f, 250f, 10f, 990f, 10f, 490f),
            floatArrayOf(10f, 1000f, 500f, 5f, 250f, 10f, 990f, 10f, 490f)
        )
        for (v in invalid) {
            assertEquals(0f, LookBoundaryPolicy.resolveRadiusPx(
                v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]
            ), 0f)
        }
    }

    @Test fun subpixelSafeRadiusFallsBackToLegacyBoundary() {
        val radius = LookBoundaryPolicy.resolveRadiusPx(
            radiusPercent = 10f, width = 1000f, height = 500f,
            anchorX = 10.5f, anchorY = 250f,
            minX = 10f, maxX = 990f, minY = 10f, maxY = 490f
        )
        assertEquals(0f, radius, 0f)
    }

    @Test fun percentagesAreClampedToSupportedRange() {
        val radius = LookBoundaryPolicy.resolveRadiusPx(
            radiusPercent = 100f, width = 1000f, height = 500f,
            anchorX = 500f, anchorY = 250f,
            minX = 10f, maxX = 990f, minY = 10f, maxY = 490f
        )
        assertEquals(225f, radius, 0.001f)
    }

    @Test fun autoReleaseInheritsGlobalWhenOverrideIsNegative() {
        assertEquals(750L, LookBoundaryPolicy.resolveAutoReleaseMs(-1, 750))
    }

    @Test fun autoReleaseZeroMeansKeepContact() {
        assertEquals(0L, LookBoundaryPolicy.resolveAutoReleaseMs(0, 750))
    }

    @Test fun autoReleaseOverrideIsIndependentFromGlobal() {
        assertEquals(1200L, LookBoundaryPolicy.resolveAutoReleaseMs(1200, 750))
    }

    @Test fun autoReleaseValuesAreClamped() {
        assertEquals(60_000L, LookBoundaryPolicy.resolveAutoReleaseMs(80_000, 750))
        assertEquals(0L, LookBoundaryPolicy.resolveAutoReleaseMs(-20, 750))
        assertTrue(LookBoundaryPolicy.resolveAutoReleaseMs(-1, 100_000) == 60_000L)
    }
}
