package com.example.keymapper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBatchCallbackForwarderTest {
    @Test
    fun successfulDeliveryDoesNotReportFailure() {
        val payload = longArrayOf(1L, 2L, 3L)
        var received: LongArray? = null
        var failureReported = false

        NativeBatchCallbackForwarder.forward(
            records = payload,
            callback = { received = it },
            onFailure = { failureReported = true }
        )

        assertTrue(payload === received)
        assertTrue(!failureReported)
    }

    @Test
    fun callbackFailureIsReportedAndRethrownUnchanged() {
        val payload = longArrayOf(4L, 5L, 6L)
        val expected = IllegalStateException("binder delivery failed")
        var reported: Throwable? = null
        var caught: Throwable? = null

        try {
            NativeBatchCallbackForwarder.forward(
                records = payload,
                callback = { throw expected },
                onFailure = { reported = it }
            )
        } catch (failure: Throwable) {
            caught = failure
        }

        assertTrue(expected === reported)
        assertTrue(expected === caught)
        assertEquals("binder delivery failed", caught?.message)
    }

    @Test
    fun diagnosticFailureDoesNotMaskDeliveryFailure() {
        val expected = IllegalArgumentException("original")
        var caught: Throwable? = null

        try {
            NativeBatchCallbackForwarder.forward(
                records = longArrayOf(1L),
                callback = { throw expected },
                onFailure = { throw AssertionError("diagnostics failed") }
            )
        } catch (failure: Throwable) {
            caught = failure
        }

        assertTrue(expected === caught)
    }
}
