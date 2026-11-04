package com.example.keymapper.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinuxInputKeyMapperTest {
    @Test
    fun mapsCommonLinuxMediaAndConsumerKeys() {
        assertEquals(KeyEvent.KEYCODE_VOLUME_MUTE, LinuxInputKeyMapper.toAndroidKeyCode(113))
        assertEquals(KeyEvent.KEYCODE_MEDIA_PAUSE, LinuxInputKeyMapper.toAndroidKeyCode(119))
        assertEquals(KeyEvent.KEYCODE_MEDIA_NEXT, LinuxInputKeyMapper.toAndroidKeyCode(163))
        assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, LinuxInputKeyMapper.toAndroidKeyCode(164))
        assertEquals(KeyEvent.KEYCODE_MEDIA_PREVIOUS, LinuxInputKeyMapper.toAndroidKeyCode(165))
        assertEquals(KeyEvent.KEYCODE_MEDIA_STOP, LinuxInputKeyMapper.toAndroidKeyCode(166))
        assertEquals(KeyEvent.KEYCODE_MEDIA_RECORD, LinuxInputKeyMapper.toAndroidKeyCode(167))
        assertEquals(KeyEvent.KEYCODE_MEDIA_REWIND, LinuxInputKeyMapper.toAndroidKeyCode(168))
        assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY, LinuxInputKeyMapper.toAndroidKeyCode(200))
        assertEquals(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, LinuxInputKeyMapper.toAndroidKeyCode(208))
    }

    @Test
    fun mapsConsumerNavigationKeysAboveBtnMisc() {
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, LinuxInputKeyMapper.toAndroidKeyCode(352))
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, LinuxInputKeyMapper.toAndroidKeyCode(353))
        assertEquals(KeyEvent.KEYCODE_INFO, LinuxInputKeyMapper.toAndroidKeyCode(358))
        assertEquals(KeyEvent.KEYCODE_GUIDE, LinuxInputKeyMapper.toAndroidKeyCode(365))
        assertEquals(KeyEvent.KEYCODE_CHANNEL_UP, LinuxInputKeyMapper.toAndroidKeyCode(402))
        assertEquals(KeyEvent.KEYCODE_CHANNEL_DOWN, LinuxInputKeyMapper.toAndroidKeyCode(403))
    }

    @Test
    fun leavesUnknownAndButtonCodesUnmapped() {
        assertNull(LinuxInputKeyMapper.toAndroidKeyCode(0))
        assertNull(LinuxInputKeyMapper.toAndroidKeyCode(272)) // BTN_LEFT
        assertNull(LinuxInputKeyMapper.toAndroidKeyCode(304)) // BTN_SOUTH
    }
}
