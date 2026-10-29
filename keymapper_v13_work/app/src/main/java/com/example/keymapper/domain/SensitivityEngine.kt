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
        val speed = hypot(inputX, inputY) * scaleTime
        val gain = if (speed == 0.0) 0.0 else curveGain(
            curve, exponent, knee, gainAbove, speed
        ) / speed

        val tx = inputX * gain * safe(sx).toDouble() + remX.toDouble()
        val ty = inputY * gain * safe(sy).toDouble() + remY.toDouble()
        val rx = toOutput(tx)
        val ry = toOutput(ty)
        outX = rx.first
        outY = ry.first
        remX = rx.second
        remY = ry.second
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

    /** Limita a saída de um frame e limpa o resto quando houve saturação. */
    private fun toOutput(value: Double): Pair<Int, Float> {
        if (!value.isFinite()) {
            return when {
                value.isNaN() -> 0 to 0f
                value > 0 -> MAX_OUTPUT to 0f
                else -> -MAX_OUTPUT to 0f
            }
        }
        if (value > MAX_OUTPUT.toDouble()) return MAX_OUTPUT to 0f
        if (value < -MAX_OUTPUT.toDouble()) return -MAX_OUTPUT to 0f
        val integer = value.toInt()
        return integer to (value - integer.toDouble()).toFloat()
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
