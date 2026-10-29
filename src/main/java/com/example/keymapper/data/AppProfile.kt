package com.example.keymapper.data

import org.json.JSONArray
import org.json.JSONObject

/** Configuração funcional isolada por pacote Android. Campos globais permanecem em AppSettings. */
data class AppProfile(
    val buttons: List<HudButton>,
    val sensX: Float,
    val sensY: Float,
    val globalSens: Float,
    val buttonScale: Float,
    val hudAlpha: Float,
    val showHud: Boolean,
    val hudStyle: Int,
    val joyX: Float,
    val joyY: Float,
    val joyR: Float,
    val lookX: Float,
    val lookY: Float,
    val lookR: Float,
    val gridSnap: Int
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("buttons", JSONArray().apply { buttons.forEach { put(it.toJson()) } })
        put("sensX", sensX.toDouble()); put("sensY", sensY.toDouble()); put("globalSens", globalSens.toDouble())
        put("buttonScale", buttonScale.toDouble()); put("hudAlpha", hudAlpha.toDouble())
        put("showHud", showHud); put("hudStyle", hudStyle)
        put("joyX", joyX.toDouble()); put("joyY", joyY.toDouble()); put("joyR", joyR.toDouble())
        put("lookX", lookX.toDouble()); put("lookY", lookY.toDouble()); put("lookR", lookR.toDouble())
        put("gridSnap", gridSnap)
    }

    fun applyTo(base: AppSettings): AppSettings = base.copy(
        buttons = buttons,
        sensX = sensX, sensY = sensY, globalSens = globalSens,
        buttonScale = buttonScale, hudAlpha = hudAlpha, showHud = showHud, hudStyle = hudStyle,
        joyX = joyX, joyY = joyY, joyR = joyR, lookX = lookX, lookY = lookY, lookR = lookR,
        gridSnap = gridSnap
    )

    companion object {
        fun fromSettings(s: AppSettings) = AppProfile(
            s.buttons.toList(), s.sensX, s.sensY, s.globalSens, s.buttonScale, s.hudAlpha,
            s.showHud, s.hudStyle, s.joyX, s.joyY, s.joyR, s.lookX, s.lookY, s.lookR, s.gridSnap
        )

        fun fromJson(o: JSONObject, fallback: AppSettings): AppProfile {
            fun f(key: String, default: Float, min: Float, max: Float): Float {
                val value = o.optDouble(key, default.toDouble()).toFloat()
                return if (value.isFinite()) value.coerceIn(min, max) else default
            }
            val buttonsJson = o.optJSONArray("buttons")
            val buttons = buttonsJson?.let { a ->
                (0 until a.length()).mapNotNull { i -> runCatching { HudButton.fromJson(a.getJSONObject(i)) }.getOrNull() }
                    .distinctBy { it.id }
            } ?: fallback.buttons
            return AppProfile(
                buttons.ifEmpty { if (buttonsJson?.length() == 0) emptyList() else fallback.buttons },
                f("sensX", fallback.sensX, 0.1f, 1000f), f("sensY", fallback.sensY, 0.1f, 1000f),
                f("globalSens", fallback.globalSens, 0.01f, 10f), f("buttonScale", fallback.buttonScale, 0.1f, 4f),
                f("hudAlpha", fallback.hudAlpha, 0.05f, 1f), o.optBoolean("showHud", fallback.showHud),
                o.optInt("hudStyle", fallback.hudStyle).coerceIn(0, 4),
                f("joyX", fallback.joyX, 0f, 1f), f("joyY", fallback.joyY, 0f, 1f), f("joyR", fallback.joyR, 0.01f, 0.5f),
                f("lookX", fallback.lookX, 0f, 1f), f("lookY", fallback.lookY, 0f, 1f), f("lookR", fallback.lookR, 0.01f, 1f),
                o.optInt("gridSnap", fallback.gridSnap).coerceIn(0, 100)
            )
        }
    }
}
