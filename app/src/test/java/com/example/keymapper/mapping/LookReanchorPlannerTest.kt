package com.example.keymapper.mapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LookReanchorPlannerTest {
    private val bounds = LookReanchorPlanner.Bounds(10f, 990f, 10f, 590f)

    private fun assertPathDistance(
        startX: Float, startY: Float, anchorX: Float, anchorY: Float,
        plan: LookReanchorPlanner.Plan, expectedX: Float, expectedY: Float
    ) {
        var pathX = 0f
        var pathY = 0f
        var currentX = startX
        var currentY = startY
        for (point in plan.reanchors) {
            pathX += point.edgeX - currentX
            pathY += point.edgeY - currentY
            currentX = anchorX
            currentY = anchorY
        }
        pathX += plan.finalX - currentX
        pathY += plan.finalY - currentY
        assertEquals(expectedX, pathX, 0.05f)
        assertEquals(expectedY, pathY, 0.05f)
    }

    @Test fun movementInsideBoundsDoesNotReanchor() {
        val plan = LookReanchorPlanner.plan(500f, 300f, 20f, -10f, 500f, 300f, bounds)!!
        assertTrue(plan.reanchors.isEmpty())
        assertEquals(520f, plan.finalX, 0f)
        assertEquals(290f, plan.finalY, 0f)
        assertFalse(plan.capped)
    }

    @Test fun crossingRightEdgeReanchorsAndPreservesRemainder() {
        val plan = LookReanchorPlanner.plan(900f, 300f, 150f, 0f, 500f, 300f, bounds)!!
        assertEquals(1, plan.reanchors.size)
        assertEquals(990f, plan.reanchors.single().edgeX, 0f)
        assertEquals(560f, plan.finalX, 0f)
        assertPathDistance(900f, 300f, 500f, 300f, plan, 150f, 0f)
    }

    @Test fun multipleScreenWidthsAreHandledInOneInputBatch() {
        val plan = LookReanchorPlanner.plan(500f, 300f, 2_500f, 0f, 500f, 300f, bounds)!!
        assertTrue(plan.reanchors.size >= 2)
        assertFalse(plan.capped)
        assertEquals(550f, plan.finalX, 0f)
        assertPathDistance(500f, 300f, 500f, 300f, plan, 2_500f, 0f)
    }

    @Test fun negativeAndDiagonalCrossingsAreHandled() {
        val plan = LookReanchorPlanner.plan(500f, 300f, -1_100f, 500f, 500f, 300f, bounds)!!
        assertTrue(plan.reanchors.isNotEmpty())
        assertTrue(plan.finalX in 10f..990f)
        assertTrue(plan.finalY in 10f..590f)
        assertPathDistance(500f, 300f, 500f, 300f, plan, -1_100f, 500f)
    }

    @Test fun invalidInputReturnsNull() {
        assertEquals(null, LookReanchorPlanner.plan(Float.NaN, 0f, 1f, 1f, 500f, 300f, bounds))
        assertEquals(null, LookReanchorPlanner.plan(0f, 0f, 1f, 1f, 500f, 300f,
            LookReanchorPlanner.Bounds(10f, 10f, 10f, 590f)))
    }

    @Test fun plannerDoesNotLoopForeverForLargeFiniteDelta() {
        val plan = LookReanchorPlanner.plan(500f, 300f, 100_000f, 0f, 500f, 300f, bounds, maxReanchors = 8)
        assertNotNull(plan)
        assertTrue(plan!!.capped)
        assertTrue(plan.reanchors.size <= 8)
        assertTrue(plan.finalX in 10f..990f)
    }
}
