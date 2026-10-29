package com.example.keymapper.domain

/** Regras independentes da View para capturar teclas físicas e botões de mouse. */
object InputBindingPolicy {
    /** Android MotionEvent button masks: primary=1, secondary=2, tertiary=4. */
    fun mouseButtonCode(buttonMask: Int): Int? = when (buttonMask) {
        1 -> -1 // mouse esquerdo
        2 -> -2 // mouse direito
        4 -> -3 // botão central
        else -> null
    }

    /** Escolhe um botão suportado de uma máscara com mais de um botão pressionado. */
    fun firstMouseButtonCode(buttonState: Int): Int? =
        listOf(1, 2, 4).firstNotNullOfOrNull { bit ->
            if ((buttonState and bit) != 0) mouseButtonCode(bit) else null
        }
}
