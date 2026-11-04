package com.example.keymapper.mapping

import kotlin.math.min

/**
 * Regras reutilizáveis do Aiming Mode: raio de recentralização e liberação automática.
 * A escala do raio é relativa ao menor lado da área de jogo e nunca ultrapassa os limites
 * físicos seguros do toque. 0% preserva o comportamento legado baseado nas bordas.
 */
internal object LookBoundaryPolicy {
    fun resolveRadiusPx(
        radiusPercent: Float,
        width: Float,
        height: Float,
        anchorX: Float,
        anchorY: Float,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): Float {
        if (!radiusPercent.isFinite() || radiusPercent <= 0f ||
            !width.isFinite() || !height.isFinite() || width <= 1f || height <= 1f ||
            !anchorX.isFinite() || !anchorY.isFinite() ||
            !minX.isFinite() || !maxX.isFinite() || !minY.isFinite() || !maxY.isFinite() ||
            minX >= maxX || minY >= maxY ||
            anchorX !in minX..maxX || anchorY !in minY..maxY
        ) return 0f

        val requested = min(width, height) * radiusPercent.coerceIn(0f, 45f) / 100f
        val safe = min(
            min(anchorX - minX, maxX - anchorX),
            min(anchorY - minY, maxY - anchorY)
        )
        val radius = min(requested, safe)
        return if (radius.isFinite() && radius >= 1f) radius else 0f
    }

    /** Apenas -1 herda o global; 0 mantém o contato; negativos corrompidos são normalizados para 0. */
    fun resolveAutoReleaseMs(buttonOverrideMs: Int, globalMs: Int): Long =
        (if (buttonOverrideMs == -1) globalMs else buttonOverrideMs)
            .coerceIn(0, 60_000)
            .toLong()
}
