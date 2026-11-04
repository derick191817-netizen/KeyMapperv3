package com.example.keymapper.input

import org.junit.Assert.assertEquals
import org.junit.Test

class MouseButtonStatePolicyTest {
    @Test fun pressActionAddsButtonEvenWhenSnapshotIsStale() {
        assertEquals(1, MouseButtonStatePolicy.resolve(0, actionButton = 1, pressed = true))
    }

    @Test fun releaseActionRemovesButtonEvenWhenSnapshotIsStale() {
        assertEquals(0, MouseButtonStatePolicy.resolve(1, actionButton = 1, pressed = false))
    }

    @Test fun preservesOtherHeldButtons() {
        assertEquals(3, MouseButtonStatePolicy.resolve(2, actionButton = 1, pressed = true))
        assertEquals(2, MouseButtonStatePolicy.resolve(3, actionButton = 1, pressed = false))
    }

    @Test fun ignoresUnknownButtonBits() {
        assertEquals(1, MouseButtonStatePolicy.resolve(1 or 0x400, actionButton = 0x400, pressed = true))
    }

    @Test fun usesSnapshotForOrdinaryMotionEvents() {
        assertEquals(5, MouseButtonStatePolicy.resolve(5))
    }
}
