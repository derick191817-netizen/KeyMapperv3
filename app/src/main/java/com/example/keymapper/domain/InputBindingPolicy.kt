package com.example.keymapper.domain

/** Regras independentes da View para capturar teclas físicas e botões de mouse. */
object InputBindingPolicy {
    /**
     * Android MotionEvent button masks: primary=1, secondary=2, tertiary=4,
     * back=8 and forward=16. Some mice/drivers expose only a subset.
     */
    fun mouseButtonCode(buttonMask: Int): Int? = when (buttonMask) {
        1 -> -1 // esquerdo
        2 -> -2 // direito
        4 -> -3 // central
        8 -> -4 // lateral voltar
        16 -> -5 // lateral avançar
        else -> null
    }

    fun mouseButtonMask(code: Int): Int? = when (code) {
        -1 -> 1
        -2 -> 2
        -3 -> 4
        -4 -> 8
        -5 -> 16
        else -> null
    }

    /** Escolhe um botão suportado de uma máscara com mais de um botão pressionado. */
    fun firstMouseButtonCode(buttonState: Int): Int? {
        for (bit in MOUSE_BUTTON_MASKS) {
            if ((buttonState and bit) != 0) return mouseButtonCode(bit)
        }
        return null
    }

    private val MOUSE_BUTTON_MASKS = intArrayOf(1, 2, 4, 8, 16)
}
