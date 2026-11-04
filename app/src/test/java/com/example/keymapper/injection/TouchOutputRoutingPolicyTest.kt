package com.example.keymapper.injection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchOutputRoutingPolicyTest {
    @Test
    fun uinputIsSelectedOnlyOnPrimaryDisplayWhenAvailable() {
        assertEquals(
            TouchOutputTransport.UINPUT,
            TouchOutputRoutingPolicy.select(displayId = 0, uinputAvailable = true)
        )
        assertTrue(TouchOutputRoutingPolicy.canContinue(TouchOutputTransport.UINPUT, displayId = 0))
    }

    @Test
    fun secondaryDisplaysAlwaysUseInputManager() {
        assertEquals(
            TouchOutputTransport.INPUT_MANAGER,
            TouchOutputRoutingPolicy.select(displayId = 2, uinputAvailable = true)
        )
        assertFalse(TouchOutputRoutingPolicy.canContinue(TouchOutputTransport.UINPUT, displayId = 2))
    }

    @Test
    fun invalidOrExternalDisplayNeverSelectsUinput() {
        assertEquals(
            TouchOutputTransport.INPUT_MANAGER,
            TouchOutputRoutingPolicy.select(displayId = -1, uinputAvailable = true)
        )
        assertEquals(
            TouchOutputTransport.INPUT_MANAGER,
            TouchOutputRoutingPolicy.select(displayId = 1, uinputAvailable = true)
        )
    }

    @Test
    fun unavailableUinputFallsBackToInputManager() {
        assertEquals(
            TouchOutputTransport.INPUT_MANAGER,
            TouchOutputRoutingPolicy.select(displayId = 0, uinputAvailable = false)
        )
        assertTrue(TouchOutputRoutingPolicy.canContinue(TouchOutputTransport.INPUT_MANAGER, displayId = 2))
    }
}
