package com.example.keymapper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputBindingPolicyTest {
    @Test
    fun mapsFiveMouseButtonsToStableCodesWhenReportedByTheDriver() {
        assertEquals(-1, InputBindingPolicy.mouseButtonCode(1))
        assertEquals(-2, InputBindingPolicy.mouseButtonCode(2))
        assertEquals(-3, InputBindingPolicy.mouseButtonCode(4))
        assertEquals(-4, InputBindingPolicy.mouseButtonCode(8))
        assertEquals(-5, InputBindingPolicy.mouseButtonCode(16))
        assertNull(InputBindingPolicy.mouseButtonCode(32))
        assertEquals(1, InputBindingPolicy.mouseButtonMask(-1))
        assertEquals(8, InputBindingPolicy.mouseButtonMask(-4))
        assertEquals(16, InputBindingPolicy.mouseButtonMask(-5))
        assertNull(InputBindingPolicy.mouseButtonMask(-6))
    }

    @Test
    fun mixedButtonStateUsesStablePriorityAcrossSupportedButtons() {
        assertEquals(-1, InputBindingPolicy.firstMouseButtonCode(7))
        assertEquals(-2, InputBindingPolicy.firstMouseButtonCode(6))
        assertEquals(-3, InputBindingPolicy.firstMouseButtonCode(4))
        assertEquals(-4, InputBindingPolicy.firstMouseButtonCode(8))
        assertEquals(-5, InputBindingPolicy.firstMouseButtonCode(16))
        assertNull(InputBindingPolicy.firstMouseButtonCode(0))
    }
}

