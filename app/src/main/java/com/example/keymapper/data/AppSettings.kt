package com.example.keymapper.data

import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

val DEFAULT_GAMES = listOf("com.dts.freefireth", "com.dts.freefiremax")

data class AppSettings(
    // --- ponteiro / sensibilidade ---
    val pointerSpeed: Int = 6,         // 1..11 (estilo Windows; 6 = padrão = movimento 1:1)
    val epp: Boolean = false,          // "Melhorar precisão do ponteiro" (aceleração)
    val sensX: Float = 1f,
    val sensY: Float = 1f,
    val globalSens: Float = 1f,        // multiplica todos os botões
    // --- parâmetros de compatibilidade "Configuração BS" ---
    val bsMouseSensitivity: Int = 69,
    val bsSensitivityRatio: Float = 0.9f,
    val bsFineTune: Int = 16240,
    val bsLookPositionX: Int = 199,         // posição horizontal lógica (0..1000) mapeada para 0..100% da área do jogo
    val curve: Int = 0,                // 0 linear, 1 exponencial, 2 customizada, 3 desacelerar
    val exponent: Float = 1.5f,
    val knee: Float = 8f,
    val gain: Float = 1.5f,
    val invertY: Boolean = false,
    val deadzone: Float = 0f,          // ignora ruído do sensor (contagens do mouse)
    val releaseDelayMs: Int = 0,       // 0..100 ms
    val calibTarget: Float = 0.5f,
    // --- áreas do HUD ---
    val joyX: Float = 0.14f,
    val joyY: Float = 0.72f,
    val joyR: Float = 0.10f,
    val lookX: Float = 0.70f,
    val lookY: Float = 0.50f,
    val lookR: Float = 0.35f,          // área de câmera maior = menos quebras do arrasto
    // --- HUD ---
    val hudStyle: Int = 0,             // 0 clássico, 1 neon, 2 BlueStacks, 3 minimalista, 4 ícones
    val showHud: Boolean = true,
    val hudAlpha: Float = 1f,
    val buttonScale: Float = 1f,       // tamanho global dos botões
    val gridSnap: Int = 0,             // 0 = sem grade; senão, divisões da área
    // --- tela / DeX ---
    val areaMode: Int = 0,             // 0 tela inteira, 1 proporção fixa, 2 janela manual
    val areaRatio: Float = 16f / 9f,
    val areaX: Float = 0f,
    val areaY: Float = 0f,
    val areaW: Float = 1f,
    val areaH: Float = 1f,
    val uiScale: Float = 1f,
    // --- desempenho / sistema ---
    val refreshHz: Int = 0,            // 0 = automático (máximo)
    val perfMode: Int = 0,             // 0 equilibrado, 1 alta performance, 2 economia
    val haptics: Boolean = true,
    val theme: Int = 0,
    val autoStart: Boolean = false,
    val editKey: Int = KeyEvent.KEYCODE_F1,
    val pauseKey: Int = KeyEvent.KEYCODE_NUMPAD_3,
    // --- diagnóstico / câmera ---
    val useExternalDisplay: Boolean = false, // usar o monitor externo (DeX); padrão: tela do aparelho
    val hudWindow: Boolean = true,        // diagnóstico: janela do HUD (tela cheia, transparente)
    val safeOverlay: Boolean = true,      // modo compatível: janelas sem aceleração por GPU
    val captureWindow: Boolean = true,    // janela 1x1 focável que captura mouse/teclado
    val lookMode: Int = Rot.CENTER,       // modo de rotação: 0 Centro (padrão/fallback seguro), 1 Infinito, 2 Desligado (para na borda), 3 Automático
    val lookIdleMs: Int = 1500,           // canvas infinito: soltar o dedo após inatividade (0 = nunca); só reancora o ponto de partida
    val relativeMouse: Boolean = false,   // EXPERIMENTAL: injeta mouse relativo (REL_X/REL_Y) em vez de arrasto de toque
    val lookIntervalMs: Int = 4,          // câmera: intervalo fixo entre toques enviados (4 ms = 250 Hz)
    // --- jogos ---
    val onlyInGame: Boolean = true,       // liga ao abrir o jogo e desliga ao sair
    val games: List<String> = DEFAULT_GAMES,
    val buttons: List<HudButton> = defaultButtons()
) {
    fun toJson(): String = JSONObject().apply {
        put("pointerSpeed", pointerSpeed); put("epp", epp)
        put("sensX", sensX.toDouble()); put("sensY", sensY.toDouble())
        put("globalSens", globalSens.toDouble())
        put("bsMouseSensitivity", bsMouseSensitivity)
        put("bsSensitivityRatio", bsSensitivityRatio.toDouble())
        put("bsFineTune", bsFineTune)
        put("bsLookPositionX", bsLookPositionX)
        put("curve", curve); put("exponent", exponent.toDouble())
        put("knee", knee.toDouble()); put("gain", gain.toDouble())
        put("invertY", invertY); put("deadzone", deadzone.toDouble())
        put("releaseDelayMs", releaseDelayMs); put("calibTarget", calibTarget.toDouble())
        put("joyX", joyX.toDouble()); put("joyY", joyY.toDouble()); put("joyR", joyR.toDouble())
        put("lookX", lookX.toDouble()); put("lookY", lookY.toDouble()); put("lookR", lookR.toDouble())
        put("hudStyle", hudStyle); put("showHud", showHud); put("hudAlpha", hudAlpha.toDouble())
        put("buttonScale", buttonScale.toDouble()); put("gridSnap", gridSnap)
        put("areaMode", areaMode); put("areaRatio", areaRatio.toDouble())
        put("areaX", areaX.toDouble()); put("areaY", areaY.toDouble())
        put("areaW", areaW.toDouble()); put("areaH", areaH.toDouble())
        put("uiScale", uiScale.toDouble())
        put("refreshHz", refreshHz); put("perfMode", perfMode)
        put("haptics", haptics); put("theme", theme); put("autoStart", autoStart)
        put("editKey", editKey); put("pauseKey", pauseKey)
        put("safeOverlay", safeOverlay); put("captureWindow", captureWindow)
        put("useExternalDisplay", useExternalDisplay); put("hudWindow", hudWindow)
        put("lookModeV2", lookMode); put("lookIdleMs", lookIdleMs); put("relativeMouse", relativeMouse)
        put("lookIntervalMs", lookIntervalMs)
        put("onlyInGame", onlyInGame); put("games", JSONArray(games))
        put("buttons", JSONArray().apply { buttons.forEach { put(it.toJson()) } })
    }.toString()

    companion object {
        fun fromJson(text: String): AppSettings = try {
            val o = JSONObject(text)
            val d = AppSettings()
            fun f(k: String, def: Float) = o.optDouble(k, def.toDouble()).toFloat()
            AppSettings(
                pointerSpeed = o.optInt("pointerSpeed", d.pointerSpeed).coerceIn(1, 11),
                epp = o.optBoolean("epp", false),
                sensX = f("sensX", d.sensX), sensY = f("sensY", d.sensY),
                globalSens = f("globalSens", d.globalSens),
                bsMouseSensitivity = o.optInt("bsMouseSensitivity", d.bsMouseSensitivity).coerceIn(0, 200),
                bsSensitivityRatio = f("bsSensitivityRatio", d.bsSensitivityRatio).coerceIn(0.01f, 10f),
                bsFineTune = o.optInt("bsFineTune", d.bsFineTune).coerceIn(1, 1_000_000),
                // Migra o formato anterior (0..100 = porcentagem) para a escala lógica 0..1000.
                // O padrão novo 199 é preservado; não representa pixels físicos.
                bsLookPositionX = o.optInt("bsLookPositionX", d.bsLookPositionX).let { value ->
                    when {
                        !o.has("bsLookPositionX") -> d.bsLookPositionX
                        value in 0..100 -> value * 10
                        value in 0..1000 -> value
                        else -> d.bsLookPositionX
                    }
                },
                curve = o.optInt("curve", d.curve), exponent = f("exponent", d.exponent),
                knee = f("knee", d.knee), gain = f("gain", d.gain),
                invertY = o.optBoolean("invertY", false), deadzone = f("deadzone", d.deadzone),
                releaseDelayMs = o.optInt("releaseDelayMs", d.releaseDelayMs),
                calibTarget = f("calibTarget", d.calibTarget),
                joyX = f("joyX", d.joyX), joyY = f("joyY", d.joyY), joyR = f("joyR", d.joyR),
                lookX = f("lookX", d.lookX), lookY = f("lookY", d.lookY), lookR = f("lookR", d.lookR).let { if (it <= 0.21f) d.lookR else it }, // migra o tamanho antigo (0.20) para o novo padrão
                hudStyle = o.optInt("hudStyle", 0), showHud = o.optBoolean("showHud", true),
                hudAlpha = f("hudAlpha", d.hudAlpha),
                buttonScale = f("buttonScale", d.buttonScale), gridSnap = o.optInt("gridSnap", 0),
                areaMode = o.optInt("areaMode", 0), areaRatio = f("areaRatio", d.areaRatio),
                areaX = f("areaX", d.areaX), areaY = f("areaY", d.areaY),
                areaW = f("areaW", d.areaW), areaH = f("areaH", d.areaH),
                uiScale = f("uiScale", d.uiScale),
                refreshHz = o.optInt("refreshHz", 0), perfMode = o.optInt("perfMode", 0),
                haptics = o.optBoolean("haptics", true), theme = o.optInt("theme", 0),
                autoStart = o.optBoolean("autoStart", false),
                editKey = o.optInt("editKey", d.editKey), pauseKey = o.optInt("pauseKey", d.pauseKey),
                safeOverlay = o.optBoolean("safeOverlay", true), captureWindow = o.optBoolean("captureWindow", true),
                useExternalDisplay = o.optBoolean("useExternalDisplay", false), hudWindow = o.optBoolean("hudWindow", true),
                lookMode = o.optInt("lookModeV2", Rot.CENTER).coerceIn(0, 3), lookIdleMs = o.optInt("lookIdleMs", 1500).coerceIn(0, 60000),
                relativeMouse = o.optBoolean("relativeMouse", false),
                lookIntervalMs = o.optInt("lookIntervalMs", 4).coerceIn(1, 20),
                onlyInGame = o.optBoolean("onlyInGame", true),
                games = o.optJSONArray("games")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: d.games,
                buttons = o.optJSONArray("buttons")?.let { a ->
                    // tolerante: um botão corrompido é ignorado, os demais são mantidos
                    val list = (0 until a.length()).mapNotNull { i ->
                        try {
                            HudButton.fromJson(a.getJSONObject(i))
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (list.isEmpty() && a.length() > 0) defaultButtons() else list
                } ?: defaultButtons()
            )
        } catch (e: Exception) {
            AppSettings()
        }
    }
}
