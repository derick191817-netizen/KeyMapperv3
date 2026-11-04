package com.example.keymapper.input

import java.util.LinkedHashMap
import java.util.LinkedHashSet

/**
 * Estado físico agregado por dispositivo.
 *
 * Uma tecla/botão só é encaminhado como DOWN quando nenhum dispositivo já o mantém pressionado,
 * e só gera UP quando o último dispositivo o solta. Use uma instância por origem de captura
 * (por exemplo, MotionEvent Android ou evdev nativo). A classe não é thread-safe: o proprietário
 * deve manter todas as operações na mesma thread.
 */
class PhysicalInputState {
    private data class DeviceState(
        val heldKeys: LinkedHashSet<Int> = LinkedHashSet(),
        var buttonMask: Int = 0,
        // Evdev pode expor códigos físicos diferentes que são normalizados para o mesmo bit.
        // Guardar o código de origem impede que o UP de um alias solte o outro.
        val heldButtonCodes: LinkedHashMap<Int, Int> = LinkedHashMap()
    )

    private val devices = LinkedHashMap<Int, DeviceState>()

    /** Retorna true somente na transição global de solto -> pressionado. */
    fun pressKey(deviceId: Int, keyCode: Int): Boolean {
        if (keyCode == 0) return false
        val alreadyHeldGlobally = isKeyHeldAnywhere(keyCode)
        val state = devices.getOrPut(deviceId) { DeviceState() }
        if (!state.heldKeys.add(keyCode)) return false
        return !alreadyHeldGlobally
    }

    /** Retorna true somente na transição global de pressionado -> solto. */
    fun releaseKey(deviceId: Int, keyCode: Int): Boolean {
        val state = devices[deviceId] ?: return false
        if (!state.heldKeys.remove(keyCode)) return false
        val releasedGlobally = devices.values.none { keyCode in it.heldKeys }
        removeIfEmpty(deviceId, state)
        return releasedGlobally
    }

    fun isKeyHeldAnywhere(keyCode: Int): Boolean =
        devices.values.any { keyCode in it.heldKeys }

    /**
     * ACTION_MULTIPLE representa um pulso. Ele só pode ser encaminhado se a tecla não estiver
     * fisicamente retida por nenhum dispositivo, evitando um UP transitório indevido.
     */
    fun canDispatchTransientKey(deviceId: Int, keyCode: Int): Boolean =
        keyCode != 0 && !isKeyHeldAnywhere(keyCode) && devices[deviceId]?.heldKeys?.contains(keyCode) != true

    /**
     * Atualiza o snapshot de botões de um dispositivo e devolve somente bits cuja situação global
     * (OR de todos os dispositivos) realmente mudou.
     */
    fun updateButtons(deviceId: Int, nextMask: Int): Int {
        val previousGlobal = aggregateButtonMask()
        val state = devices.getOrPut(deviceId) { DeviceState() }
        state.buttonMask = nextMask and MouseButtonStatePolicy.KNOWN_BUTTON_MASK
        val changed = previousGlobal xor aggregateButtonMask()
        removeIfEmpty(deviceId, state)
        return changed
    }

    /**
     * Aplica uma transição evdev rastreando o código Linux físico e o bit lógico Android.
     * Vários códigos Linux podem ser normalizados para o mesmo bit (por exemplo BTN_SIDE e
     * BTN_BACK). O UP de um código só libera o bit quando nenhum alias ou outro dispositivo
     * continua mantendo o botão pressionado.
     */
    fun updateButtonCode(deviceId: Int, sourceCode: Int, buttonBit: Int, pressed: Boolean): Boolean {
        val knownMask = MouseButtonStatePolicy.KNOWN_BUTTON_MASK
        if (sourceCode <= 0 || buttonBit <= 0 || (buttonBit and (buttonBit - 1)) != 0 ||
            (buttonBit and knownMask) != buttonBit
        ) return false

        val previousGlobal = aggregateButtonMask()
        val state = devices.getOrPut(deviceId) { DeviceState() }
        if (pressed) state.heldButtonCodes.putIfAbsent(sourceCode, buttonBit)
        else state.heldButtonCodes.remove(sourceCode)
        val changed = ((previousGlobal xor aggregateButtonMask()) and buttonBit) != 0
        removeIfEmpty(deviceId, state)
        return changed
    }

    /** Limpa somente um dispositivo e libera apenas entradas que nenhum outro ainda mantém. */
    fun clearDevice(deviceId: Int): Snapshot {
        val removed = devices[deviceId] ?: return Snapshot(0, intArrayOf())
        val previousGlobalButtons = aggregateButtonMask()
        val remainingKeys = LinkedHashSet<Int>()
        devices.forEach { (id, state) -> if (id != deviceId) remainingKeys.addAll(state.heldKeys) }
        val releasedKeys = removed.heldKeys.filter { it !in remainingKeys }.toIntArray()
        devices.remove(deviceId)
        val releasedButtons = previousGlobalButtons and aggregateButtonMask().inv()
        return Snapshot(releasedButtons, releasedKeys)
    }

    /** Liberação global usada em perda de foco, CANCEL, encerramento ou parada do capturador. */
    fun clear(): Snapshot {
        val buttons = aggregateButtonMask()
        val keys = LinkedHashSet<Int>()
        devices.values.forEach { keys.addAll(it.heldKeys) }
        devices.clear()
        return Snapshot(buttons, keys.toIntArray())
    }

    private fun aggregateButtonMask(): Int {
        var result = 0
        devices.values.forEach { state ->
            result = result or state.buttonMask
            state.heldButtonCodes.values.forEach { result = result or it }
        }
        return result
    }

    private fun removeIfEmpty(deviceId: Int, state: DeviceState) {
        if (state.heldKeys.isEmpty() && state.buttonMask == 0 && state.heldButtonCodes.isEmpty()) {
            devices.remove(deviceId)
        }
    }

    data class Snapshot(val buttonMask: Int, val keyCodes: IntArray)
}
