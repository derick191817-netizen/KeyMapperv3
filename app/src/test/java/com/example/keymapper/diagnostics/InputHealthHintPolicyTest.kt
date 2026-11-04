package com.example.keymapper.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test

class InputHealthHintPolicyTest {
    private fun state(
        hasMouse: Boolean = true,
        captureRequested: Boolean = true,
        windowFocused: Boolean = true,
        captureConfirmed: Boolean = true,
        mouseReceived: Long = 0,
        mouseReceivedWithoutPointerCapture: Long = 0,
        mouseProcessed: Long = 0,
        mouseForwarded: Long = 0,
        injectionSuccesses: Long = 0,
        injectionFailures: Long = 0
    ) = InputHealthState(
        hasMouse, captureRequested, windowFocused, captureConfirmed,
        mouseReceived, mouseReceivedWithoutPointerCapture, mouseProcessed,
        mouseForwarded, injectionSuccesses, injectionFailures
    )

    @Test fun currentFocusLossWinsOverHistoricalInjectionSuccess() {
        val hint = InputHealthHintPolicy.hint(
            state(windowFocused = false, captureConfirmed = false, injectionSuccesses = 12)
        )
        assertTrue(hint.contains("não está focada"))
    }

    @Test fun disabledCaptureWinsOverHistoricalInjectionSuccess() {
        val hint = InputHealthHintPolicy.hint(
            state(captureRequested = false, captureConfirmed = false, injectionSuccesses = 12)
        )
        assertTrue(hint.contains("captura está desativada"))
    }

    @Test fun relativeFallbackIsNotReportedAsGlobalCapture() {
        val hint = InputHealthHintPolicy.hint(
            state(
                captureConfirmed = false,
                mouseReceived = 3,
                mouseReceivedWithoutPointerCapture = 3,
                mouseProcessed = 3,
                mouseForwarded = 3,
                injectionSuccesses = 3
            )
        )
        assertTrue(hint.contains("não confirma que o jogo recebeu"))
    }

    @Test fun missingMouseIsReportedFirst() {
        val hint = InputHealthHintPolicy.hint(
            state(hasMouse = false, captureRequested = false, injectionSuccesses = 12)
        )
        assertTrue(hint.contains("não lista um mouse"))
    }
}
