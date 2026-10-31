package com.example.keymapper.input

/** Máquina de estados pura: pode ser validada sem Android, Binder ou Views. */
enum class InputState {
    INACTIVE,
    CONFIGURATION,
    GAME_READY,
    FREE,
    GRAB_REQUESTED,
    GRABBED,
    RELATIVE_ACTIVE,
    RELATIVE_FALLBACK_ACTIVE,
    RELEASE_REQUESTED,
    RELEASED,
    SUSPENDED,
    DISCONNECTED,
    ERROR,
    // Compatibilidade com logs/estados antigos de versões anteriores.
    POINTER_CAPTURED,
    POINTER_RELEASED
}

object InputStatePolicy {
    fun isAllowed(from: InputState, to: InputState): Boolean {
        if (from == to) return true
        // Estados de interrupção podem ser alcançados a partir de qualquer estado ativo.
        if (to in setOf(InputState.ERROR, InputState.SUSPENDED, InputState.INACTIVE, InputState.DISCONNECTED)) return true
        // A liberação é sempre permitida: nenhum estado pode bloquear o caminho de cleanup.
        if (to == InputState.RELEASE_REQUESTED) return true

        return when (from) {
            InputState.INACTIVE -> to in setOf(
                InputState.CONFIGURATION, InputState.GAME_READY, InputState.FREE,
                InputState.GRAB_REQUESTED, InputState.RELEASED
            )
            InputState.CONFIGURATION -> to in setOf(
                InputState.GAME_READY, InputState.RELEASED, InputState.FREE, InputState.GRAB_REQUESTED
            )
            InputState.GAME_READY, InputState.FREE -> to in setOf(
                InputState.CONFIGURATION, InputState.GRAB_REQUESTED, InputState.RELEASED,
                InputState.POINTER_CAPTURED, InputState.POINTER_RELEASED
            )
            InputState.GRAB_REQUESTED -> to in setOf(
                InputState.GRABBED, InputState.RELATIVE_FALLBACK_ACTIVE,
                InputState.RELEASED, InputState.GRAB_REQUESTED
            )
            InputState.GRABBED, InputState.POINTER_CAPTURED -> to in setOf(
                InputState.RELATIVE_ACTIVE, InputState.RELATIVE_FALLBACK_ACTIVE,
                InputState.GRAB_REQUESTED, InputState.RELEASED, InputState.POINTER_RELEASED
            )
            InputState.RELATIVE_ACTIVE -> to in setOf(
                InputState.GRABBED, InputState.GRAB_REQUESTED, InputState.RELEASED,
                InputState.RELATIVE_FALLBACK_ACTIVE
            )
            InputState.RELATIVE_FALLBACK_ACTIVE -> to in setOf(
                InputState.GRABBED, InputState.GRAB_REQUESTED, InputState.RELEASED,
                InputState.RELATIVE_ACTIVE
            )
            InputState.RELEASE_REQUESTED -> to == InputState.RELEASED || to == InputState.GRAB_REQUESTED
            InputState.RELEASED, InputState.POINTER_RELEASED -> to in setOf(
                InputState.GRAB_REQUESTED, InputState.CONFIGURATION, InputState.GAME_READY, InputState.FREE
            )
            InputState.SUSPENDED -> to in setOf(
                InputState.GRAB_REQUESTED, InputState.GRABBED, InputState.RELEASED,
                InputState.CONFIGURATION, InputState.GAME_READY, InputState.FREE
            )
            InputState.DISCONNECTED -> to in setOf(
                InputState.FREE, InputState.RELEASED, InputState.GRAB_REQUESTED
            )
            InputState.ERROR -> to in setOf(
                InputState.INACTIVE, InputState.GAME_READY, InputState.FREE,
                InputState.RELEASED, InputState.GRAB_REQUESTED,
                InputState.RELATIVE_FALLBACK_ACTIVE
            )
        }
    }

    /** RELATIVE_ACTIVE só é usado quando Pointer Capture foi confirmado; fallback fica separado. */
    fun isCaptureConfirmed(state: InputState): Boolean =
        state == InputState.GRABBED || state == InputState.RELATIVE_ACTIVE || state == InputState.POINTER_CAPTURED

    fun isReceivingRelativeInput(state: InputState): Boolean =
        state == InputState.RELATIVE_ACTIVE || state == InputState.RELATIVE_FALLBACK_ACTIVE

    /** Não sinaliza falha de Pointer Capture se já existe fluxo relativo alternativo funcional. */
    fun shouldReportCaptureTimeout(
        captureRequested: Boolean,
        windowFocused: Boolean,
        pointerCaptureConfirmed: Boolean,
        relativeFallbackActive: Boolean
    ): Boolean = captureRequested && windowFocused && !pointerCaptureConfirmed && !relativeFallbackActive
}
