package com.example.keymapper.input

/**
 * Acumula frações de scroll separadamente por dispositivo físico.
 *
 * O destino pode mapear cada passo inteiro para uma tecla (Page Up/Down ou setas). Dois mouses
 * não devem completar a fração um do outro. A instância é usada exclusivamente na thread do motor.
 */
class MouseScrollAccumulator(
    private val maxInputPerEvent: Float = 16f,
    private val maxAccumulated: Float = 32f,
    private val maxStepsPerAxisPerEvent: Int = 16
) {
    private data class DeviceScroll(var horizontal: Float = 0f, var vertical: Float = 0f)
    private val devices = HashMap<Int, DeviceScroll>()

    init {
        require(maxInputPerEvent > 0f && maxInputPerEvent.isFinite())
        require(maxAccumulated >= maxInputPerEvent && maxAccumulated.isFinite())
        require(maxStepsPerAxisPerEvent > 0)
    }

    /**
     * Preenche [out] sem alocação: out[0] = passos verticais (+ cima, - baixo),
     * out[1] = passos horizontais (+ direita, - esquerda). O retorno indica se houve passo.
     */
    fun consumeInto(deviceId: Int, horizontal: Float, vertical: Float, out: IntArray): Boolean {
        if (out.size < 2) return false
        out[0] = 0
        out[1] = 0
        if (!horizontal.isFinite() || !vertical.isFinite() ||
            (horizontal == 0f && vertical == 0f)
        ) return false

        val state = devices.getOrPut(deviceId) { DeviceScroll() }
        state.horizontal = (state.horizontal + horizontal.coerceIn(-maxInputPerEvent, maxInputPerEvent))
            .coerceIn(-maxAccumulated, maxAccumulated)
        state.vertical = (state.vertical + vertical.coerceIn(-maxInputPerEvent, maxInputPerEvent))
            .coerceIn(-maxAccumulated, maxAccumulated)

        var verticalSteps = 0
        while (state.vertical >= 1f && verticalSteps < maxStepsPerAxisPerEvent) {
            verticalSteps++
            state.vertical -= 1f
        }
        while (state.vertical <= -1f && verticalSteps > -maxStepsPerAxisPerEvent) {
            verticalSteps--
            state.vertical += 1f
        }

        var horizontalSteps = 0
        while (state.horizontal >= 1f && horizontalSteps < maxStepsPerAxisPerEvent) {
            horizontalSteps++
            state.horizontal -= 1f
        }
        while (state.horizontal <= -1f && horizontalSteps > -maxStepsPerAxisPerEvent) {
            horizontalSteps--
            state.horizontal += 1f
        }

        out[0] = verticalSteps
        out[1] = horizontalSteps
        if (state.horizontal == 0f && state.vertical == 0f) devices.remove(deviceId)
        return verticalSteps != 0 || horizontalSteps != 0
    }

    fun resetDevice(deviceId: Int) {
        devices.remove(deviceId)
    }

    fun reset() {
        devices.clear()
    }

    /** Exposto para diagnóstico/testes sem revelar os valores acumulados. */
    fun trackedDeviceCount(): Int = devices.size
}
