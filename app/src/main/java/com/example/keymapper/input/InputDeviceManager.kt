package com.example.keymapper.input

import android.view.InputDevice

object InputDeviceManager {
    @JvmStatic
    fun hasSource(device: InputDevice?, source: Int): Boolean {
        return device?.supportsSource(source) == true
    }
}
