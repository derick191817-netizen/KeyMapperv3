package com.example.keymapper.input

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sinaliza perda de um lote evdev entre a thread JNI e o looper que consome a entrada.
 * O consumidor executa uma liberação global antes de aceitar o próximo lote, evitando que um
 * DOWN/UP perdido deixe uma tecla, botão ou relatório relativo parcialmente retido.
 */
internal class NativeInputBatchLossGuard {
    private val recoveryRequired = AtomicBoolean(false)

    fun markBatchDropped() {
        recoveryRequired.set(true)
    }

    /** Retorna true uma única vez por grupo de perdas ainda não recuperadas. */
    fun consumeRecoveryRequired(): Boolean = recoveryRequired.getAndSet(false)
}
