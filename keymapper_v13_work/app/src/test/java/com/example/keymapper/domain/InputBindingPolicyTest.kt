package com.example.keymapper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputBindingPolicyTest {
    @Test
    fun mapsTheThreeSupportedMouseButtonsToStableCodes() {
        assertEquals(-1, InputBindingPolicy.mouseButtonCode(1))
        assertEquals(-2, InputBindingPolicy.mouseButtonCode(2))
        assertEquals(-3, InputBindingPolicy.mouseButtonCode(4))
        assertNull(InputBindingPolicy.mouseButtonCode(8))
    }

    @Test
    fun mixedButtonStateChoosesPrimaryThenSecondaryThenMiddle() {
        assertEquals(-1, InputBindingPolicy.firstMouseButtonCode(7))
        assertEquals(-2, InputBindingPolicy.firstMouseButtonCode(6))
        assertEquals(-3, InputBindingPolicy.firstMouseButtonCode(4))
        assertNull(InputBindingPolicy.firstMouseButtonCode(0))
    }
}
