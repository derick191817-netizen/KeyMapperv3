package com.example.keymapper.injection

/** Limites conservadores usados pelo injetor para evitar MotionEvents multitoque inválidos. IDs são 0..31. */
object TouchPointerPolicy {
    const val MAX_SIMULTANEOUS_POINTERS = 16

    fun canStart(currentPointerCount: Int, pointerId: Int): Boolean =
        pointerId in 0..31 && currentPointerCount in 0 until MAX_SIMULTANEOUS_POINTERS
}
