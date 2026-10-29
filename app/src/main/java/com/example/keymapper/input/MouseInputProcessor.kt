package com.example.keymapper.input

import java.util.concurrent.atomic.AtomicLong

/** Valida e contabiliza deltas recebidos. Não aplica sensibilidade: isso pertence ao AimController/SensitivityEngine. */
class MouseInputProcessor {
    data class Delta(val dx: Float, val dy: Float, val deviceId: Int, val eventTime: Long)

    private val accepted = AtomicLong(0)
    private val rejected = AtomicLong(0)

    fun process(dx: Float, dy: Float, deviceId: Int, eventTime: Long): Delta? {
        if (!dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) {
            rejected.incrementAndGet()
            return null
        }
        accepted.incrementAndGet()
        return Delta(dx, dy, deviceId, eventTime)
    }

    fun acceptedCount(): Long = accepted.get()
    fun rejectedCount(): Long = rejected.get()
    fun reset() { accepted.set(0); rejected.set(0) }
}
