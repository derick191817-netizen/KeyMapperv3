package com.example.keymapper.input

/** Linux evdev codes transported by the privileged JNI capture service. */
object LinuxEvdevEvent {
    const val RECORD_FIELDS = 6
    const val DEVICE_MOUSE = 1
    const val DEVICE_KEYBOARD = 2
    const val EV_SYN = 0
    const val EV_KEY = 1
    const val EV_REL = 2
    const val SYN_REPORT = 0
    const val SYN_DROPPED = 3
    const val REL_X = 0
    const val REL_Y = 1
    const val REL_HWHEEL = 6
    const val REL_WHEEL = 8
    const val REL_WHEEL_HI_RES = 11
    const val REL_HWHEEL_HI_RES = 12
    const val BTN_LEFT = 272
    const val BTN_RIGHT = 273
    const val BTN_MIDDLE = 274
    const val BTN_SIDE = 275
    const val BTN_EXTRA = 276
    const val BTN_FORWARD = 277
    const val BTN_BACK = 278

    /** Converts Linux wheel events to detents; hi-res units are 1/120 of a detent. */
    fun verticalScrollDetents(code: Int, value: Int): Float? = when (code) {
        REL_WHEEL -> value.toFloat()
        REL_WHEEL_HI_RES -> value / 120f
        else -> null
    }

    fun horizontalScrollDetents(code: Int, value: Int): Float? = when (code) {
        REL_HWHEEL -> value.toFloat()
        REL_HWHEEL_HI_RES -> value / 120f
        else -> null
    }

    fun mouseButtonMask(code: Int): Int? = when (code) {
        BTN_LEFT -> 1
        BTN_RIGHT -> 2
        BTN_MIDDLE -> 4
        BTN_SIDE, BTN_BACK -> 8
        BTN_EXTRA, BTN_FORWARD -> 16
        else -> null
    }
}
