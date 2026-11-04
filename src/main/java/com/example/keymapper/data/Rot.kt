package com.example.keymapper.data

/** Estratégias persistidas do controle Sight/Aim. Os IDs antigos permanecem compatíveis. */
object Rot {
    const val CENTER = 0       // Legado: toque + UP/DOWN com reancoragem
    const val INFINITE = 1     // Mouse relativo nativo; não usa contato touchscreen
    const val OFF = 2          // Boundary: arrasto limitado à borda
    const val AUTO = 3         // Reancoragem touchscreen compatível
    const val NONSTOP = 4      // Handover sobreposto: POINTER_DOWN antes de POINTER_UP

    fun label(mode: Int): String = when (mode) {
        AUTO, CENTER -> "Contínuo automático (toque + reancoragem)"
        NONSTOP -> "Contínuo Nonstop (handover multitouch)"
        INFINITE -> "Contínuo relativo nativo (sem toque)"
        OFF -> "Limite ativo (para na borda)"
        else -> "Usar o global"
    }
}
