package com.example.keymapper.data

import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

/** Tipos persistidos de controle HUD. Tap/Hold/Combo/Rapid Tap compartilham STANDARD sem perder identidade de modo. */
object Kind {
    const val STANDARD = 0   // Tap/Hold/Combo/Rapid Tap: controles de toque geral.
    const val FIRE = 1       // Fire: disparo especializado, independente do controle padrão.
    const val AIM = 2        // Aim/Sight: controle de visão/câmera.
    const val SWIPE = 3      // Swipe: gesto touchscreen com origem, destino e duração.
    const val MACRO = 4      // Macro: sequência temporizada, distinta do Combo simultâneo.
}

/** Como o botão reage à tecla (botão padrão; no tiro só HOLD e TAP). */
object Mode {
    const val HOLD = 0       // ACTION_DOWN ao apertar, ACTION_UP só ao soltar a tecla
    const val TAP = 1        // toque único e curto
    const val TOGGLE = 2     // aperta uma vez para ligar, outra para desligar
    const val DOUBLE = 3     // duplo toque: um aperto = dois toques curtos
    const val RAPID = 4      // toques repetidos somente enquanto a entrada física estiver pressionada
}

/** Teclas do mouse usam códigos negativos: M1..M5 = -1..-5. */
fun keyLabel(code: Int): String = when (code) {
    0 -> "Sem tecla"
    -1 -> "Mouse Esquerdo"
    -2 -> "Mouse Direito"
    -3 -> "Mouse do Meio"
    -4 -> "Mouse Lateral Voltar"
    -5 -> "Mouse Lateral Avançar"
    KeyEvent.KEYCODE_NUMPAD_3 -> "NUM3"
    else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
        .replace("NUMPAD_", "NUM ")
}

