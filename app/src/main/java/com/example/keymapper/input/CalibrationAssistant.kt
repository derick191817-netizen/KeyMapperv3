import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
package com.example.keymapper.input

import kotlin.math.abs

/** Soma o movimento bruto do mouse durante uma janela e sugere a sensibilidade. */
class CalibrationAssistant {
    var sumX = 0f
        private set
    var sumY = 0f
        private set

    fun reset() {
        sumX = 0f; sumY = 0f
    }

    fun feed(dx: Float, dy: Float) {
        sumX += abs(dx); sumY += abs(dy)
    }

    /** Sensibilidade para que o movimento medido corresponda a [targetPx] pixels. */
    fun suggestX(targetPx: Float): Float? = if (sumX > 1f) targetPx / sumX else null
    fun suggestY(targetPx: Float): Float? = if (sumY > 1f) targetPx / sumY else null
}
