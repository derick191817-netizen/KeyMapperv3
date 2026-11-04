package com.example.keymapper.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputBatchLossGuardTest {
    @Test fun noLossDoesNotRequestRecovery() {
        val guard = NativeInputBatchLossGuard()
        assertFalse(guard.consumeRecoveryRequired())
    }

    @Test fun oneDroppedBatchRequestsExactlyOneRecovery() {
        val guard = NativeInputBatchLossGuard()
        guard.markBatchDropped()
        assertTrue(guard.consumeRecoveryRequired())
        assertFalse(guard.consumeRecoveryRequired())
    }

    @Test fun multipleDroppedBatchesAreCoalescedUntilRecovery() {
        val guard = NativeInputBatchLossGuard()
        guard.markBatchDropped()
        guard.markBatchDropped()
        guard.markBatchDropped()
        assertTrue(guard.consumeRecoveryRequired())
        assertFalse(guard.consumeRecoveryRequired())
    }

    @Test fun lossAfterRecoverySchedulesAnotherReset() {
        val guard = NativeInputBatchLossGuard()
        guard.markBatchDropped()
        assertTrue(guard.consumeRecoveryRequired())
        guard.markBatchDropped()
        assertTrue(guard.consumeRecoveryRequired())
    }
}
