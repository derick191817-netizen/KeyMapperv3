package com.example.keymapper.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinuxEvdevEventTest {
    @Test
    fun mapsCommonKeyboardCodesToAndroidKeyCodes() {
        assertEquals(KeyEvent.KEYCODE_W, LinuxInputKeyMapper.toAndroidKeyCode(17))
        assertEquals(KeyEvent.KEYCODE_A, LinuxInputKeyMapper.toAndroidKeyCode(30))
        assertEquals(KeyEvent.KEYCODE_SPACE, LinuxInputKeyMapper.toAndroidKeyCode(57))
        assertEquals(KeyEvent.KEYCODE_ENTER, LinuxInputKeyMapper.toAndroidKeyCode(28))
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, LinuxInputKeyMapper.toAndroidKeyCode(103))
    }

    @Test
    fun convertsHighResolutionWheelUnitsToFractionalDetents() {
        assertEquals(1f, LinuxEvdevEvent.verticalScrollDetents(LinuxEvdevEvent.REL_WHEEL, 1)!!, 0f)
        assertEquals(0.25f, LinuxEvdevEvent.verticalScrollDetents(LinuxEvdevEvent.REL_WHEEL_HI_RES, 30)!!, 0.0001f)
        assertEquals(-1f, LinuxEvdevEvent.verticalScrollDetents(LinuxEvdevEvent.REL_WHEEL_HI_RES, -120)!!, 0f)
        assertEquals(0.5f, LinuxEvdevEvent.horizontalScrollDetents(LinuxEvdevEvent.REL_HWHEEL_HI_RES, 60)!!, 0.0001f)
        assertNull(LinuxEvdevEvent.verticalScrollDetents(LinuxEvdevEvent.REL_X, 20))
        assertNull(LinuxEvdevEvent.horizontalScrollDetents(LinuxEvdevEvent.REL_Y, 20))
    }

    @Test
    fun mapsMouseButtonsWithoutInventingUnknownButtons() {
        assertEquals(1, LinuxEvdevEvent.mouseButtonMask(LinuxEvdevEvent.BTN_LEFT))
        assertEquals(2, LinuxEvdevEvent.mouseButtonMask(LinuxEvdevEvent.BTN_RIGHT))
        assertEquals(4, LinuxEvdevEvent.mouseButtonMask(LinuxEvdevEvent.BTN_MIDDLE))
        assertEquals(8, LinuxEvdevEvent.mouseButtonMask(LinuxEvdevEvent.BTN_SIDE))
        assertEquals(8, LinuxEvdevEvent.mouseButtonMask(LinuxEvdevEvent.BTN_BACK))
        assertEquals(16, LinuxEvdevEvent.mouseButtonMask(LinuxEvdevEvent.BTN_EXTRA))
        assertEquals(16, LinuxEvdevEvent.mouseButtonMask(LinuxEvdevEvent.BTN_FORWARD))
        assertNull(LinuxEvdevEvent.mouseButtonMask(9999))
    }
}
