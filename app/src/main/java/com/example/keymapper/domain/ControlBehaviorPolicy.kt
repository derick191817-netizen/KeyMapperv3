package com.example.keymapper.domain

/** Regras puras para gestos e repetição; compartilhadas pelo motor e pelos testes locais. */
object ControlBehaviorPolicy {
    data class Point(val x: Float, val y: Float)

    fun rapidIntervalMs(value: Int): Int = value.coerceIn(40, 2_000)
    fun swipeDurationMs(value: Int): Int = value.coerceIn(50, 2_000)

    fun swipeProgress(elapsedMs: Long, durationMs: Int): Float {
        val safeDuration = swipeDurationMs(durationMs).toFloat()
        val safeElapsed = elapsedMs.coerceAtLeast(0L).toFloat()
        return (safeElapsed / safeDuration).coerceIn(0f, 1f)
    }

    fun interpolateSwipe(start: Point, end: Point, progress: Float): Point {
        val t = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        val sx = start.x.takeIf { it.isFinite() } ?: 0f
        val sy = start.y.takeIf { it.isFinite() } ?: 0f
        val ex = end.x.takeIf { it.isFinite() } ?: sx
        val ey = end.y.takeIf { it.isFinite() } ?: sy
        return Point(sx + (ex - sx) * t, sy + (ey - sy) * t)
    }
}