data class HudButton(
    val id: Int,
    val key: Int,              // tecla do botão; 0 = ainda não associada (ignorada pelo motor)
    val x: Float,              // posição em fração da ÁREA DO JOGO (0..1)
    val y: Float,
    val size: Float = 0.07f,   // diâmetro em fração do menor lado da área do jogo
    val opacity: Float = 1f,
    val kind: Int = Kind.STANDARD,
    val mode: Int = Mode.HOLD,
    val delayMs: Int = 0,      // tempo de resposta: atraso (0..500 ms) entre apertar a tecla e o toque
    val macro: List<Int> = emptyList(),   // botão padrão: aciona também estes botões (não toca em si mesmo)
    val name: String = "",
    // ---- mira ----
    val aimSensX: Float = 0.9f,       // sensibilidade X com a mira ativa (1.0 = neutro)
    val aimSensY: Float = 0.9f,
    val aimToggle: Boolean = false,   // suspender: um toque liga a mira, o próximo desliga
    val mouseAim: Boolean = true,     // mouse move a câmera enquanto mira
    val rotMode: Int = -1,            // -1 = global; AUTO/CENTER=UP/DOWN, NONSTOP=handover multitouch, INFINITE=relativo, OFF=Boundary
    val aimDeadzone: Float = 0f,
    // 0 = bordas da área atual; >0 = raio de recentralização como no Aiming Mode do K2er.
    val aimBoundaryRadiusPct: Float = 0f,
    // -1 = herda AppSettings.lookIdleMs; 0 = mantém contato; >0 = liberação automática em ms.
    val aimAutoReleaseMs: Int = -1,
    val shootKey: Int = -1,           // tecla de tiro associada a esta mira
    val shootSensX: Float = 1f,       // multiplicador extra enquanto a tecla de tiro está pressionada
    val shootSensY: Float = 1f,
    val aimGeneral: Float = 16240f,   // fine tuning em escala lógica; 16240 = neutro
    val aimMouseSens: Float = 69f,    // escala lógica do mouse; 69 = neutro
    val aimAccelLevel: Float = 0f,    // aceleração do mouse, 0..10 (0 = nenhuma)
    val crossSize: Float = 5f,        // cruz de mira, 0..10 (0 = oculta)
    val crossOpacity: Float = 100f,   // 0..100 %
    val aimInvert: Boolean = false,
    val fireWithAim: Boolean = false,
    val aimPosX: Float = 199f,       // escala lógica 0..1000 da área de jogo
    val aimPosY: Float = 199f,       // escala lógica 0..1000 da área de jogo
    val aimZoom: Float = 1f,
    // ---- tiro ----
    val linkAimId: Int = -1,          // tiro: ao pressionar, aciona também esta mira
    val visualScale: Float = 1f,      // escala apenas de apresentação; não altera posição/ação mapeada
    val rapidIntervalMs: Int = 120,   // Rapid Tap: intervalo entre toques, 40..2000 ms
    val swipeEndX: Float = 0.72f,     // Swipe: destino normalizado dentro da área do jogo
    val swipeEndY: Float = 0.50f,
    val swipeDurationMs: Int = 180,   // Swipe: duração do gesto, 50..2000 ms
    val isCombo: Boolean = false,    // Combo simultâneo: usa macro de IDs; não executa o próprio ponto
    val sequence: List<MacroStep> = emptyList(), // Macro sequencial; separado do campo legado macro (Combo)
    val sequenceRepeatCount: Int = 1 // Número de execuções completas (1..20)
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("key", key)
        put("x", x.toDouble()); put("y", y.toDouble())
        put("size", size.toDouble()); put("opacity", opacity.toDouble())
        put("type", typeName(kind)); put("mode", mode); put("delayMs", delayMs)
        put("macro", JSONArray(macro)); put("name", name)
        put("aimSensX", aimSensX.toDouble()); put("aimSensY", aimSensY.toDouble())
        put("aimToggle", aimToggle); put("mouseAim", mouseAim); put("rotMode", rotMode)
        put("aimDeadzone", aimDeadzone.toDouble())
        put("aimBoundaryRadiusPct", aimBoundaryRadiusPct.toDouble())
        put("aimAutoReleaseMs", aimAutoReleaseMs)
        put("shootKey", shootKey)
        put("shootSensX", shootSensX.toDouble()); put("shootSensY", shootSensY.toDouble())
        put("aimGeneral", aimGeneral.toDouble()); put("aimMouseSens", aimMouseSens.toDouble())
        put("aimAccelLevel", aimAccelLevel.toDouble())
        put("crossSize", crossSize.toDouble()); put("crossOpacity", crossOpacity.toDouble())
        put("aimInvert", aimInvert); put("fireWithAim", fireWithAim)
        put("aimPosX", aimPosX.toDouble()); put("aimPosY", aimPosY.toDouble())
        put("aimZoom", aimZoom.toDouble()); put("linkAimId", linkAimId)
        put("visualScale", visualScale.toDouble())
        put("rapidIntervalMs", rapidIntervalMs)
        put("swipeEndX", swipeEndX.toDouble()); put("swipeEndY", swipeEndY.toDouble())
        put("swipeDurationMs", swipeDurationMs)
        put("isCombo", isCombo)
        put("sequence", JSONArray().apply { sequence.take(MacroStepPolicy.MAX_STEPS).forEach { put(it.toJson()) } })
        put("sequenceRepeatCount", MacroStepPolicy.repeatCount(sequenceRepeatCount))
        // Marca a semântica atual para migrar preferências antigas uma única vez.
        put("aimTuningScale", 2)
    }

    companion object {
        fun typeName(kind: Int): String = when (kind) {
            Kind.FIRE -> "FIRE"
            Kind.AIM -> "AIM"
            Kind.SWIPE -> "SWIPE"
            Kind.MACRO -> "MACRO"
            else -> "STANDARD"
        }

        /**
         * Lê um botão salvo. Aceita o formato novo ("type"/"mode") e os formatos antigos
         * ("kind" 0..4 = TAP, TOGGLE, MACRO, CLICK, AIM), convertendo para os tipos atuais.
         * Campos ausentes viram valores padrão; nunca lança por falta de campo opcional.
         */
        fun fromJson(b: JSONObject): HudButton {
            val id = b.getInt("id")
            require(id >= 0) { "ID de controle inválido" }
            val key = b.getInt("key")
            require(key == 0 || key in -5..-1 || key in 1..KeyEvent.getMaxKeyCode()) { "Código de tecla inválido" }
            fun finiteFloat(field: String, default: Float, min: Float, max: Float): Float {
                val value = b.optDouble(field, default.toDouble()).toFloat()
                return if (value.isFinite()) value.coerceIn(min, max) else default
            }
            val name = b.optString("name", "").take(64)
            // Macros são limitadas para evitar configurações corrompidas gerarem listas enormes.
            val macro = b.optJSONArray("macro")?.let { m ->
                (0 until minOf(m.length(), 128)).mapNotNull { i ->
                    runCatching { m.getInt(i) }.getOrNull()?.takeIf { it >= 0 }
                }.distinct()
            } ?: emptyList()
            // Compatibilidade: controles antigos com macro não vazia continuam sendo combos.
            val isCombo = b.optBoolean("isCombo", name.equals("Combo", ignoreCase = true) || macro.isNotEmpty())
            val kind: Int
            val mode: Int
            if (b.has("type")) {
                kind = when (b.optString("type")) {
                    "FIRE" -> Kind.FIRE
                    "AIM" -> Kind.AIM
                    "SWIPE" -> Kind.SWIPE
                    "MACRO" -> Kind.MACRO
                    else -> Kind.STANDARD
                }
                mode = b.optInt("mode", Mode.HOLD).coerceIn(0, Mode.RAPID)
            } else {
                val old = b.optInt("kind", 0) // legado: 0 TAP, 1 TOGGLE, 2 MACRO, 3 CLICK, 4 AIM
                val isAim = old == 4 || name.equals("Mira", ignoreCase = true)
                val isFire = !isAim && (key == -1 || name.equals("Tiro", ignoreCase = true))
                kind = when {
                    isAim -> Kind.AIM
                    isFire -> Kind.FIRE
                    else -> Kind.STANDARD
                }
                mode = when {
                    isFire -> Mode.HOLD      // o Tiro antigo vira "segurar contínuo" (corrige o tiro que só dava cliques)
                    old == 1 -> Mode.TOGGLE
                    old == 3 -> Mode.TAP
                    else -> Mode.HOLD
                }
            }
            val sequence = b.optJSONArray("sequence")?.let { array ->
                (0 until minOf(array.length(), MacroStepPolicy.MAX_STEPS)).mapNotNull { index ->
                    runCatching { array.getJSONObject(index) }.getOrNull()?.let(MacroStep::fromJson)
                }
            } ?: emptyList()
            val sequenceRepeatCount = MacroStepPolicy.repeatCount(b.optInt("sequenceRepeatCount", 1))
            val tuningV2 = b.optInt("aimTuningScale", 0) >= 2
            val oldMouseSens = b.optDouble("aimMouseSens", 1.0).toFloat()
            val oldGeneral = b.optDouble("aimGeneral", 1.0).toFloat()
            val oldPosX = b.optDouble("aimPosX", 0.5).toFloat()
            val oldPosY = b.optDouble("aimPosY", 0.5).toFloat()
            return HudButton(
                id = id,
                key = key,
                x = finiteFloat("x", 0.5f, 0f, 1f),
                y = finiteFloat("y", 0.5f, 0f, 1f),
                size = finiteFloat("size", 0.07f, 0.01f, 0.5f),
                opacity = finiteFloat("opacity", 1f, 0f, 1f),
                kind = kind,
                mode = mode,
                delayMs = b.optInt("delayMs", 0).coerceIn(0, 500),
                macro = macro,
                name = name,
                aimSensX = finiteFloat("aimSensX", 0.9f, 0.01f, 100f),
                aimSensY = finiteFloat("aimSensY", 0.9f, 0.01f, 100f),
                aimToggle = b.optBoolean("aimToggle", false),
                mouseAim = b.optBoolean("mouseAim", true),
                rotMode = b.optInt("rotMode", -1).coerceIn(-1, Rot.NONSTOP),
                aimDeadzone = finiteFloat("aimDeadzone", 0f, 0f, 100f),
                aimBoundaryRadiusPct = finiteFloat("aimBoundaryRadiusPct", 0f, 0f, 45f),
                aimAutoReleaseMs = b.optInt("aimAutoReleaseMs", -1).let { raw ->
                    if (raw == -1) -1 else raw.coerceIn(0, 60_000)
                },
                shootKey = b.optInt("shootKey", -1).let { code ->
                    if (code == 0 || code in -5..-1 || code in 1..KeyEvent.getMaxKeyCode()) code else -1
                },
                shootSensX = finiteFloat("shootSensX", 1f, 0.01f, 100f),
                shootSensY = finiteFloat("shootSensY", 1f, 0.01f, 100f),
                aimGeneral = (if (!tuningV2 && oldGeneral <= 5f) oldGeneral * 16240f else oldGeneral)
                    .takeIf { it.isFinite() }?.coerceIn(1f, 1_000_000f) ?: 16240f,
                aimMouseSens = (if (!tuningV2 && oldMouseSens <= 5f) oldMouseSens * 69f else oldMouseSens)
                    .takeIf { it.isFinite() }?.coerceIn(0.01f, 1000f) ?: 69f,
                aimAccelLevel = finiteFloat("aimAccelLevel", 0f, 0f, 10f),
                crossSize = finiteFloat("crossSize", 5f, 0f, 10f),
                crossOpacity = finiteFloat("crossOpacity", 100f, 0f, 100f),
                aimInvert = b.optBoolean("aimInvert", false),
                fireWithAim = b.optBoolean("fireWithAim", false),
                aimPosX = (if (!b.has("aimPosX")) 199f else if (!tuningV2 && oldPosX in 0f..1f) oldPosX * 1000f else oldPosX)
                    .takeIf { it.isFinite() }?.coerceIn(0f, 1000f) ?: 199f,
                aimPosY = (if (!b.has("aimPosY")) 199f else if (!tuningV2 && oldPosY in 0f..1f) oldPosY * 1000f else oldPosY)
                    .takeIf { it.isFinite() }?.coerceIn(0f, 1000f) ?: 199f,
                aimZoom = finiteFloat("aimZoom", 1f, 0.1f, 10f),
                linkAimId = b.optInt("linkAimId", -1),
                visualScale = finiteFloat("visualScale", 1f, 0.5f, 2f),
                rapidIntervalMs = b.optInt("rapidIntervalMs", 120).coerceIn(40, 2_000),
                swipeEndX = finiteFloat("swipeEndX", 0.72f, 0f, 1f),
                swipeEndY = finiteFloat("swipeEndY", 0.50f, 0f, 1f),
                swipeDurationMs = b.optInt("swipeDurationMs", 180).coerceIn(50, 2_000),
                isCombo = kind != Kind.MACRO && isCombo,
                sequence = sequence,
                sequenceRepeatCount = sequenceRepeatCount
            ).let { parsed ->
                // Fire mantém somente os modos explicitamente suportados pelo painel especializado.
                if (parsed.kind == Kind.FIRE && parsed.mode != Mode.TAP && parsed.mode != Mode.HOLD) {
                    parsed.copy(mode = Mode.HOLD)
                } else parsed
            }
        }
    }
}

/** Nome mostrado ao usuário: o nome dado ou um nome automático pelo tipo/tecla. */
fun HudButton.displayName(): String = name.ifEmpty {
    when (kind) {
        Kind.FIRE -> "Tiro"
        Kind.AIM -> "Aim / Sight"
        Kind.SWIPE -> "Swipe"
        Kind.MACRO -> "Macro"
        else -> when (mode) {
            Mode.TAP -> "Tap"
            Mode.RAPID -> "Rapid Tap"
            else -> "Botão ${keyLabel(key)}"
        }
    }
}

fun defaultButtons(): List<HudButton> = listOf(
    HudButton(0, -1, 0.88f, 0.62f, 0.10f, kind = Kind.FIRE, mode = Mode.HOLD),
    HudButton(1, -2, 0.90f, 0.40f, kind = Kind.AIM),
    HudButton(2, KeyEvent.KEYCODE_SPACE, 0.92f, 0.80f),
    HudButton(3, KeyEvent.KEYCODE_CTRL_LEFT, 0.80f, 0.86f, mode = Mode.TOGGLE),
    HudButton(4, KeyEvent.KEYCODE_R, 0.76f, 0.72f)
)
