package com.example.keymapper.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputStatePolicyTest {
    @Test fun captureAndRelativeInputAreTrackedAsSeparateCapabilities() {
        assertFalse(InputStatePolicy.isCaptureConfirmed(InputState.GRAB_REQUESTED))
        assertTrue(InputStatePolicy.isCaptureConfirmed(InputState.GRABBED))
        assertTrue(InputStatePolicy.isReceivingRelativeInput(InputState.RELATIVE_ACTIVE))
        assertFalse(InputStatePolicy.isReceivingRelativeInput(InputState.GRABBED))
        assertFalse(InputStatePolicy.isCaptureConfirmed(InputState.RELATIVE_FALLBACK_ACTIVE))
        assertTrue(InputStatePolicy.isReceivingRelativeInput(InputState.RELATIVE_FALLBACK_ACTIVE))
    }

    @Test fun normalCaptureLifecycleIsAllowed() {
        assertTrue(InputStatePolicy.isAllowed(InputState.FREE, InputState.GRAB_REQUESTED))
        assertTrue(InputStatePolicy.isAllowed(InputState.GRAB_REQUESTED, InputState.GRABBED))
        assertTrue(InputStatePolicy.isAllowed(InputState.GRABBED, InputState.RELATIVE_ACTIVE))
        assertTrue(InputStatePolicy.isAllowed(InputState.RELATIVE_ACTIVE, InputState.RELEASE_REQUESTED))
        assertTrue(InputStatePolicy.isAllowed(InputState.RELEASE_REQUESTED, InputState.RELEASED))
    }

    @Test fun losingFocusAndDisconnectCanAlwaysSuspendOrDisconnect() {
        assertTrue(InputStatePolicy.isAllowed(InputState.RELATIVE_ACTIVE, InputState.SUSPENDED))
        assertTrue(InputStatePolicy.isAllowed(InputState.GRABBED, InputState.DISCONNECTED))
    }

    @Test fun lifecycleCanStartFromInitialErrorAndDisconnectStates() {
        assertTrue(InputStatePolicy.isAllowed(InputState.INACTIVE, InputState.GRAB_REQUESTED))
        assertTrue(InputStatePolicy.isAllowed(InputState.ERROR, InputState.GRAB_REQUESTED))
        assertTrue(InputStatePolicy.isAllowed(InputState.DISCONNECTED, InputState.GRAB_REQUESTED))
        assertTrue(InputStatePolicy.isAllowed(InputState.CONFIGURATION, InputState.GRAB_REQUESTED))
    }

    @Test fun relativeFallbackCanRecoverFromPendingOrTimedOutPointerCapture() {
        assertTrue(InputStatePolicy.isAllowed(InputState.GRAB_REQUESTED, InputState.RELATIVE_FALLBACK_ACTIVE))
        assertTrue(InputStatePolicy.isAllowed(InputState.ERROR, InputState.RELATIVE_FALLBACK_ACTIVE))
        assertTrue(InputStatePolicy.isAllowed(InputState.RELATIVE_FALLBACK_ACTIVE, InputState.GRABBED))
        assertTrue(InputStatePolicy.isAllowed(InputState.RELATIVE_FALLBACK_ACTIVE, InputState.RELEASE_REQUESTED))
    }

    @Test fun captureTimeoutIsSuppressedOnlyWhenRelativeFallbackIsWorking() {
        assertFalse(InputStatePolicy.shouldReportCaptureTimeout(false, true, false, false))
        assertFalse(InputStatePolicy.shouldReportCaptureTimeout(true, false, false, false))
        assertFalse(InputStatePolicy.shouldReportCaptureTimeout(true, true, true, false))
        assertFalse(InputStatePolicy.shouldReportCaptureTimeout(true, true, false, true))
        assertTrue(InputStatePolicy.shouldReportCaptureTimeout(true, true, false, false))
    }

    @Test fun captureLossCanReturnToRequestedWithoutSkippingConfirmation() {
        assertTrue(InputStatePolicy.isAllowed(InputState.GRABBED, InputState.GRAB_REQUESTED))
        assertTrue(InputStatePolicy.isAllowed(InputState.RELATIVE_ACTIVE, InputState.GRAB_REQUESTED))
        assertTrue(InputStatePolicy.isAllowed(InputState.SUSPENDED, InputState.GRAB_REQUESTED))
        assertFalse(InputStatePolicy.isAllowed(InputState.RELEASE_REQUESTED, InputState.GRABBED))
        assertTrue(InputStatePolicy.isAllowed(InputState.RELEASE_REQUESTED, InputState.RELEASED))
    }
}
