import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
package com.example.keymapper.util

import android.view.HapticFeedbackConstants
import android.view.View

/** Vibração curta de confirmação via View (não exige nenhuma permissão). */
object Haptics {
    @Volatile
    var enabled = true

    @Suppress("DEPRECATION")
    fun tick(v: View) {
        if (!enabled) return
        try {
            v.performHapticFeedback(
                HapticFeedbackConstants.CLOCK_TICK,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (e: Exception) {
        }
    }
}
