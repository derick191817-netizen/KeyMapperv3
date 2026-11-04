package com.example.keymapper.domain

/**
 * Validação centralizada dos campos numéricos do editor.
 * Valores parciais do IME (por exemplo "", "-" ou "0,") nunca devem ser aplicados.
 */
object NumericSettingPolicy {
    fun intValue(raw: String, range: IntRange): Int? {
        val value = raw.trim().toIntOrNull() ?: return null
        return value.takeIf { it in range }
    }

    fun floatValue(raw: String, min: Float, max: Float): Float? {
        if (!min.isFinite() || !max.isFinite() || min > max) return null
        val normalized = raw.trim().replace(',', '.')
        if (normalized.isEmpty() || normalized == "-" || normalized == "." || normalized == "-.") {
            return null
        }
        val value = normalized.toFloatOrNull() ?: return null
        if (!value.isFinite()) return null
        return value.takeIf { it >= min && it <= max }
    }
}
