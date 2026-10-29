package com.example.keymapper.input

import com.example.keymapper.data.AppSettings
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Matemática da mira: curva + eixos X/Y independentes + acúmulo do resto fracionário
 * (a mira não "pula" coordenadas).
 *
 * Curvas: 0 linear (1:1) | 1 exponencial | 2 customizada (joelho + ganho) |
 *         3 desacelerar | 4 "Melhorar precisão do ponteiro" (aceleração suave, aproximação do Windows).
 * As curvas 1 e 3 são normalizadas para que a velocidade de referência (REF) saia igual à entrada.
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
        timeScale: Float = 1f   // normaliza a velocidade para um intervalo de referência (curvas iguais em qualquer taxa)
    ) {
        val v = hypot(dx, dy) * timeScale
        val k = if (v == 0f) 0f else curveGain(curve, exponent, knee, gainAbove, v) / v
        val tx = dx * k * safe(sx) + remX
        val ty = dy * k * safe(sy) + remY
        outX = tx.toInt()
        outY = ty.toInt()
        remX = tx - outX
        remY = ty - outY
    }

    fun process(dx: Float, dy: Float, s: AppSettings) =
        process(dx, dy, s.sensX, s.sensY, s.curve, s.exponent, s.knee, s.gain)

    fun reset() {
        remX = 0f; remY = 0f; outX = 0; outY = 0
    }

    /** Aceita de 0 a 1000 (valor digitado), ignorando NaN/infinito. */
    private fun safe(v: Float): Float = if (v.isNaN() || v.isInfinite()) 1f else v.coerceIn(0f, MAX_SENS)

    private fun curveGain(curve: Int, exponent: Float, knee: Float, gainAbove: Float, v: Float): Float {
        val e = exponent.coerceIn(0.1f, 10f)
        return when (curve) {
            1 -> v.pow(e) / REF.pow(e - 1f)
            2 -> if (v <= knee) v else knee + (v - knee) * gainAbove
            3 -> v.pow(1f / e) * REF.pow(1f - 1f / e)
            4 -> v * (0.6f + 0.1f * v).coerceIn(0.6f, 2.0f)   // 1:1 em ~4 contagens, até 2x em movimentos rápidos
            else -> v
        }
    }

    private companion object {
        const val REF = 10f
        const val MAX_SENS = 1000f
    }
}
