package com.example.keymapper.input

/**
 * Posição lógica do mouse virtual. Esta coordenada NÃO representa a posição do ponteiro Android
 * nem é limitada às dimensões da tela. Ela serve para manter um referencial acumulado enquanto
 * os deltas relativos são encaminhados ao motor de visão.
 *
 * O cursor visual do HUD tem outro papel: no modo capturado ele fica ancorado no ponto de mira.
 * Portanto, a posição lógica pode continuar crescendo durante giros repetidos de 360 graus sem
 * deslocar o retículo gráfico até uma borda.
 */
class VirtualCursorController(initialX: Double = 0.0, initialY: Double = 0.0) {
    data class Position(val x: Double, val y: Double)
    data class Motion(val deltaX: Double, val deltaY: Double, val position: Position)

    @Volatile private var px = normalize(initialX)
    @Volatile private var py = normalize(initialY)

    /** Atualiza a posição lógica e devolve o delta efetivamente aceito pelo acumulador. */
    @Synchronized
    fun advance(dx: Float, dy: Float): Motion? {
        if (!dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) return null
        val oldX = px
        val oldY = py
        px = normalize(px + dx.toDouble())
        py = normalize(py + dy.toDouble())
        return Motion(px - oldX, py - oldY, Position(px, py)).takeIf {
            it.deltaX != 0.0 || it.deltaY != 0.0
        }
    }

    /** Compatibilidade para chamadas que só precisam consultar a posição após mover. */
    @Synchronized
    fun moveBy(dx: Float, dy: Float): Position? = advance(dx, dy)?.position

    @Synchronized
    fun set(x: Float, y: Float) = set(x.toDouble(), y.toDouble())

    @Synchronized
    fun set(x: Double, y: Double) {
        if (x.isFinite() && y.isFinite()) {
            px = normalize(x)
            py = normalize(y)
        }
    }

    @Synchronized
    fun reset(x: Double = 0.0, y: Double = 0.0) {
        // Coordenadas inválidas não devem apagar a última posição lógica válida.
        if (x.isFinite() && y.isFinite()) {
            px = normalize(x)
            py = normalize(y)
        }
    }

    @Synchronized
    fun position(): Position = Position(px, py)

    private fun normalize(value: Double): Double =
        if (value.isFinite()) value.coerceIn(-MAX_LOGICAL_COORDINATE, MAX_LOGICAL_COORDINATE) else 0.0

    private companion object {
        // Limite de integridade, não limite de tela. Mesmo 1e12 unidades excede enormemente
        // qualquer sessão humana de movimento; evita perda de precisão por valores corrompidos.
        const val MAX_LOGICAL_COORDINATE = 1_000_000_000_000.0
    }
}
