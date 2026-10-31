package com.example.keymapper.input

import java.util.LinkedHashMap
import java.util.LinkedHashSet

/**
 * Estado físico agregado por dispositivo Android.
 *
 * Uma tecla/botão só é encaminhado como DOWN quando nenhum dispositivo já o mantém pressionado,
 * e só gera UP quando o último dispositivo o solta. Isso evita que dois teclados/mouses disputem
 * o mesmo código lógico e que a remoção de um deles solte uma entrada ainda mantida pelo outro.
 * A instância pertence à thread de UI/CaptureView.
 */
class PhysicalInputState {
    private data class DeviceState(
        val heldKeys: LinkedHashSet<Int> = LinkedHashSet(),
        var buttonMask: Int = 0
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
        state.buttonMask = nextMask
        val changed = previousGlobal xor aggregateButtonMask()
        removeIfEmpty(deviceId, state)
        return changed
    }

    /** Limpa somente um dispositivo e libera apenas entradas que nenhum outro ainda mantém. */
    fun clearDevice(deviceId: Int): Snapshot {
        val removed = devices.remove(deviceId) ?: return Snapshot(0, intArrayOf())
        val remainingKeys = LinkedHashSet<Int>()
        devices.values.forEach { remainingKeys.addAll(it.heldKeys) }
        val releasedKeys = removed.heldKeys.filter { it !in remainingKeys }.toIntArray()
        val remainingButtons = aggregateButtonMask()
        val releasedButtons = removed.buttonMask and remainingButtons.inv()
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
        devices.values.forEach { result = result or it.buttonMask }
        return result
    }

    private fun removeIfEmpty(deviceId: Int, state: DeviceState) {
        if (state.heldKeys.isEmpty() && state.buttonMask == 0) devices.remove(deviceId)
    }

    data class Snapshot(val buttonMask: Int, val keyCodes: IntArray)
}
