package com.example.keymapper.injection

/** Backoff pequeno e limitado para refazer o vínculo privilegiado sem criar bind em loop. */
internal object UserServiceReconnectPolicy {
    private val delaysMs = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)

    const val MAX_AUTOMATIC_RETRIES = 5

    fun delayMillis(attempt: Int): Long {
        require(attempt >= 1) { "attempt deve começar em 1" }
        return delaysMs[(attempt - 1).coerceAtMost(delaysMs.lastIndex)]
    }

    fun shouldRetry(attemptsAlreadyScheduled: Int): Boolean =
        attemptsAlreadyScheduled < MAX_AUTOMATIC_RETRIES

    /** Conectar o Binder não zera o orçamento; somente captura realmente iniciada zera. */
    fun afterCaptureStart(attemptsAlreadyScheduled: Int, captureStarted: Boolean): Int =
        if (captureStarted) 0 else attemptsAlreadyScheduled

    /** Nova rodada só começa por solicitação ativa e quando não existe vínculo/tentativa pendente. */
    fun shouldAttemptExplicitRetry(
        active: Boolean,
        requestedEnabled: Boolean,
        binding: Boolean,
        hasConnection: Boolean,
        retryScheduled: Boolean,
        binderReady: Boolean,
        permissionGranted: Boolean,
        serverVersion: Int
    ): Boolean = active && requestedEnabled && !binding && !hasConnection && !retryScheduled &&
        binderReady && permissionGranted && serverVersion >= 10
}
