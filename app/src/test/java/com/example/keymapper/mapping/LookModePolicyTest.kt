package com.example.keymapper.mapping

import com.example.keymapper.data.Rot
import org.junit.Assert.assertEquals
import org.junit.Test

class LookModePolicyTest {
    @Test fun automaticModePrioritizesTouchReanchor() {
        assertEquals(Rot.CENTER, LookModePolicy.resolve(Rot.AUTO, -1, false))
    }

    @Test fun nativeRelativeModeMustBeExplicit() {
        assertEquals(Rot.INFINITE, LookModePolicy.resolve(Rot.INFINITE, -1, false))
        assertEquals(Rot.INFINITE, LookModePolicy.resolve(Rot.AUTO, -1, true))
    }

    @Test fun buttonModeOverridesGlobalMode() {
        assertEquals(Rot.INFINITE, LookModePolicy.resolve(Rot.CENTER, Rot.INFINITE, false))
        assertEquals(Rot.CENTER, LookModePolicy.resolve(Rot.OFF, Rot.CENTER, false))
    }

    @Test fun offAlwaysWinsOverRelativePreference() {
        assertEquals(Rot.OFF, LookModePolicy.resolve(Rot.OFF, -1, true))
        assertEquals(Rot.OFF, LookModePolicy.resolve(Rot.AUTO, Rot.OFF, true))
    }
}
