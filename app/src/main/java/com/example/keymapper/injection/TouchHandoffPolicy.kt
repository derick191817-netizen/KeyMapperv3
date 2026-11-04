package com.example.keymapper.injection

/**
 * Decide se um handover pode manter dois contatos durante alguns eventos.
 * O destino recebe temporariamente um ponteiro adicional; quando não há slot disponível,
 * o motor usa o handover sequencial compatível em vez de exceder os limites do MotionEvent.
 */
internal object TouchHandoffPolicy {
    fun useOverlappedHandoff(requestedNonstop: Boolean, activePointers: Int, newPointerId: Int): Boolean =
        requestedNonstop && TouchPointerPolicy.canStart(activePointers, newPointerId)
}
