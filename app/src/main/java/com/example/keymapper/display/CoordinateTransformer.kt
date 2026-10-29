package com.example.keymapper.display

import android.graphics.RectF
import kotlin.math.roundToInt

/** Conversões centralizadas entre coordenadas normalizadas da área do jogo e pixels do monitor. */
object CoordinateTransformer {
    data class PixelPoint(val x: Float, val y: Float)

    fun normalizedToPixels(area: RectF, x: Float, y: Float): PixelPoint {
        val nx = if (x.isFinite()) x.coerceIn(0f, 1f) else 0.5f
        val ny = if (y.isFinite()) y.coerceIn(0f, 1f) else 0.5f
        return PixelPoint(area.left + nx * area.width(), area.top + ny * area.height())
    }

    fun pixelsToNormalized(area: RectF, x: Float, y: Float): PixelPoint {
        if (area.width() <= 0f || area.height() <= 0f) return PixelPoint(0.5f, 0.5f)
        return PixelPoint(((x - area.left) / area.width()).coerceIn(0f, 1f), ((y - area.top) / area.height()).coerceIn(0f, 1f))
    }

    fun normalizedToIntPixels(area: RectF, x: Float, y: Float): Pair<Int, Int> {
        val p = normalizedToPixels(area, x, y)
        return p.x.roundToInt() to p.y.roundToInt()
    }
}
