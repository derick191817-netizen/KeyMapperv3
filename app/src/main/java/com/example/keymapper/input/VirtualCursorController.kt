import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
package com.example.keymapper.input

/** Estado lógico independente do cursor desenhado e das coordenadas físicas da tela. */
class VirtualCursorController(initialX: Float = 5_000f, initialY: Float = 5_000f) {
    data class Position(val x: Float, val y: Float)
    @Volatile private var px = initialX
    @Volatile private var py = initialY

    @Synchronized fun moveBy(dx: Float, dy: Float): Position? {
        if (!dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) return null
        px += dx
        py += dy
        // Limita apenas a magnitude do estado lógico para evitar overflow; não altera nem descarta o delta encaminhado.
        if (px > WRAP_LIMIT) px -= WRAP_SIZE else if (px < -WRAP_LIMIT) px += WRAP_SIZE
        if (py > WRAP_LIMIT) py -= WRAP_SIZE else if (py < -WRAP_LIMIT) py += WRAP_SIZE
        return Position(px, py)
    }

    @Synchronized fun set(x: Float, y: Float) {
        if (x.isFinite() && y.isFinite()) { px = x; py = y }
    }
    @Synchronized fun position(): Position = Position(px, py)

    private companion object { const val WRAP_LIMIT = 50_000f; const val WRAP_SIZE = 100_000f }
}
