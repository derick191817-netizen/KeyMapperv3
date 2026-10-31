package com.example.keymapper.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayTargetPolicyTest {
    @Test fun internalModeAlwaysUsesDefaultDisplay() {
        assertEquals(0, DisplayTargetPolicy.resolve(false, 0, 4))
        assertEquals(0, DisplayTargetPolicy.resolve(false, 0, null))
    }

    @Test fun externalModeUsesOnlyTheSelectedExternalDisplay() {
        assertEquals(4, DisplayTargetPolicy.resolve(true, 0, 4))
    }

    @Test fun missingExternalDisplayDoesNotFallbackToInternal() {
        assertNull(DisplayTargetPolicy.resolve(true, 0, null))
    }

    @Test fun invalidOrDefaultDisplayCannotMasqueradeAsExternal() {
        assertNull(DisplayTargetPolicy.resolve(true, 0, 0))
        assertNull(DisplayTargetPolicy.resolve(true, 0, -1))
        assertNull(DisplayTargetPolicy.resolve(false, -1, 4))
    }
}
