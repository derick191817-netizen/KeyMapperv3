package com.example.keymapper.mapping

/**
 * Planeja reancoragens para visão por toque. O resultado descreve os pontos em que o contato
 * atual deve ser levado antes de ser substituído por um novo contato no anchor.
 *
 * O cálculo é independente do Android para poder ser testado. Ele preserva deltas que atravessam
 * mais de uma largura/altura da área em um único evento. A aplicação concreta usa UP/DOWN entre
 * contatos; isso é um fallback de toque, não uma captura relativa nativa sem interrupção.
 */
internal object LookReanchorPlanner {
    data class Bounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float) {
        fun isValid(): Boolean = minX.isFinite() && maxX.isFinite() && minY.isFinite() && maxY.isFinite() &&
            minX < maxX && minY < maxY
    }

    data class ReanchorPoint(val edgeX: Float, val edgeY: Float)
    data class Plan(
        val reanchors: List<ReanchorPoint>,
        val finalX: Float,
        val finalY: Float,
        val capped: Boolean
    )

    fun plan(
        startX: Float,
        startY: Float,
        deltaX: Float,
        deltaY: Float,
        anchorX: Float,
        anchorY: Float,
        bounds: Bounds,
        maxReanchors: Int = DEFAULT_MAX_REANCHORS
    ): Plan? {
        if (!startX.isFinite() || !startY.isFinite() || !deltaX.isFinite() || !deltaY.isFinite() ||
            !anchorX.isFinite() || !anchorY.isFinite() || !bounds.isValid() || maxReanchors < 1
        ) return null

        val minX = bounds.minX.toDouble()
        val maxX = bounds.maxX.toDouble()
        val minY = bounds.minY.toDouble()
        val maxY = bounds.maxY.toDouble()
        val ax = anchorX.toDouble().coerceIn(minX, maxX)
        val ay = anchorY.toDouble().coerceIn(minY, maxY)
        var x = startX.toDouble() + deltaX.toDouble()
        var y = startY.toDouble() + deltaY.toDouble()
        val reanchors = ArrayList<ReanchorPoint>()
        var capped = false

        while (x < minX || x > maxX || y < minY || y > maxY) {
            if (reanchors.size >= maxReanchors) {
                capped = true
                break
            }
            val edgeX = x.coerceIn(minX, maxX)
            val edgeY = y.coerceIn(minY, maxY)
            reanchors += ReanchorPoint(edgeX.toFloat(), edgeY.toFloat())
            // O movimento restante é aplicado a partir do novo ponto de toque.
            x = ax + (x - edgeX)
            y = ay + (y - edgeY)
        }

        return Plan(
            reanchors = reanchors,
            finalX = x.coerceIn(minX, maxX).toFloat(),
            finalY = y.coerceIn(minY, maxY).toFloat(),
            capped = capped
        )
    }

    private const val DEFAULT_MAX_REANCHORS = 512
}
