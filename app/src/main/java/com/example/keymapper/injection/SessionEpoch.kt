package com.example.keymapper.injection

import java.util.concurrent.atomic.AtomicLong

/**
 * Monotonic session identity for asynchronous Binder callbacks. Invalidating an epoch makes
 * every callback created by an earlier service session stale, even if a new session starts.
 */
internal class SessionEpoch {
    private val generation = AtomicLong(0L)

    fun begin(): Long = generation.incrementAndGet()

    fun invalidate(): Long = generation.incrementAndGet()

    fun currentToken(): Long = generation.get()

    fun isCurrent(token: Long): Boolean = token != 0L && generation.get() == token
}
