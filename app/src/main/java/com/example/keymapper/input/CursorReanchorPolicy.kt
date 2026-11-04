package com.example.keymapper.input

/**
 * Mantém o retículo visual dentro do display sem prender o cursor na borda.
 *
 * A posição lógica é acumulada separadamente por [VirtualCursorController]. Esta política só
 * calcula onde desenhar o retículo: ao cruzar um limite, ele reaparece no ponto de ancoragem e
 * continua com o excedente. O cálculo é O(1), mesmo para deltas que atravessam milhares de telas.
 */
internal object CursorReanchorPolicy {
    data class Position(val x: Float, val y: Float)

    /** Caminho sem alocação para atualização do retículo em eventos de mouse de alta frequência. */
    fun advanceInto(
        startX: Float,
        startY: Float,
        deltaX: Float,
        deltaY: Float,
        anchorX: Float,
        anchorY: Float,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        out: FloatArray
    ): Boolean {
        if (out.size < 2 ||
            !startX.isFinite() || !startY.isFinite() || !deltaX.isFinite() || !deltaY.isFinite() ||
            !anchorX.isFinite() || !anchorY.isFinite() || !minX.isFinite() || !maxX.isFinite() ||
            !minY.isFinite() || !maxY.isFinite() || minX >= maxX || minY >= maxY
        ) return false

        val x = advanceAxis(
            startX.toDouble(), deltaX.toDouble(), anchorX.toDouble(),
            minX.toDouble(), maxX.toDouble()
        )
        val y = advanceAxis(
            startY.toDouble(), deltaY.toDouble(), anchorY.toDouble(),
            minY.toDouble(), maxY.toDouble()
        )
        if (!x.isFinite() || !y.isFinite()) return false
        out[0] = x.toFloat()
        out[1] = y.toFloat()
        return out[0].isFinite() && out[1].isFinite()
    }

    fun advance(
        startX: Float,
        startY: Float,
        deltaX: Float,
        deltaY: Float,
        anchorX: Float,
        anchorY: Float,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): Position? {
        val out = FloatArray(2)
        return if (advanceInto(startX, startY, deltaX, deltaY, anchorX, anchorY, minX, maxX, minY, maxY, out)) {
            Position(out[0], out[1])
        } else null
    }

    private fun advanceAxis(start: Double, delta: Double, anchor: Double, min: Double, max: Double): Double {
        val safeStart = start.coerceIn(min, max)
        val safeAnchor = anchor.coerceIn(min, max)
        val target = safeStart + delta
        if (!target.isFinite()) return safeAnchor
        if (target > max) {
            val span = max - safeAnchor
            if (span <= 0.0) return max
            val overflow = target - max
            val remainder = overflow % span
            // Uma chegada exata à borda não provoca uma reancoragem adicional.
            return safeAnchor + if (remainder == 0.0) span else remainder.coerceIn(0.0, span)
        }
        if (target < min) {
            val span = safeAnchor - min
            if (span <= 0.0) return min
            val overflow = min - target
            val remainder = overflow % span
            return safeAnchor - if (remainder == 0.0) span else remainder.coerceIn(0.0, span)
        }
        return target
    }
}
