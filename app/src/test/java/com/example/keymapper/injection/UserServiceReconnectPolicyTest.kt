package com.example.keymapper.injection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserServiceReconnectPolicyTest {
    @Test fun usesIncreasingBackoff() {
        assertEquals(1_000L, UserServiceReconnectPolicy.delayMillis(1))
        assertEquals(2_000L, UserServiceReconnectPolicy.delayMillis(2))
        assertEquals(5_000L, UserServiceReconnectPolicy.delayMillis(3))
        assertEquals(10_000L, UserServiceReconnectPolicy.delayMillis(4))
        assertEquals(30_000L, UserServiceReconnectPolicy.delayMillis(5))
    }

    @Test fun capsDelayAfterLastConfiguredStep() {
        assertEquals(30_000L, UserServiceReconnectPolicy.delayMillis(6))
        assertEquals(30_000L, UserServiceReconnectPolicy.delayMillis(100))
    }

    @Test fun allowsExactlyFiveAutomaticRetries() {
        repeat(UserServiceReconnectPolicy.MAX_AUTOMATIC_RETRIES) { attempt ->
            assertTrue(UserServiceReconnectPolicy.shouldRetry(attempt))
        }
        assertFalse(UserServiceReconnectPolicy.shouldRetry(UserServiceReconnectPolicy.MAX_AUTOMATIC_RETRIES))
    }

    @Test fun failedCaptureStartDoesNotResetRetryBudget() {
        assertEquals(3, UserServiceReconnectPolicy.afterCaptureStart(3, captureStarted = false))
        assertEquals(0, UserServiceReconnectPolicy.afterCaptureStart(3, captureStarted = true))
    }

    @Test fun binderConnectionsAloneCannotResetTheAutomaticRetryBudget() {
        var attempts = 0
        repeat(UserServiceReconnectPolicy.MAX_AUTOMATIC_RETRIES) {
            assertTrue(UserServiceReconnectPolicy.shouldRetry(attempts))
            attempts++
            // Simula Binder conectado, mas startInputCapture ainda falhou.
            attempts = UserServiceReconnectPolicy.afterCaptureStart(attempts, captureStarted = false)
        }
        assertEquals(UserServiceReconnectPolicy.MAX_AUTOMATIC_RETRIES, attempts)
        assertFalse(UserServiceReconnectPolicy.shouldRetry(attempts))
        assertEquals(0, UserServiceReconnectPolicy.afterCaptureStart(attempts, captureStarted = true))
    }

    @Test fun explicitRetryRequiresActiveRequestedCaptureAndReadyBinder() {
        assertTrue(UserServiceReconnectPolicy.shouldAttemptExplicitRetry(
            active = true, requestedEnabled = true, binding = false, hasConnection = false,
            retryScheduled = false, binderReady = true, permissionGranted = true, serverVersion = 13
        ))
    }

    @Test fun explicitRetryRejectsInactiveOrDisabledCapture() {
        assertFalse(UserServiceReconnectPolicy.shouldAttemptExplicitRetry(
            active = false, requestedEnabled = true, binding = false, hasConnection = false,
            retryScheduled = false, binderReady = true, permissionGranted = true, serverVersion = 13
        ))
        assertFalse(UserServiceReconnectPolicy.shouldAttemptExplicitRetry(
            active = true, requestedEnabled = false, binding = false, hasConnection = false,
            retryScheduled = false, binderReady = true, permissionGranted = true, serverVersion = 13
        ))
    }

    @Test fun explicitRetryDoesNotDuplicateBindingOrPendingRetry() {
        assertFalse(UserServiceReconnectPolicy.shouldAttemptExplicitRetry(
            active = true, requestedEnabled = true, binding = true, hasConnection = false,
            retryScheduled = false, binderReady = true, permissionGranted = true, serverVersion = 13
        ))
        assertFalse(UserServiceReconnectPolicy.shouldAttemptExplicitRetry(
            active = true, requestedEnabled = true, binding = false, hasConnection = true,
            retryScheduled = false, binderReady = true, permissionGranted = true, serverVersion = 13
        ))
        assertFalse(UserServiceReconnectPolicy.shouldAttemptExplicitRetry(
            active = true, requestedEnabled = true, binding = false, hasConnection = false,
            retryScheduled = true, binderReady = true, permissionGranted = true, serverVersion = 13
        ))
    }

    @Test fun explicitRetryRequiresBinderPermissionAndSupportedVersion() {
        assertFalse(UserServiceReconnectPolicy.shouldAttemptExplicitRetry(
            active = true, requestedEnabled = true, binding = false, hasConnection = false,
            retryScheduled = false, binderReady = false, permissionGranted = true, serverVersion = 13
        ))
        assertFalse(UserServiceReconnectPolicy.shouldAttemptExplicitRetry(
            active = true, requestedEnabled = true, binding = false, hasConnection = false,
            retryScheduled = false, binderReady = true, permissionGranted = false, serverVersion = 13
        ))
        assertFalse(UserServiceReconnectPolicy.shouldAttemptExplicitRetry(
            active = true, requestedEnabled = true, binding = false, hasConnection = false,
            retryScheduled = false, binderReady = true, permissionGranted = true, serverVersion = 9
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroAttempt() {
        UserServiceReconnectPolicy.delayMillis(0)
    }
}
