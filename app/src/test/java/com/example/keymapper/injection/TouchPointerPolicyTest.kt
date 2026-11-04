package com.example.keymapper.injection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchPointerPolicyTest {
    @Test fun acceptsAndroidPointerIdsAndUpToSixteenConcurrentContacts() {
        assertTrue(TouchPointerPolicy.canStart(0, 0))
        assertTrue(TouchPointerPolicy.canStart(15, 31))
    }


    @Test fun nonstopUsesOverlapOnlyWhenAnotherPointerSlotIsAvailable() {
        assertTrue(TouchHandoffPolicy.useOverlappedHandoff(true, 1, 1))
        assertFalse(TouchHandoffPolicy.useOverlappedHandoff(true, 16, 1))
        assertFalse(TouchHandoffPolicy.useOverlappedHandoff(false, 1, 1))
        assertFalse(TouchHandoffPolicy.useOverlappedHandoff(true, 1, 32))
    }

    @Test fun rejectsSeventeenthContactAndInvalidIds() {
        assertFalse(TouchPointerPolicy.canStart(16, 1))
        assertFalse(TouchPointerPolicy.canStart(-1, 1))
        assertFalse(TouchPointerPolicy.canStart(0, -1))
        assertFalse(TouchPointerPolicy.canStart(0, 32))
    }
}
