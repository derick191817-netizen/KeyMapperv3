package com.example.keymapper.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent

/** Inspeção dos dispositivos que o Android já reconhece; não concede acesso global à entrada. */
object InputDeviceManager {
    data class DeviceInfo(
        val id: Int,
        val name: String,
        val sources: Int,
        val virtual: Boolean,
        val external: Boolean,
        val keyboard: Boolean,
        val mouse: Boolean,
        val gamepad: Boolean
    )

    data class Snapshot(val devices: List<DeviceInfo>) {
        val keyboards: List<DeviceInfo> get() = devices.filter { it.keyboard }
        val mice: List<DeviceInfo> get() = devices.filter { it.mouse }
        val hasPhysicalKeyboard: Boolean get() = keyboards.any { !it.virtual }
        val hasMouse: Boolean get() = mice.isNotEmpty()
        fun summary(): String = buildString {
            append("${mice.size} mouse(s), ${keyboards.size} teclado(s)")
            if (devices.isEmpty()) append("; nenhum periférico de entrada listado")
            else append("; ${devices.count { !it.virtual }} dispositivo(s) não virtual(is)")
        }
    }

    fun from(event: MotionEvent): DeviceInfo? {
        val id = event.deviceId
        val device = InputDevice.getDevice(id)
        if (device == null) {
            return if (id < 0) DeviceInfo(id, "Dispositivo desconhecido", event.source, true, false, false, isMouseSource(event.source), false) else null
        }
        return describe(device)
    }

    fun snapshot(): Snapshot = Snapshot(InputDevice.getDeviceIds().mapNotNull { id ->
        InputDevice.getDevice(id)?.let(::describe)
    }.sortedWith(compareBy<DeviceInfo> { !it.mouse }.thenBy { !it.keyboard }.thenBy { it.name }))

    /** Observa conexão/desconexão. O chamador deve chamar close() no ciclo de vida adequado. */
    fun observe(context: Context, onChanged: (Snapshot) -> Unit): AutoCloseable {
        val manager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val handler = Handler(Looper.getMainLooper())
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = publish()
            override fun onInputDeviceRemoved(deviceId: Int) = publish()
            override fun onInputDeviceChanged(deviceId: Int) = publish()
            private fun publish() { handler.post { onChanged(snapshot()) } }
        }
        manager.registerInputDeviceListener(listener, handler)
        handler.post { onChanged(snapshot()) }
        return AutoCloseable { manager.unregisterInputDeviceListener(listener) }
    }

    fun isMouse(event: MotionEvent): Boolean = isMouseSource(event.source)

    private fun isMouseSource(source: Int): Boolean =
        (source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
            (source and InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE

    private fun describe(device: InputDevice): DeviceInfo {
        val keyboard = device.hasSource(InputDevice.SOURCE_KEYBOARD)
        val mouse = device.hasSource(InputDevice.SOURCE_MOUSE) || device.hasSource(InputDevice.SOURCE_MOUSE_RELATIVE)
        val gamepad = device.hasSource(InputDevice.SOURCE_GAMEPAD) || device.hasSource(InputDevice.SOURCE_JOYSTICK)
        return DeviceInfo(
            id = device.id,
            name = device.name?.takeIf { it.isNotBlank() } ?: "Dispositivo sem nome",
            sources = device.sources,
            virtual = device.isVirtual,
            external = if (Build.VERSION.SDK_INT >= 29) device.isExternal else false,
            keyboard = keyboard,
            mouse = mouse,
            gamepad = gamepad
        )
    }
}
