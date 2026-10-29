package com.example.keymapper.data

import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

/** Só 3 tipos de botão, como nos emuladores de PC. */
object Kind {
    const val STANDARD = 0   // Padrão: simula qualquer tecla (espaço, C, R, Ctrl...)
    const val FIRE = 1       // Tiro: segurar contínuo (ou toque único)
    const val AIM = 2        // Mira: sensibilidade própria, suspender (toggle), mouse aim...
}

/** Como o botão reage à tecla (botão padrão; no tiro só HOLD e TAP). */
object Mode {
    const val HOLD = 0       // ACTION_DOWN ao apertar, ACTION_UP só ao soltar a tecla
    const val TAP = 1        // toque único e curto
    const val TOGGLE = 2     // aperta uma vez para ligar, outra para desligar
    const val DOUBLE = 3     // duplo toque: um aperto = dois toques curtos
}

/** Modo de rotação da câmera/mira. 0..2 são os modos; 3 só existe como escolha do usuário. */
object Rot {
    const val CENTER = 0     // Normal: ao chegar na borda, solta e toca no centro no mesmo instante (fallback seguro)
    const val INFINITE = 1   // Mouse virtual: coordenadas virtuais sem limite (jogos 3D)
    const val OFF = 2        // Desligado: a mira para na borda
    const val AUTO = 3       // Automático: Infinito em jogos 3D conhecidos, Centro nos demais

    fun label(m: Int): String = when (m) {
        AUTO -> "Automático (Infinito / Centro)"
        CENTER -> "Somente Centro (jogos 2D/incompatíveis)"
        INFINITE -> "Somente Infinito (jogos 3D)"
        OFF -> "Desligado (para na borda)"
        else -> "Usar o global"
    }
}

/** Teclas do mouse usam códigos negativos: M1 = -1, M2 = -2, M3 = -3. */
fun keyLabel(code: Int): String = when (code) {
    -1 -> "Mouse Esquerdo"
    -2 -> "Mouse Direito"
    -3 -> "Scroll"
    KeyEvent.KEYCODE_NUMPAD_3 -> "NUM3"
    else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
        .replace("NUMPAD_", "NUM ")
}

