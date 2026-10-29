package com.example.keymapper.display

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenGeometryPolicyTest {
    private val base = ScreenGeometryPolicy.Snapshot(
        displayId = 0, width = 1600, height = 900, densityDpi = 240
    )

    @Test
    fun unchangedMetricsOnlyRefreshOverlay() {
        assertEquals(ScreenGeometryPolicy.Change.NONE, ScreenGeometryPolicy.classify(base, base.copy()))
    }

    @Test
    fun orientationOrWindowResizeRequestsRebuild() {
        assertEquals(
            ScreenGeometryPolicy.Change.RESIZE,
            ScreenGeometryPolicy.classify(base, base.copy(width = 900, height = 1600))
        )
    }

    @Test
    fun displayOrDensityChangeRequestsReinitialization() {
        assertEquals(
            ScreenGeometryPolicy.Change.REINITIALIZE,
            ScreenGeometryPolicy.classify(base, base.copy(displayId = 2))
        )
        assertEquals(
            ScreenGeometryPolicy.Change.REINITIALIZE,
            ScreenGeometryPolicy.classify(base, base.copy(densityDpi = 280, width = 900, height = 1600))
        )
    }

    @Test
    fun displayChangeHasPriorityOverSizeChange() {
        assertEquals(
            ScreenGeometryPolicy.Change.REINITIALIZE,
            ScreenGeometryPolicy.classify(base, base.copy(displayId = 1, width = 900, height = 1600))
        )
    }
}
