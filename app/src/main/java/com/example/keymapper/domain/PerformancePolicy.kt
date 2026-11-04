package com.example.keymapper.domain

/**
 * Converte o modo de desempenho escolhido pelo usuário em uma folga efetiva no agendador.
 * Mode: 0 equilibrado, 1 alta performance, 2 economia.
 */
object PerformancePolicy {
    fun extraSlackMs(mode: Int, totalRamGb: Int): Int = when (mode) {
        1 -> 0
        2 -> 5
        else -> if (totalRamGb in 1..3) 5 else 0
    }

    fun effectiveLookIntervalMs(baseIntervalMs: Int, mode: Int, totalRamGb: Int): Int =
        applySlack(baseIntervalMs, extraSlackMs(mode, totalRamGb))

    fun applySlack(baseIntervalMs: Int, extraSlackMs: Int): Int {
        val base = baseIntervalMs.coerceIn(1, 20)
        return (base + extraSlackMs.coerceIn(0, 20)).coerceIn(1, 20)
    }
}
