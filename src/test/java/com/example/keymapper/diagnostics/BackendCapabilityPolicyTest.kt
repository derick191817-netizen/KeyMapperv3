package com.example.keymapper.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackendCapabilityPolicyTest {
    @Test fun missingNativeModuleIsNeverReportedAsImplemented() {
        val result = BackendCapabilityPolicy.assess(false, true, true)
        assertEquals(BackendCapabilityLevel.NOT_IMPLEMENTED, result.level)
    }

    @Test fun modulePresenceDoesNotMeanBackendWasInitialized() {
        val result = BackendCapabilityPolicy.assess(true, false, true)
        assertEquals(BackendCapabilityLevel.MODULE_PRESENT_NOT_INITIALIZED, result.level)
    }

    @Test fun initializationDoesNotMeanEndToEndWasVerified() {
        val result = BackendCapabilityPolicy.assess(true, true, false)
        assertEquals(BackendCapabilityLevel.INITIALIZED_NOT_VERIFIED, result.level)
    }

    @Test fun onlyEndToEndVerificationProducesDeviceVerifiedState() {
        val result = BackendCapabilityPolicy.assess(true, true, true)
        assertEquals(BackendCapabilityLevel.VERIFIED_ON_DEVICE, result.level)
    }

    @Test fun shizukuGrantAloneNeverConfirmsKernelNodeAccess() {
        assertFalse(BackendCapabilityPolicy.shizukuGrantAloneConfirmsKernelNodeAccess())
    }
}
