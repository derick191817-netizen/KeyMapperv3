package com.example.keymapper.input

/**
 * Escolhe a origem correta dos deltas do mouse.
 *
 * Durante Pointer Capture, X/Y representam movimento relativo. Fora da captura, X/Y
 * normalmente são posições absolutas; nesse caso só AXIS_RELATIVE_X/Y são aceitos.
 */
object MouseDeltaPolicy {
    data class Delta(val x: Float, val y: Float)

    /**
     * Variante sem alocação para o caminho de alta frequência. [out] precisa ter ao menos
     * duas posições: X no índice 0 e Y no índice 1. Retorna false para amostras inválidas/zero.
     */
    fun selectInto(
        captured: Boolean,
        pointerX: Float,
        pointerY: Float,
        relativeAxisX: Float,
        relativeAxisY: Float,
        out: FloatArray
    ): Boolean {
        if (out.size < 2) return false
        val x = if (captured) pointerX else relativeAxisX
        val y = if (captured) pointerY else relativeAxisY
        if (!x.isFinite() || !y.isFinite() || (x == 0f && y == 0f)) return false
        out[0] = x
        out[1] = y
        return true
    }

    /** API conveniente para testes e chamadas fora do caminho crítico. */
    fun select(
        captured: Boolean,
        pointerX: Float,
        pointerY: Float,
        relativeAxisX: Float,
        relativeAxisY: Float
    ): Delta? {
        val out = FloatArray(2)
        return if (selectInto(captured, pointerX, pointerY, relativeAxisX, relativeAxisY, out)) {
            Delta(out[0], out[1])
        } else null
    }
}
