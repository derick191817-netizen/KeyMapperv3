package com.example.keymapper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumericSettingPolicyTest {
    @Test fun integerValuesAreTrimmedAndInclusive() {
        assertEquals(0, NumericSettingPolicy.intValue(" 0 ", 0..200))
        assertEquals(200, NumericSettingPolicy.intValue("200", 0..200))
    }

    @Test fun integerRejectsPartialAndOutOfRangeValues() {
        assertNull(NumericSettingPolicy.intValue("", 0..200))
        assertNull(NumericSettingPolicy.intValue("1.5", 0..200))
        assertNull(NumericSettingPolicy.intValue("201", 0..200))
        assertNull(NumericSettingPolicy.intValue("-1", 0..200))
    }

    @Test fun decimalAcceptsDotAndCommaSeparators() {
        assertEquals(0.9f, NumericSettingPolicy.floatValue("0.9", 0.01f, 10f)!!, 0f)
        assertEquals(0.9f, NumericSettingPolicy.floatValue(" 0,9 ", 0.01f, 10f)!!, 0f)
    }

    @Test fun decimalRejectsIntermediateAndMalformedInput() {
        assertNull(NumericSettingPolicy.floatValue("", 0f, 10f))
        assertNull(NumericSettingPolicy.floatValue("-", 0f, 10f))
        assertNull(NumericSettingPolicy.floatValue(",", 0f, 10f))
        assertNull(NumericSettingPolicy.floatValue("-.", 0f, 10f))
        assertNull(NumericSettingPolicy.floatValue("1,2,3", 0f, 10f))
        assertNull(NumericSettingPolicy.floatValue("abc", 0f, 10f))
    }

    @Test fun decimalRejectsNonFiniteAndOutOfRangeValues() {
        assertNull(NumericSettingPolicy.floatValue("NaN", 0f, 10f))
        assertNull(NumericSettingPolicy.floatValue("Infinity", 0f, 10f))
        assertNull(NumericSettingPolicy.floatValue("-0.1", 0f, 10f))
        assertNull(NumericSettingPolicy.floatValue("10.1", 0f, 10f))
    }

    @Test fun invalidRangeIsRejected() {
        assertNull(NumericSettingPolicy.floatValue("1", 10f, 0f))
        assertNull(NumericSettingPolicy.floatValue("1", Float.NaN, 10f))
    }
}
