package com.example.keymapper.input

import android.view.InputDevice
import android.view.MotionEvent

/** Leitura observacional do dispositivo que originou um evento; não concede acesso global. */
object InputDeviceManager {
    data class DeviceInfo(val id: Int, val name: String, val sources: Int, val virtual: Boolean)

    fun from(event: MotionEvent): DeviceInfo? {
        val id = event.deviceId
        val device = InputDevice.getDevice(id) ?: return if (id < 0) DeviceInfo(id, "Dispositivo desconhecido", event.source, true) else null
        return DeviceInfo(device.id, device.name ?: "Mouse sem nome", device.sources, device.isVirtual)
    }

    fun isMouse(event: MotionEvent): Boolean =
        (event.source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
        (event.source and InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE
}
