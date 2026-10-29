package com.example.keymapper.input

/**
 * Estado lógico del puntero/mira, independiente del cursor físico dibujado por Android.
 * La posición se mantiene en un plano lógico circular para evitar overflow sin confundirla
 * con coordenadas absolutas de pantalla. La inyección física/relativa la realiza el motor.
 */
class VirtualCursorController(initialX: Float = DEFAULT_X, initialY: Float = DEFAULT_Y) {
    data class Position(val x: Float, val y: Float)

    @Volatile private var px = normalizeInitial(initialX)
    @Volatile private var py = normalizeInitial(initialY)

    @Synchronized
    fun moveBy(dx: Float, dy: Float): Position? {
        if (!dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) return null
        // Float.MAX_VALUE é finito, mas somas em Float podem virar infinito. A soma em Double
        // seguida de wrap por resto lida também com deltas que atravessam o limite muitas vezes.
        px = wrap(px.toDouble() + dx.toDouble())
        py = wrap(py.toDouble() + dy.toDouble())
        return Position(px, py)
    }

    @Synchronized
    fun set(x: Float, y: Float) {
        if (x.isFinite() && y.isFinite()) {
            px = wrap(x.toDouble())
            py = wrap(y.toDouble())
        }
    }

    /** Reinicia o estado lógico; não move nem reposiciona fisicamente o ponteiro Android. */
    @Synchronized
    fun reset(x: Float = DEFAULT_X, y: Float = DEFAULT_Y) {
        px = normalizeInitial(x)
        py = normalizeInitial(y)
    }

    @Synchronized
    fun position(): Position = Position(px, py)

    private fun normalizeInitial(value: Float): Float =
        if (value.isFinite()) wrap(value.toDouble()) else 0f

    private fun wrap(value: Double): Float {
        if (!value.isFinite()) return 0f
        var remainder = value % WRAP_SIZE
        if (remainder >= WRAP_LIMIT) remainder -= WRAP_SIZE
        if (remainder < -WRAP_LIMIT) remainder += WRAP_SIZE
        return remainder.toFloat()
    }

    private companion object {
        const val DEFAULT_X = 5_000f
        const val DEFAULT_Y = 5_000f
        const val WRAP_LIMIT = 50_000.0
        const val WRAP_SIZE = WRAP_LIMIT * 2.0
    }
}
