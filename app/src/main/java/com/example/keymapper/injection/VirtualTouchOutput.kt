package com.example.keymapper.injection

/**
 * Optional native touchscreen transport.
 *
 * The interface accepts coordinates normalized to the virtual device range 0..65535.
 * A false result is a transport failure, not proof that the target application consumed
 * (or did not consume) the input frame.
 */
interface VirtualTouchOutput {
    fun isAvailable(): Boolean

    fun sendFrame(
        actionMasked: Int,
        actionIndex: Int,
        pointerCount: Int,
        pointerIds: IntArray,
        pointerXs: FloatArray,
        pointerYs: FloatArray
    ): Boolean

    fun releaseAll(): Boolean
}
