package com.example.keymapper.injection

/** Transport selected for a complete touchscreen gesture. */
internal enum class TouchOutputTransport {
    INPUT_MANAGER,
    UINPUT
}

/**
 * A uinput touchscreen has no public Android displayId routing contract. Keep it on display 0;
 * all secondary displays (including Samsung DeX) stay on InputManager.
 */
internal object TouchOutputRoutingPolicy {
    fun select(displayId: Int, uinputAvailable: Boolean): TouchOutputTransport =
        if (displayId == 0 && uinputAvailable) {
            TouchOutputTransport.UINPUT
        } else {
            TouchOutputTransport.INPUT_MANAGER
        }

    fun canContinue(transport: TouchOutputTransport, displayId: Int): Boolean =
        transport != TouchOutputTransport.UINPUT || displayId == 0
}