data class HudButton(
    val id: Int,
    val key: Int,              // tecla do botão (na mira = "tecla de mira")
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
    val rotMode: Int = -1,            // modo de rotação desta mira (-1 = usar o global; 0..3 = Rot.*)
    val aimDeadzone: Float = 0f,
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
    val linkAimId: Int = -1           // tiro: ao pressionar, aciona também esta mira
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
        put("shootKey", shootKey)
        put("shootSensX", shootSensX.toDouble()); put("shootSensY", shootSensY.toDouble())
        put("aimGeneral", aimGeneral.toDouble()); put("aimMouseSens", aimMouseSens.toDouble())
        put("aimAccelLevel", aimAccelLevel.toDouble())
        put("crossSize", crossSize.toDouble()); put("crossOpacity", crossOpacity.toDouble())
        put("aimInvert", aimInvert); put("fireWithAim", fireWithAim)
        put("aimPosX", aimPosX.toDouble()); put("aimPosY", aimPosY.toDouble())
        put("aimZoom", aimZoom.toDouble()); put("linkAimId", linkAimId)
        // Marca a semântica atual para migrar preferências antigas uma única vez.
        put("aimTuningScale", 2)
    }

    companion object {
        fun typeName(kind: Int): String = when (kind) {
            Kind.FIRE -> "FIRE"
            Kind.AIM -> "AIM"
            else -> "STANDARD"
        }

        /**
         * Lê um botão salvo. Aceita o formato novo ("type"/"mode") e os formatos antigos
         * ("kind" 0..4 = TAP, TOGGLE, MACRO, CLICK, AIM), convertendo para os 3 tipos.
         * Campos ausentes viram valores padrão; nunca lança por falta de campo opcional.
         */
        fun fromJson(b: JSONObject): HudButton {
            val key = b.getInt("key")
            val name = b.optString("name", "")
            val macro = b.optJSONArray("macro")?.let { m -> (0 until m.length()).map { m.getInt(it) } } ?: emptyList()
            val kind: Int
            val mode: Int
            if (b.has("type")) {
                kind = when (b.optString("type")) {
                    "FIRE" -> Kind.FIRE
                    "AIM" -> Kind.AIM
                    else -> Kind.STANDARD
                }
                mode = b.optInt("mode", Mode.HOLD).coerceIn(0, 3)
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
            val tuningV2 = b.optInt("aimTuningScale", 0) >= 2
            val oldMouseSens = b.optDouble("aimMouseSens", 1.0).toFloat()
            val oldGeneral = b.optDouble("aimGeneral", 1.0).toFloat()
            val oldPosX = b.optDouble("aimPosX", 0.5).toFloat()
            val oldPosY = b.optDouble("aimPosY", 0.5).toFloat()
            return HudButton(
                id = b.getInt("id"),
                key = key,
                x = b.optDouble("x", 0.5).toFloat(),
                y = b.optDouble("y", 0.5).toFloat(),
                size = b.optDouble("size", 0.07).toFloat(),
                opacity = b.optDouble("opacity", 1.0).toFloat(),
                kind = kind,
                mode = mode,
                delayMs = b.optInt("delayMs", 0).coerceIn(0, 500),
                macro = macro,
                name = name,
                aimSensX = b.optDouble("aimSensX", 0.9).toFloat(),
                aimSensY = b.optDouble("aimSensY", 0.9).toFloat(),
                aimToggle = b.optBoolean("aimToggle", false),
                mouseAim = b.optBoolean("mouseAim", true),
                rotMode = b.optInt("rotMode", -1).coerceIn(-1, 3),
                aimDeadzone = b.optDouble("aimDeadzone", 0.0).toFloat(),
                shootKey = b.optInt("shootKey", -1),
                shootSensX = b.optDouble("shootSensX", 1.0).toFloat(),
                shootSensY = b.optDouble("shootSensY", 1.0).toFloat(),
                aimGeneral = if (!tuningV2 && oldGeneral <= 5f) oldGeneral * 16240f else oldGeneral,
                aimMouseSens = if (!tuningV2 && oldMouseSens <= 5f) oldMouseSens * 69f else oldMouseSens,
                aimAccelLevel = b.optDouble("aimAccelLevel", 0.0).toFloat(),
                crossSize = b.optDouble("crossSize", 5.0).toFloat(),
                crossOpacity = b.optDouble("crossOpacity", 100.0).toFloat(),
                aimInvert = b.optBoolean("aimInvert", false),
                fireWithAim = b.optBoolean("fireWithAim", false),
                aimPosX = if (!b.has("aimPosX")) 199f else if (!tuningV2 && oldPosX in 0f..1f) oldPosX * 1000f else oldPosX,
                aimPosY = if (!b.has("aimPosY")) 199f else if (!tuningV2 && oldPosY in 0f..1f) oldPosY * 1000f else oldPosY,
                aimZoom = b.optDouble("aimZoom", 1.0).toFloat(),
                linkAimId = b.optInt("linkAimId", -1)
            )
        }
    }
}

/** Nome mostrado ao usuário: o nome dado ou um nome automático pelo tipo/tecla. */
fun HudButton.displayName(): String = name.ifEmpty {
    when (kind) {
        Kind.FIRE -> "Tiro"
        Kind.AIM -> "Mira"
        else -> "Botão ${keyLabel(key)}"
    }
}

fun defaultButtons(): List<HudButton> = listOf(
    HudButton(0, -1, 0.88f, 0.62f, 0.10f, kind = Kind.FIRE, mode = Mode.HOLD),
    HudButton(1, -2, 0.90f, 0.40f, kind = Kind.AIM),
    HudButton(2, KeyEvent.KEYCODE_SPACE, 0.92f, 0.80f),
    HudButton(3, KeyEvent.KEYCODE_CTRL_LEFT, 0.80f, 0.86f, mode = Mode.TOGGLE),
    HudButton(4, KeyEvent.KEYCODE_R, 0.76f, 0.72f)
)
