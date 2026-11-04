package com.example.keymapper.domain

import kotlin.math.hypot

/** Geometry-only HUD placement. Coordinates are normalized to the playable area. */
object HudPlacementPolicy {
    data class Point(val x: Float, val y: Float)

    data class OccupiedCircle(
        val x: Float,
        val y: Float,
        val radiusPx: Float
    )

    /**
     * Finds a visible position with the largest minimum clearance from existing controls.
     * The preferred position wins when the canvas is empty. If every candidate overlaps,
     * the least-overlapping candidate is returned instead of placing the new control off-screen.
     */
    fun findPosition(
        widthPx: Float,
        heightPx: Float,
        candidateRadiusPx: Float,
        occupied: List<OccupiedCircle>,
        preferred: Point = Point(0.5f, 0.5f)
    ): Point {
        if (!widthPx.isFinite() || !heightPx.isFinite() || widthPx <= 0f || heightPx <= 0f) {
            return Point(0.5f, 0.5f)
        }

        val radius = if (candidateRadiusPx.isFinite()) {
            candidateRadiusPx.coerceAtLeast(0f).coerceAtMost(minOf(widthPx, heightPx) / 2f)
        } else {
            0f
        }
        val minX = (radius / widthPx).coerceIn(0f, 0.5f)
        val minY = (radius / heightPx).coerceIn(0f, 0.5f)
        val maxX = 1f - minX
        val maxY = 1f - minY
        val preferredPoint = Point(
            normalized(preferred.x).coerceIn(minX, maxX),
            normalized(preferred.y).coerceIn(minY, maxY)
        )

        val validOccupied = occupied.filter {
            it.x.isFinite() && it.y.isFinite() && it.radiusPx.isFinite() && it.radiusPx >= 0f
        }
        if (validOccupied.isEmpty()) return preferredPoint

        // The grid is deliberately deterministic: the same profile and geometry always
        // produce the same placement, which keeps editor behavior predictable across devices.
        val candidates = ArrayList<Point>(17 * 13 + 1)
        candidates += preferredPoint
        for (row in 0..12) {
            val y = minY + (maxY - minY) * row / 12f
            for (column in 0..16) {
                val x = minX + (maxX - minX) * column / 16f
                candidates += Point(x, y)
            }
        }

        var best = preferredPoint
        var bestClearance = Float.NEGATIVE_INFINITY
        var bestDistance = Float.POSITIVE_INFINITY
        for (candidate in candidates) {
            var clearance = Float.POSITIVE_INFINITY
            for (other in validOccupied) {
                val dx = (candidate.x - normalized(other.x)) * widthPx
                val dy = (candidate.y - normalized(other.y)) * heightPx
                val distance = hypot(dx, dy)
                val otherRadius = other.radiusPx.coerceAtMost(minOf(widthPx, heightPx) / 2f)
                clearance = minOf(clearance, distance - radius - otherRadius)
            }
            val preferredDx = (candidate.x - preferredPoint.x) * widthPx
            val preferredDy = (candidate.y - preferredPoint.y) * heightPx
            val preferredDistance = preferredDx * preferredDx + preferredDy * preferredDy
            if (clearance > bestClearance ||
                (clearance == bestClearance && preferredDistance < bestDistance)
            ) {
                best = candidate
                bestClearance = clearance
                bestDistance = preferredDistance
            }
        }
        return best
    }

    private fun normalized(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else 0.5f
}
