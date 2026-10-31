package com.example.keymapper.input

import android.util.Log
import com.example.keymapper.diagnostics.InputDiagnostics

/**
 * Máquina de estados sincronizada. GRABBED significa que o Android confirmou Pointer Capture
 * para a View; RELATIVE_ACTIVE significa amostra relativa após Pointer Capture confirmado,
 * RELATIVE_FALLBACK_ACTIVE significa eixo relativo recebido por generic MotionEvent sem captura.
 * Nenhum estado afirma que o destino aceitou ou aplicou a saída injetada.
 */
object InputStateMachine {
    private const val TAG = "KeyMapperState"
    private val lock = Any()
    private var current: InputState = InputState.INACTIVE
    private var lastReason: String = "Inicializado"

    fun current(): InputState = synchronized(lock) { current }

    fun transition(next: InputState, reason: String, force: Boolean = false): Boolean {
        synchronized(lock) {
            if (current == next) {
                lastReason = reason
                return true
            }
            if (!force && !isAllowed(current, next)) {
                Log.w(TAG, "Transição ignorada: $current -> $next ($reason)")
                return false
            }
            val previous = current
            current = next
            lastReason = reason
            Log.i(TAG, "$previous -> $next: $reason")
            InputDiagnostics.setMode(next.name, "$previous -> $next: $reason")
            return true
        }
    }

    fun snapshot(): String = synchronized(lock) { "$current; motivo=$lastReason" }

    private fun isAllowed(from: InputState, to: InputState): Boolean =
        InputStatePolicy.isAllowed(from, to)
}
