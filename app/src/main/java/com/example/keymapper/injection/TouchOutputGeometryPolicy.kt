package com.example.keymapper.injection

/** Coordenadas do touchscreen virtual normalizadas para os eixos ABS 0..65535. */
internal data class NormalizedTouchPoint(val x: Float, val y: Float)

/**
 * Geometria é pré-condição do transporte uinput. Não inventar uma tela 1x1 quando a consulta
 * falha: isso faria qualquer coordenada válida colapsar silenciosamente para (0, 0).
 */
internal object TouchOutputGeometryPolicy {
    const val ABS_MAXIMUM = 65535f

    fun isUsable(width: Int, height: Int): Boolean = width > 1 && height > 1

    fun normalize(x: Float, y: Float, width: Int, height: Int): NormalizedTouchPoint? {
        if (!isUsable(width, height) || !x.isFinite() || !y.isFinite()) return null
        val maxX = (width - 1).toFloat()
        val maxY = (height - 1).toFloat()
        return NormalizedTouchPoint(
            x = (x.coerceIn(0f, maxX) / maxX) * ABS_MAXIMUM,
            y = (y.coerceIn(0f, maxY) / maxY) * ABS_MAXIMUM
        )
    }
}
