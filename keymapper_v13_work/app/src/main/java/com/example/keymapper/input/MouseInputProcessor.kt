package com.example.keymapper.input

import java.util.concurrent.atomic.AtomicLong

/** Valida e contabiliza deltas recebidos; sensibilidade e curvas são aplicadas pelo motor de mira. */
class MouseInputProcessor {
    data class Delta(val dx: Float, val dy: Float, val deviceId: Int, val eventTime: Long)

    private val accepted = AtomicLong(0)
    private val rejected = AtomicLong(0)

    fun process(dx: Float, dy: Float, deviceId: Int, eventTime: Long): Delta? {
        if (!dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) {
            rejected.incrementAndGet()
            return null
        }
        // Um driver defeituoso não deve conseguir entregar deltas quase infinitos ao pipeline.
        // O limite é muito acima de um evento físico normal e só atua como proteção de integridade.
        accepted.incrementAndGet()
        return Delta(
            dx = dx.coerceIn(-MAX_DELTA_PER_EVENT, MAX_DELTA_PER_EVENT),
            dy = dy.coerceIn(-MAX_DELTA_PER_EVENT, MAX_DELTA_PER_EVENT),
            deviceId = deviceId,
            eventTime = eventTime
        )
    }

    fun acceptedCount(): Long = accepted.get()
    fun rejectedCount(): Long = rejected.get()
    fun reset() { accepted.set(0); rejected.set(0) }

    private companion object {
        const val MAX_DELTA_PER_EVENT = 100_000f
    }
}
