package com.example.keymapper.input

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureRetryPolicyTest {
    @Test fun noRetryWhenCaptureIsNotRequestedOrWindowHasNoFocus() {
        assertEquals(CaptureRetryPolicy.Action.NO_ACTION, CaptureRetryPolicy.decide(false, true, false, false, 0, 3))
        assertEquals(CaptureRetryPolicy.Action.NO_ACTION, CaptureRetryPolicy.decide(true, false, false, false, 0, 3))
    }

    @Test fun confirmedCaptureNeedsNoRecovery() {
        assertEquals(CaptureRetryPolicy.Action.NO_ACTION, CaptureRetryPolicy.decide(true, true, true, false, 0, 3))
    }

    @Test fun workingRelativeFallbackPreventsFalseFailure() {
        assertEquals(CaptureRetryPolicy.Action.WAIT_FOR_FALLBACK, CaptureRetryPolicy.decide(true, true, false, true, 3, 3))
    }

    @Test fun recoveryUsesOnlyTheConfiguredNumberOfRetries() {
        assertEquals(CaptureRetryPolicy.Action.RETRY, CaptureRetryPolicy.decide(true, true, false, false, 0, 3))
        assertEquals(CaptureRetryPolicy.Action.RETRY, CaptureRetryPolicy.decide(true, true, false, false, 2, 3))
        assertEquals(CaptureRetryPolicy.Action.FAIL, CaptureRetryPolicy.decide(true, true, false, false, 3, 3))
    }

    @Test fun zeroRetriesFailsImmediatelyAndNegativeRetriesAreNormalized() {
        assertEquals(CaptureRetryPolicy.Action.FAIL, CaptureRetryPolicy.decide(true, true, false, false, 0, 0))
        assertEquals(CaptureRetryPolicy.Action.FAIL, CaptureRetryPolicy.decide(true, true, false, false, -1, -4))
    }
}
