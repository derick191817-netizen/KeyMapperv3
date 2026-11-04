package com.example.keymapper.input

/** Decisão pura para recuperar Pointer Capture sem retry infinito nem falso erro no fallback relativo. */
object CaptureRetryPolicy {
    enum class Action { NO_ACTION, WAIT_FOR_FALLBACK, RETRY, FAIL }

    fun decide(
        requested: Boolean,
        windowFocused: Boolean,
        captureConfirmed: Boolean,
        relativeFallbackActive: Boolean,
        retryCount: Int,
        maxRetries: Int
    ): Action {
        if (!requested || !windowFocused || captureConfirmed) return Action.NO_ACTION
        if (relativeFallbackActive) return Action.WAIT_FOR_FALLBACK
        val safeMax = maxRetries.coerceAtLeast(0)
        return if (retryCount.coerceAtLeast(0) < safeMax) Action.RETRY else Action.FAIL
    }
}
