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
        /** True when this bounded batch must be followed by another batch. */
        val capped: Boolean,
        /** Movement not yet represented by this batch, measured from the re-anchor point. */
        val remainingDeltaX: Float = 0f,
        val remainingDeltaY: Float = 0f
    ) {
        val hasRemaining: Boolean
            get() = remainingDeltaX != 0f || remainingDeltaY != 0f
    }

    fun plan(
        startX: Float,
        startY: Float,
        deltaX: Float,
        deltaY: Float,
        anchorX: Float,
        anchorY: Float,
        bounds: Bounds,
        maxReanchors: Int = DEFAULT_MAX_REANCHORS,
        boundaryRadius: Float = 0f
    ): Plan? {
        if (!startX.isFinite() || !startY.isFinite() || !deltaX.isFinite() || !deltaY.isFinite() ||
            !anchorX.isFinite() || !anchorY.isFinite() || !bounds.isValid() || maxReanchors < 1 ||
            !boundaryRadius.isFinite() || boundaryRadius < 0f
        ) return null
        if (boundaryRadius > 0f) {
            return planRadial(
                startX, startY, deltaX, deltaY, anchorX, anchorY,
                bounds, maxReanchors, boundaryRadius
            )
        }

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

        if (capped) {
            // Depois da última troca de contato, o novo ponteiro está no anchor. Não descarte
            // o excedente nem o comprima na borda: devolva-o como próximo lote relativo ao anchor.
            val remainingX = (x - ax).toFloat()
            val remainingY = (y - ay).toFloat()
            if (!remainingX.isFinite() || !remainingY.isFinite()) return null
            return Plan(
                reanchors = reanchors,
                finalX = ax.toFloat(),
                finalY = ay.toFloat(),
                capped = true,
                remainingDeltaX = remainingX,
                remainingDeltaY = remainingY
            )
        }

        return Plan(
            reanchors = reanchors,
            finalX = x.toFloat(),
            finalY = y.toFloat(),
            capped = false
        )
    }


    /**
     * Recentragem por raio, equivalente ao Radius do Aiming Mode. O cruzamento é calculado
     * pela interseção exata do segmento com o círculo, preservando o delta excedente em cada
     * reancoragem. O raio é limitado pela distância do anchor às quatro bordas físicas.
     */
    private fun planRadial(
        startX: Float,
        startY: Float,
        deltaX: Float,
        deltaY: Float,
        anchorX: Float,
        anchorY: Float,
        bounds: Bounds,
        maxReanchors: Int,
        requestedRadius: Float
    ): Plan? {
        val ax = anchorX.toDouble()
        val ay = anchorY.toDouble()
        val minX = bounds.minX.toDouble()
        val maxX = bounds.maxX.toDouble()
        val minY = bounds.minY.toDouble()
        val maxY = bounds.maxY.toDouble()
        if (ax !in minX..maxX || ay !in minY..maxY) return null

        val safeRadius = minOf(ax - minX, maxX - ax, ay - minY, maxY - ay)
        val radius = minOf(requestedRadius.toDouble(), safeRadius)
        if (!radius.isFinite() || radius < 1.0) return null
        val radiusSquared = radius * radius
        var px = startX.toDouble() - ax
        var py = startY.toDouble() - ay
        var dx = deltaX.toDouble()
        var dy = deltaY.toDouble()
        val startDistanceSquared = px * px + py * py
        // Uma mudança de configuração durante um gesto não deve inventar um caminho inverso.
        // O chamador mantém o raio ativo estável até o controle ser liberado.
        if (!startDistanceSquared.isFinite() || startDistanceSquared > radiusSquared + 1e-4) return null

        val reanchors = ArrayList<ReanchorPoint>()
        var iterations = 0
        while (true) {
            val qx = px + dx
            val qy = py + dy
            val qDistanceSquared = qx * qx + qy * qy
            if (!qDistanceSquared.isFinite()) return null
            if (qDistanceSquared <= radiusSquared + 1e-7) {
                return Plan(
                    reanchors = reanchors,
                    finalX = (ax + qx).toFloat(),
                    finalY = (ay + qy).toFloat(),
                    capped = false
                )
            }

            val a = dx * dx + dy * dy
            if (!a.isFinite() || a <= 1e-18) return null
            val b = 2.0 * (px * dx + py * dy)
            val c = px * px + py * py - radiusSquared
            val discriminant = b * b - 4.0 * a * c
            if (!discriminant.isFinite() || discriminant < -1e-7) return null
            val root = kotlin.math.sqrt(discriminant.coerceAtLeast(0.0))
            val t = ((-b + root) / (2.0 * a)).coerceIn(0.0, 1.0)
            val edgeX = (ax + px + dx * t).toFloat()
            val edgeY = (ay + py + dy * t).toFloat()
            if (!edgeX.isFinite() || !edgeY.isFinite()) return null
            reanchors += ReanchorPoint(edgeX, edgeY)

            val remainingFraction = 1.0 - t
            dx *= remainingFraction
            dy *= remainingFraction
            px = 0.0
            py = 0.0

            if (reanchors.size >= maxReanchors) {
                return Plan(
                    reanchors = reanchors,
                    finalX = ax.toFloat(),
                    finalY = ay.toFloat(),
                    capped = dx != 0.0 || dy != 0.0,
                    remainingDeltaX = dx.toFloat(),
                    remainingDeltaY = dy.toFloat()
                )
            }
            iterations++
            if (iterations > maxReanchors + 1) return null
        }
    }

    private const val DEFAULT_MAX_REANCHORS = 64
}
