package com.example.keymapper.input

/**
 * Forwards a native input batch without hiding delivery failures from JNI.
 * The native worker uses the pending Java exception to decide whether it must
 * retain per-device recovery markers and retry the batch boundary.
 */
internal object NativeBatchCallbackForwarder {
    fun forward(
        records: LongArray,
        callback: (LongArray) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        try {
            callback(records)
        } catch (failure: Throwable) {
            // Diagnostic handling must never replace the original failure that JNI observes.
            runCatching { onFailure(failure) }
            throw failure
        }
    }
}
