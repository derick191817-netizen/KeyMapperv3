package com.example.keymapper.input

import org.junit.Assert.*
import org.junit.Test

class CaptureBackendPolicyTest {
    @Test fun pointerCaptureRequiresFocusAndIsNeverGlobal() {
        val waiting = CaptureBackendPolicy.evaluate(CaptureBackendEnvironment(34, false))
        assertEquals(CaptureBackendKind.ANDROID_POINTER_CAPTURE, waiting.kind)
        assertEquals(CaptureBackendReadiness.WAITING_FOR_WINDOW_FOCUS, waiting.readiness)
        assertFalse(waiting.globalCapture)
        val ready = CaptureBackendPolicy.evaluate(CaptureBackendEnvironment(34, true))
        assertEquals(CaptureBackendReadiness.READY, ready.readiness)
        assertFalse(ready.globalCapture)
    }
    @Test fun oldAndroidWithoutNativeBackendIsUnsupported() {
        val result = CaptureBackendPolicy.evaluate(CaptureBackendEnvironment(25, true))
        assertEquals(CaptureBackendKind.NONE, result.kind)
        assertEquals(CaptureBackendReadiness.UNSUPPORTED, result.readiness)
    }
    @Test fun nativeBackendDoesNotBecomeReadyFromInstallationAlone() {
        val result = CaptureBackendPolicy.evaluate(CaptureBackendEnvironment(34, true, nativeComponentInstalled = true))
        assertEquals(CaptureBackendKind.PRIVILEGED_EVDEV, result.kind)
        assertEquals(CaptureBackendReadiness.WAITING_FOR_DEVICE_ACCESS, result.readiness)
        assertFalse(result.globalCapture)
    }
    @Test fun nativeBackendRequiresBothAccessAndGrabConfirmation() {
        val accessOnly = CaptureBackendPolicy.evaluate(CaptureBackendEnvironment(34, true, true, true, false))
        assertFalse(accessOnly.globalCapture)
        val ready = CaptureBackendPolicy.evaluate(CaptureBackendEnvironment(34, true, true, true, true))
        assertEquals(CaptureBackendReadiness.READY, ready.readiness)
        assertTrue(ready.globalCapture)
    }
}
