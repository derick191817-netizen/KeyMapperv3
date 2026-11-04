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


    @Test fun allFourEdgesPreserveHorizontalAndVerticalDeltas() {
        val cases = listOf(
            Triple(900f, 300f, 180f to 0f),
            Triple(100f, 300f, -180f to 0f),
            Triple(500f, 500f, 0f to 180f),
            Triple(500f, 100f, 0f to -180f)
        )
        for ((startX, startY, delta) in cases) {
            val plan = LookReanchorPlanner.plan(
                startX, startY, delta.first, delta.second, 500f, 300f, bounds
            ) ?: error("valid direction was rejected")
            assertTrue("each edge approach should reanchor", plan.reanchors.isNotEmpty())
            assertPathDistance(startX, startY, 500f, 300f, plan, delta.first, delta.second)
        }
    }

    @Test fun repeatedDirectionChangesDoNotCreateAOneWayBias() {
        var cursorX = 500f
        var cursorY = 300f
        var representedX = 0f
        var representedY = 0f
        val deltas = listOf(900f to 0f, -1_800f to 0f, 900f to 0f, 0f to -900f, 0f to 1_800f, 0f to -900f)
        for ((dx, dy) in deltas) {
            val plan = LookReanchorPlanner.plan(cursorX, cursorY, dx, dy, 500f, 300f, bounds)
                ?: error("valid direction change was rejected")
            representedX += dx - plan.remainingDeltaX
            representedY += dy - plan.remainingDeltaY
            cursorX = plan.finalX
            cursorY = plan.finalY
        }
        assertEquals(0f, representedX, 0.1f)
        assertEquals(0f, representedY, 0.1f)
    }

    @Test fun invalidInputReturnsNull() {
        assertEquals(null, LookReanchorPlanner.plan(Float.NaN, 0f, 1f, 1f, 500f, 300f, bounds))
        assertEquals(null, LookReanchorPlanner.plan(0f, 0f, 1f, 1f, 500f, 300f,
            LookReanchorPlanner.Bounds(10f, 10f, 10f, 590f)))
    }

    @Test fun plannerReturnsUnconsumedMovementInsteadOfClampingAtTheEdge() {
        val plan = LookReanchorPlanner.plan(
            500f, 300f, 100_000f, 0f, 500f, 300f, bounds, maxReanchors = 8
        )
        assertNotNull(plan)
        assertTrue(plan!!.capped)
        assertTrue(plan.reanchors.size <= 8)
        assertEquals(500f, plan.finalX, 0f)
        assertEquals(300f, plan.finalY, 0f)
        assertTrue(plan.remainingDeltaX > 0f)
        assertEquals(0f, plan.remainingDeltaY, 0f)
        assertTrue(plan.hasRemaining)
    }

    @Test fun oversizedHorizontalMovementIsFullyPreservedAcrossBoundedBatches() {
        assertChunkedMovementIsPreserved(100_000f, 0f, 8)
    }

    @Test fun oversizedDiagonalMovementIsFullyPreservedAcrossBoundedBatches() {
        assertChunkedMovementIsPreserved(-100_000f, 65_000f, 5)
    }

    private fun assertChunkedMovementIsPreserved(dx: Float, dy: Float, maxReanchors: Int) {
        var currentX = 500f
        var currentY = 300f
        var remainingX = dx
        var remainingY = dy
        var consumedX = 0.0
        var consumedY = 0.0
        var batches = 0

        while ((remainingX != 0f || remainingY != 0f) && batches < 2_000) {
            val plan = LookReanchorPlanner.plan(
                currentX, currentY, remainingX, remainingY,
                500f, 300f, bounds, maxReanchors
            ) ?: error("planner rejected a finite continuation")
            assertTrue(plan.reanchors.size <= maxReanchors)
            consumedX += remainingX.toDouble() - plan.remainingDeltaX.toDouble()
            consumedY += remainingY.toDouble() - plan.remainingDeltaY.toDouble()
            currentX = plan.finalX
            currentY = plan.finalY
            remainingX = plan.remainingDeltaX
            remainingY = plan.remainingDeltaY
            batches++
        }

        assertTrue("continuation must finish in a bounded number of batches", batches < 2_000)
        assertEquals(dx.toDouble(), consumedX, 0.1)
        assertEquals(dy.toDouble(), consumedY, 0.1)
        assertEquals(0f, remainingX, 0f)
        assertEquals(0f, remainingY, 0f)
        assertTrue(currentX in bounds.minX..bounds.maxX)
        assertTrue(currentY in bounds.minY..bounds.maxY)
        assertTrue(batches > 1)
    }

    private val radialRadius = 100f

    private fun assertRadialPathDistance(
        startX: Float,
        startY: Float,
        anchorX: Float,
        anchorY: Float,
        plan: LookReanchorPlanner.Plan,
        expectedX: Float,
        expectedY: Float
    ) {
        var consumedX = 0.0
        var consumedY = 0.0
        var currentX = startX
        var currentY = startY
        for (point in plan.reanchors) {
            consumedX += (point.edgeX - currentX).toDouble()
            consumedY += (point.edgeY - currentY).toDouble()
            currentX = anchorX
            currentY = anchorY
        }
        consumedX += (plan.finalX - currentX).toDouble()
        consumedY += (plan.finalY - currentY).toDouble()
        consumedX += plan.remainingDeltaX.toDouble()
        consumedY += plan.remainingDeltaY.toDouble()
        assertEquals(expectedX.toDouble(), consumedX, 0.08)
        assertEquals(expectedY.toDouble(), consumedY, 0.08)
    }

    @Test fun radialBoundaryDoesNotReanchorInsideRadius() {
        val plan = LookReanchorPlanner.plan(
            500f, 300f, 80f, 0f, 500f, 300f, bounds,
            boundaryRadius = radialRadius
        )!!
        assertTrue(plan.reanchors.isEmpty())
        assertEquals(580f, plan.finalX, 0.001f)
        assertEquals(300f, plan.finalY, 0.001f)
        assertFalse(plan.capped)
    }

    @Test fun radialBoundaryReanchorsAtExactCircleIntersection() {
        val plan = LookReanchorPlanner.plan(
            500f, 300f, 150f, 0f, 500f, 300f, bounds,
            boundaryRadius = radialRadius
        )!!
        assertEquals(1, plan.reanchors.size)
        assertEquals(600f, plan.reanchors.single().edgeX, 0.001f)
        assertEquals(300f, plan.reanchors.single().edgeY, 0.001f)
        assertEquals(550f, plan.finalX, 0.001f)
        assertEquals(300f, plan.finalY, 0.001f)
        assertRadialPathDistance(500f, 300f, 500f, 300f, plan, 150f, 0f)
    }

    @Test fun radialBoundaryHandlesDiagonalMovementWithoutChangingItsDirection() {
        val plan = LookReanchorPlanner.plan(
            500f, 300f, 200f, 200f, 500f, 300f, bounds,
            boundaryRadius = radialRadius
        )!!
        assertTrue(plan.reanchors.size >= 2)
        for (point in plan.reanchors) {
            val distance = kotlin.math.hypot(point.edgeX - 500f, point.edgeY - 300f)
            assertEquals(radialRadius, distance, 0.02f)
        }
        val finalDistance = kotlin.math.hypot(plan.finalX - 500f, plan.finalY - 300f)
        assertTrue(finalDistance <= radialRadius + 0.02f)
        assertRadialPathDistance(500f, 300f, 500f, 300f, plan, 200f, 200f)
    }

    @Test fun radialBoundaryPreservesMovementAcrossCappedBatches() {
        var x = 500f
        var y = 300f
        var dx = 100_000f
        var dy = -65_000f
        var consumedX = 0.0
        var consumedY = 0.0
        var batches = 0
        while ((dx != 0f || dy != 0f) && batches < 2_000) {
            val plan = LookReanchorPlanner.plan(
                x, y, dx, dy, 500f, 300f, bounds,
                maxReanchors = 5, boundaryRadius = radialRadius
            ) ?: error("radial planner rejected a finite continuation")
            assertTrue(plan.reanchors.size <= 5)
            consumedX += dx.toDouble() - plan.remainingDeltaX.toDouble()
            consumedY += dy.toDouble() - plan.remainingDeltaY.toDouble()
            x = plan.finalX
            y = plan.finalY
            dx = plan.remainingDeltaX
            dy = plan.remainingDeltaY
            batches++
        }
        assertTrue("radial continuation must finish", batches < 2_000)
        assertEquals(100_000.0, consumedX, 0.5)
        assertEquals(-65_000.0, consumedY, 0.5)
        assertEquals(0f, dx, 0f)
        assertEquals(0f, dy, 0f)
        assertTrue(kotlin.math.hypot(x - 500f, y - 300f) <= radialRadius + 0.02f)
    }

    @Test fun radialBoundaryClampsRadiusToHardTouchBounds() {
        val nearLeft = LookReanchorPlanner.Bounds(10f, 990f, 10f, 590f)
        val plan = LookReanchorPlanner.plan(
            40f, 300f, -80f, 0f, 40f, 300f, nearLeft,
            boundaryRadius = 100f
        )!!
        assertEquals(2, plan.reanchors.size)
        assertEquals(10f, plan.reanchors.first().edgeX, 0.001f)
        assertEquals(10f, plan.reanchors.last().edgeX, 0.001f)
        assertEquals(20f, plan.finalX, 0.001f)
        assertRadialPathDistance(40f, 300f, 40f, 300f, plan, -80f, 0f)
    }

    @Test fun radialPlannerRejectsStartOutsideActiveRadius() {
        assertEquals(null, LookReanchorPlanner.plan(
            650f, 300f, 1f, 0f, 500f, 300f, bounds,
            boundaryRadius = radialRadius
        ))
    }

    @Test fun radialPlannerRejectsNegativeAndNonFiniteRadius() {
        assertEquals(null, LookReanchorPlanner.plan(
            500f, 300f, 1f, 0f, 500f, 300f, bounds,
            boundaryRadius = -1f
        ))
        assertEquals(null, LookReanchorPlanner.plan(
            500f, 300f, 1f, 0f, 500f, 300f, bounds,
            boundaryRadius = Float.NaN
        ))
    }

    @Test fun radialModePreservesFractionalSubpixelMovement() {
        val plan = LookReanchorPlanner.plan(
            500f, 300f, 0.35f, -0.35f, 500f, 300f, bounds,
            boundaryRadius = radialRadius
        )!!
        assertTrue(plan.reanchors.isEmpty())
        assertEquals(500.35f, plan.finalX, 0.0001f)
        assertEquals(299.65f, plan.finalY, 0.0001f)
    }

    @Test fun radialMovementReversalsDoNotCreateDirectionalBias() {
        var x = 500f
        var y = 300f
        var totalX = 0.0
        var totalY = 0.0
        val deltas = listOf(350f to 0f, -700f to 0f, 350f to 0f, 0f to 350f, 0f to -700f, 0f to 350f)
        for ((dx, dy) in deltas) {
            val plan = LookReanchorPlanner.plan(
                x, y, dx, dy, 500f, 300f, bounds,
                boundaryRadius = radialRadius
            ) ?: error("radial planner rejected direction reversal")
            totalX += dx.toDouble() - plan.remainingDeltaX.toDouble()
            totalY += dy.toDouble() - plan.remainingDeltaY.toDouble()
            x = plan.finalX
            y = plan.finalY
        }
        assertEquals(0.0, totalX, 0.2)
        assertEquals(0.0, totalY, 0.2)
    }

}
