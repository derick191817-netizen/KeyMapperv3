import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
package com.example.keymapper.input

/**
 * Escala de 1 a 11 do Painel de Controle do Windows ("Velocidade do ponteiro").
 * O nível 6 (meio) é o padrão de fábrica e vale 1.0x: movimento 1:1 com o mouse.
 * Multiplicadores do Windows para os 11 níveis (valores de registro 1, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20).
 */
object PointerSpeed {
    const val DEFAULT = 6
    private val MULT = floatArrayOf(
        0.03125f, 0.0625f, 0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f
    )

    fun mult(level: Int): Float = MULT[level.coerceIn(1, 11) - 1]

    fun label(level: Int): String = if (level == DEFAULT) "$level (padrão)" else "$level"
}
