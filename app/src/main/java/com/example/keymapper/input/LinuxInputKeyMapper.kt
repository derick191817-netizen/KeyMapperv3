package com.example.keymapper.input

import android.view.KeyEvent

/** Maps Linux evdev keyboard codes to the Android key codes already used by KeyMapper profiles. */
object LinuxInputKeyMapper {
    private val codes = buildMap {
        putAll(mapOf(
            1 to KeyEvent.KEYCODE_ESCAPE,
            2 to KeyEvent.KEYCODE_1, 3 to KeyEvent.KEYCODE_2, 4 to KeyEvent.KEYCODE_3,
            5 to KeyEvent.KEYCODE_4, 6 to KeyEvent.KEYCODE_5, 7 to KeyEvent.KEYCODE_6,
            8 to KeyEvent.KEYCODE_7, 9 to KeyEvent.KEYCODE_8, 10 to KeyEvent.KEYCODE_9,
            11 to KeyEvent.KEYCODE_0,
            12 to KeyEvent.KEYCODE_MINUS, 13 to KeyEvent.KEYCODE_EQUALS,
            14 to KeyEvent.KEYCODE_DEL, 15 to KeyEvent.KEYCODE_TAB,
            16 to KeyEvent.KEYCODE_Q, 17 to KeyEvent.KEYCODE_W, 18 to KeyEvent.KEYCODE_E,
            19 to KeyEvent.KEYCODE_R, 20 to KeyEvent.KEYCODE_T, 21 to KeyEvent.KEYCODE_Y,
            22 to KeyEvent.KEYCODE_U, 23 to KeyEvent.KEYCODE_I, 24 to KeyEvent.KEYCODE_O,
            25 to KeyEvent.KEYCODE_P, 26 to KeyEvent.KEYCODE_LEFT_BRACKET,
            27 to KeyEvent.KEYCODE_RIGHT_BRACKET, 28 to KeyEvent.KEYCODE_ENTER,
            29 to KeyEvent.KEYCODE_CTRL_LEFT, 30 to KeyEvent.KEYCODE_A, 31 to KeyEvent.KEYCODE_S,
            32 to KeyEvent.KEYCODE_D, 33 to KeyEvent.KEYCODE_F, 34 to KeyEvent.KEYCODE_G,
            35 to KeyEvent.KEYCODE_H, 36 to KeyEvent.KEYCODE_J, 37 to KeyEvent.KEYCODE_K,
            38 to KeyEvent.KEYCODE_L, 39 to KeyEvent.KEYCODE_SEMICOLON,
            40 to KeyEvent.KEYCODE_APOSTROPHE, 41 to KeyEvent.KEYCODE_GRAVE,
            42 to KeyEvent.KEYCODE_SHIFT_LEFT, 43 to KeyEvent.KEYCODE_BACKSLASH,
            44 to KeyEvent.KEYCODE_Z, 45 to KeyEvent.KEYCODE_X, 46 to KeyEvent.KEYCODE_C,
            47 to KeyEvent.KEYCODE_V, 48 to KeyEvent.KEYCODE_B, 49 to KeyEvent.KEYCODE_N,
            50 to KeyEvent.KEYCODE_M, 51 to KeyEvent.KEYCODE_COMMA, 52 to KeyEvent.KEYCODE_PERIOD,
            53 to KeyEvent.KEYCODE_SLASH, 54 to KeyEvent.KEYCODE_SHIFT_RIGHT,
            55 to KeyEvent.KEYCODE_NUMPAD_MULTIPLY, 56 to KeyEvent.KEYCODE_ALT_LEFT,
            57 to KeyEvent.KEYCODE_SPACE, 58 to KeyEvent.KEYCODE_CAPS_LOCK,
            59 to KeyEvent.KEYCODE_F1, 60 to KeyEvent.KEYCODE_F2, 61 to KeyEvent.KEYCODE_F3,
            62 to KeyEvent.KEYCODE_F4, 63 to KeyEvent.KEYCODE_F5, 64 to KeyEvent.KEYCODE_F6,
            65 to KeyEvent.KEYCODE_F7, 66 to KeyEvent.KEYCODE_F8, 67 to KeyEvent.KEYCODE_F9,
            68 to KeyEvent.KEYCODE_F10, 87 to KeyEvent.KEYCODE_F11, 88 to KeyEvent.KEYCODE_F12,
            71 to KeyEvent.KEYCODE_NUMPAD_7, 72 to KeyEvent.KEYCODE_NUMPAD_8,
            73 to KeyEvent.KEYCODE_NUMPAD_9, 75 to KeyEvent.KEYCODE_NUMPAD_4,
            76 to KeyEvent.KEYCODE_NUMPAD_5, 77 to KeyEvent.KEYCODE_NUMPAD_6,
            79 to KeyEvent.KEYCODE_NUMPAD_1, 80 to KeyEvent.KEYCODE_NUMPAD_2,
            81 to KeyEvent.KEYCODE_NUMPAD_3, 82 to KeyEvent.KEYCODE_NUMPAD_0,
            83 to KeyEvent.KEYCODE_NUMPAD_DOT, 74 to KeyEvent.KEYCODE_NUMPAD_SUBTRACT,
            78 to KeyEvent.KEYCODE_NUMPAD_ADD, 96 to KeyEvent.KEYCODE_NUMPAD_ENTER,
            97 to KeyEvent.KEYCODE_CTRL_RIGHT, 98 to KeyEvent.KEYCODE_NUMPAD_DIVIDE,
            100 to KeyEvent.KEYCODE_ALT_RIGHT, 102 to KeyEvent.KEYCODE_MOVE_HOME,
            103 to KeyEvent.KEYCODE_DPAD_UP, 104 to KeyEvent.KEYCODE_PAGE_UP,
            105 to KeyEvent.KEYCODE_DPAD_LEFT, 106 to KeyEvent.KEYCODE_DPAD_RIGHT,
            107 to KeyEvent.KEYCODE_MOVE_END, 108 to KeyEvent.KEYCODE_DPAD_DOWN,
            109 to KeyEvent.KEYCODE_PAGE_DOWN, 110 to KeyEvent.KEYCODE_INSERT,
            111 to KeyEvent.KEYCODE_FORWARD_DEL, 114 to KeyEvent.KEYCODE_VOLUME_DOWN,
            115 to KeyEvent.KEYCODE_VOLUME_UP, 125 to KeyEvent.KEYCODE_META_LEFT,
            126 to KeyEvent.KEYCODE_META_RIGHT, 139 to KeyEvent.KEYCODE_MENU,
            158 to KeyEvent.KEYCODE_BACK
        ))
    }

    fun toAndroidKeyCode(linuxCode: Int): Int? = codes[linuxCode]
}
