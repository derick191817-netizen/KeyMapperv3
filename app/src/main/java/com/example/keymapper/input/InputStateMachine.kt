import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
package com.example.keymapper.input

import android.util.Log
import com.example.keymapper.diagnostics.InputDiagnostics

/** Estados explícitos do pipeline de entrada. Não representa permissão do Android; representa
 * somente o estado observado pelo próprio KeyMapper. */
enum class InputState {
    INACTIVE, CONFIGURATION, GAME_READY, POINTER_CAPTURED, POINTER_RELEASED, SUSPENDED, ERROR
}

/** Máquina de estados pequena e sincronizada para evitar que foco/captura exibam estados fictícios.
 * Os eventos são observações do sistema, não uma tentativa de forçar APIs indisponíveis. */
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

    private fun isAllowed(from: InputState, to: InputState): Boolean {
        if (to == InputState.ERROR || to == InputState.SUSPENDED || to == InputState.INACTIVE) return true
        return when (from) {
            InputState.INACTIVE -> to == InputState.GAME_READY || to == InputState.CONFIGURATION
            InputState.CONFIGURATION -> to == InputState.GAME_READY || to == InputState.POINTER_RELEASED
            InputState.GAME_READY -> to == InputState.CONFIGURATION || to == InputState.POINTER_CAPTURED || to == InputState.POINTER_RELEASED
            InputState.POINTER_CAPTURED -> to == InputState.POINTER_RELEASED || to == InputState.CONFIGURATION
            InputState.POINTER_RELEASED -> to == InputState.POINTER_CAPTURED || to == InputState.CONFIGURATION || to == InputState.GAME_READY
            InputState.SUSPENDED -> to == InputState.GAME_READY || to == InputState.POINTER_RELEASED || to == InputState.CONFIGURATION
            InputState.ERROR -> to == InputState.INACTIVE || to == InputState.GAME_READY
        }
    }
}
