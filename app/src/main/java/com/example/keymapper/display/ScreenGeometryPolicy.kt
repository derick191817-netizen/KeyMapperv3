package com.example.keymapper.display

/**
 * Classifica mudanças do monitor para decidir se as janelas precisam ser recriadas.
 * Mantida sem dependências Android para permitir teste unitário determinístico.
 */
object ScreenGeometryPolicy {
    data class Snapshot(
        val displayId: Int,
        val width: Int,
        val height: Int,
        val densityDpi: Int
    )

    enum class Change {
        NONE,
        RESIZE,
        REINITIALIZE
    }

    fun classify(previous: Snapshot, current: Snapshot): Change {
        if (previous.displayId != current.displayId || previous.densityDpi != current.densityDpi) {
            return Change.REINITIALIZE
        }
        if (previous.width != current.width || previous.height != current.height) {
            return Change.RESIZE
        }
        return Change.NONE
    }
}
