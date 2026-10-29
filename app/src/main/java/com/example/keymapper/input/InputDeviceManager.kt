package com.example.keymapper.input

import android.view.InputDevice

object InputDeviceManager {

    @JvmStatic
    fun hasSource(device: InputDevice?, source: Int): Boolean {
        // A função correta do Android é supportsSource, não hasSource
        return device?.supportsSource(source) == true
    }
}
