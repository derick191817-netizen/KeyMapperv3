package com.example.keymapper.domain

import kotlin.math.hypot
import kotlin.math.pow

/**
 * Matemática da mira: curva + eixos X/Y independentes + acúmulo do resto fracionário.
 * Curvas: 0 linear | 1 exponencial | 2 customizada | 3 desacelerar | 4 aceleração suave.
 * Entradas anormais são neutralizadas para que NaN/Infinity não contaminem chamadas futuras.
 */
class SensitivityEngine {
    private var remX = 0f
    private var remY = 0f
    var outX = 0
        private set
    var outY = 0
        private set

    fun process(
        dx: Float, dy: Float, sx: Float, sy: Float,
        curve: Int, exponent: Float, knee: Float, gainAbove: Float,
        timeScale: Float = 1f
    ) {
        if (!dx.isFinite() || !dy.isFinite() || !timeScale.isFinite()) {
            reset()
            return
        }

        // Os deltas físicos normais são minúsculos perto deste limite. Ele impede overflow em
        // casos de driver corrompido sem mudar a faixa operacional ordinária do mouse.
        val inputX = dx.coerceIn(-MAX_INPUT_DELTA, MAX_INPUT_DELTA).toDouble()
        val inputY = dy.coerceIn(-MAX_INPUT_DELTA, MAX_INPUT_DELTA).toDouble()
        val scaleTime = timeScale.coerceIn(0f, MAX_TIME_SCALE).toDouble()
        val inputMagnitude = hypot(inputX, inputY)
        // timeScale ajusta somente a velocidade usada para escolher o ganho da curva.
        // O divisor precisa ser a mesma magnitude normalizada; dividir pela magnitude original
        // multiplicaria a sensibilidade pela taxa de polling (ex.: 4x em 1000 Hz vs 250 Hz).
        val normalizedSpeed = inputMagnitude * scaleTime
        val gain = if (normalizedSpeed == 0.0) 0.0 else curveGain(
            curve, exponent, knee, gainAbove, normalizedSpeed
        ) / normalizedSpeed

        // Mantém o delta físico da amostra; a curva modifica apenas o ganho, não a quantidade
        // de amostras por segundo. Assim, o deslocamento acumulado não depende do polling rate.
        val tx = inputX * gain * safe(sx).toDouble() + remX.toDouble()
        val ty = inputY * gain * safe(sy).toDouble() + remY.toDouble()
        writeOutputX(tx)
        writeOutputY(ty)
    }

    fun reset() {
        remX = 0f
        remY = 0f
        outX = 0
        outY = 0
    }

    /** Sensitivity inválida continua usando 1x, por compatibilidade com o comportamento anterior. */
    private fun safe(v: Float): Float = if (!v.isFinite()) 1f else v.coerceIn(0f, MAX_SENS)

    private fun curveGain(curve: Int, exponent: Float, knee: Float, gainAbove: Float, v: Double): Double {
        val e = if (exponent.isFinite()) exponent.coerceIn(0.1f, 10f).toDouble() else 1.5
        val k = if (knee.isFinite()) knee.coerceIn(0f, 100f).toDouble() else 8.0
        val ga = if (gainAbove.isFinite()) gainAbove.coerceIn(0.01f, 10f).toDouble() else 1.0
        val raw = when (curve) {
            1 -> v.pow(e) / REF.pow(e - 1.0)
            2 -> if (v <= k) v else k + (v - k) * ga
            3 -> v.pow(1.0 / e) * REF.pow(1.0 - 1.0 / e)
            4 -> v * (0.6 + 0.1 * v).coerceIn(0.6, 2.0)
            else -> v
        }
        return if (!raw.isFinite()) MAX_CURVE_GAIN else raw.coerceIn(0.0, MAX_CURVE_GAIN)
    }

    /** Converte cada eixo sem criar Pair por amostra no caminho de alta frequência. */
    private fun writeOutputX(value: Double) {
        if (!value.isFinite()) {
            outX = when {
                value.isNaN() -> 0
                value > 0 -> MAX_OUTPUT
                else -> -MAX_OUTPUT
            }
            remX = 0f
            return
        }
        if (value > MAX_OUTPUT.toDouble()) {
            outX = MAX_OUTPUT
            remX = 0f
            return
        }
        if (value < -MAX_OUTPUT.toDouble()) {
            outX = -MAX_OUTPUT
            remX = 0f
            return
        }
        val integer = value.toInt()
        outX = integer
        remX = (value - integer.toDouble()).toFloat()
    }

    private fun writeOutputY(value: Double) {
        if (!value.isFinite()) {
            outY = when {
                value.isNaN() -> 0
                value > 0 -> MAX_OUTPUT
                else -> -MAX_OUTPUT
            }
            remY = 0f
            return
        }
        if (value > MAX_OUTPUT.toDouble()) {
            outY = MAX_OUTPUT
            remY = 0f
            return
        }
        if (value < -MAX_OUTPUT.toDouble()) {
            outY = -MAX_OUTPUT
            remY = 0f
            return
        }
        val integer = value.toInt()
        outY = integer
        remY = (value - integer.toDouble()).toFloat()
    }

    private companion object {
        const val REF = 10.0
        const val MAX_SENS = 1000f
        const val MAX_INPUT_DELTA = 100_000f
        const val MAX_TIME_SCALE = 4f
        const val MAX_CURVE_GAIN = 10_000.0
        const val MAX_OUTPUT = 1_000_000
    }
}
