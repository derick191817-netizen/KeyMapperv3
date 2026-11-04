package com.example.keymapper.input

/**
 * Normaliza o estado dos botões do mouse sem depender exclusivamente de buttonState.
 * Alguns drivers expõem a transição em ACTION_BUTTON_PRESS/RELEASE, mas fornecem
 * um snapshot buttonState atrasado ou incompleto nessa mesma amostra.
 */
object MouseButtonStatePolicy {
    const val KNOWN_BUTTON_MASK = 0x1F

    fun resolve(
        reportedMask: Int,
        actionButton: Int = 0,
        pressed: Boolean? = null
    ): Int {
        var next = reportedMask and KNOWN_BUTTON_MASK
        val changed = actionButton and KNOWN_BUTTON_MASK
        return when (pressed) {
            true -> next or changed
            false -> next and changed.inv()
            null -> next
        } and KNOWN_BUTTON_MASK
    }
}
