package com.example.keymapper.overlay

import kotlin.math.roundToInt

/**
 * Calcula dimensões e posição de um painel overlay usando apenas a geometria do display.
 * A política é independente de Android para que as regras de margem e clamp sejam testadas
 * na JVM e reutilizadas em celulares, janelas DeX e displays externos.
 */
object OverlayWindowPlacementPolicy {
    data class Placement(
        val widthPx: Int,
        val heightPx: Int,
        val xPx: Int,
        val yPx: Int,
        val landscape: Boolean
    )

    /**
     * Em paisagem, posiciona o painel no lado oposto ao controle selecionado.
     * Em retrato, posiciona-o na borda oposta ao controle, mantendo margem em todos os lados.
     * Coordenadas de controle são normalizadas (0..1); entradas inválidas usam o centro.
     */
    fun forControl(
        screenWidthPx: Int,
        screenHeightPx: Int,
        density: Float,
        controlX: Float = 0.5f,
        controlY: Float = 0.5f
    ): Placement {
        val screenWidth = screenWidthPx.coerceAtLeast(1)
        val screenHeight = screenHeightPx.coerceAtLeast(1)
        val safeDensity = density.takeIf { it.isFinite() && it > 0f }?.coerceIn(0.5f, 8f) ?: 1f
        val requestedMargin = (24f * safeDensity).roundToInt().coerceAtLeast(1)
        val minimumDimension = minOf(screenWidth, screenHeight)
        // Não permita que alta densidade consuma quase todo um display pequeno.
        val proportionalMarginLimit = (minimumDimension * 0.08f).roundToInt()
        val absoluteMarginLimit = ((minimumDimension - 1) / 2).coerceAtLeast(0)
        val margin = requestedMargin.coerceAtMost(proportionalMarginLimit.coerceAtMost(absoluteMarginLimit))
        val availableWidth = (screenWidth - margin * 2).coerceAtLeast(1)
        val availableHeight = (screenHeight - margin * 2).coerceAtLeast(1)
        val landscape = screenWidth > screenHeight
        val normalizedX = controlX.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f
        val normalizedY = controlY.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f

        val width: Int
        val height: Int
        val x: Int
        val y: Int
        if (landscape) {
            width = minOf((380f * safeDensity).roundToInt().coerceAtLeast(1), (availableWidth / 2).coerceAtLeast(1))
            height = availableHeight
            x = if (normalizedX > 0.5f) margin else (screenWidth - margin - width).coerceAtLeast(0)
            y = ((screenHeight - height) / 2).coerceAtLeast(0)
        } else {
            width = minOf((560f * safeDensity).roundToInt().coerceAtLeast(1), availableWidth)
            height = minOf((screenHeight * 0.60f).roundToInt().coerceAtLeast(1), availableHeight)
            x = ((screenWidth - width) / 2).coerceAtLeast(0)
            y = if (normalizedY > 0.5f) margin else (screenHeight - margin - height).coerceAtLeast(0)
        }

        // Final clamp also protects narrow / unusually small virtual displays.
        val finalWidth = width.coerceIn(1, screenWidth)
        val finalHeight = height.coerceIn(1, screenHeight)
        return Placement(
            widthPx = finalWidth,
            heightPx = finalHeight,
            xPx = x.coerceIn(0, (screenWidth - finalWidth).coerceAtLeast(0)),
            yPx = y.coerceIn(0, (screenHeight - finalHeight).coerceAtLeast(0)),
            landscape = landscape
        )
    }
}
