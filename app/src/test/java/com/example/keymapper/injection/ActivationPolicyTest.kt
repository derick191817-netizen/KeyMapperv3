package com.example.keymapper.injection

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivationPolicyTest {
    @Test fun missingShizukuTakesPriority() {
        assertEquals(
            ActivationState.SHIZUKU_MISSING,
            ActivationPolicy.resolve(false, false, false, false, false)
        )
    }

    @Test fun stoppedBinderIsNotReady() {
        assertEquals(
            ActivationState.SHIZUKU_STOPPED,
            ActivationPolicy.resolve(true, false, false, false, false)
        )
    }

    @Test fun appPermissionIsCheckedBeforeRemotePermission() {
        assertEquals(
            ActivationState.NEEDS_PERMISSION,
            ActivationPolicy.resolve(true, true, false, false, true)
        )
    }

    @Test fun injectionPermissionIsIndependentFromShizukuAppPermission() {
        assertEquals(
            ActivationState.INPUT_PERMISSION_MISSING,
            ActivationPolicy.resolve(true, true, true, false, true)
        )
    }

    @Test fun inputBinderMustExistBeforeReady() {
        assertEquals(
            ActivationState.INPUT_SERVICE_UNAVAILABLE,
            ActivationPolicy.resolve(true, true, true, true, false)
        )
    }

    @Test fun readyRequiresEveryCapability() {
        assertEquals(
            ActivationState.READY,
            ActivationPolicy.resolve(true, true, true, true, true)
        )
    }
}
