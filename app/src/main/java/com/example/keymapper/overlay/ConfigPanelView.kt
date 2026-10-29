package com.example.keymapper.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.example.keymapper.data.HudButton
import com.example.keymapper.data.Kind
import com.example.keymapper.data.Mode
import com.example.keymapper.data.Rot
import com.example.keymapper.data.displayName
import com.example.keymapper.data.keyLabel
import com.example.keymapper.util.Haptics
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Painel de configuração do botão, por cima do jogo. TUDO é aplicado NA HORA (sem Salvar/Confirmar):
 * cada mudança já vai para o motor de injeção e é gravada em segundo plano. "Fechar" só fecha o painel.
 *  - PADRÃO: tecla, modo (toque simples, segurar, alternar, duplo toque), tempo de resposta (0 a 500 ms)
 *  - TIRO: tecla e modo (toque único ou segurar)
 *  - MIRA: sensibilidade X e Y (1 a 10, 1 = neutro), suspender (toggle), deadzone
 */
class ConfigPanelView(
    ctx: Context,
    private val host: HudView.Host,
    private val buttonId: Int,
    private val density: Float,
    private val onDone: (saved: Boolean) -> Unit
) : LinearLayout(ctx) {

    private val scroll = ScrollView(ctx)
    private val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    /** 0 = não captura tecla, 1 = tecla do botão, 2 = tecla de tiro (mira). */
    private var listenTarget = 0
    private var hint = ""
    private var advancedOpen = false
    private val gray = Color.argb(255, 60, 60, 66)
    private val red = Color.argb(255, 150, 40, 40)
    private val dim = Color.argb(255, 170, 170, 180)

    /** Gravação em segundo plano (com pequena espera para não gravar a cada pixel do slider). */
    private val saveRunnable = Runnable { host.persist() }

    init {
        orientation = VERTICAL
        isFocusable = true
        isFocusableInTouchMode = true
        background = GradientDrawable().apply {
            setColor(Color.argb(246, 22, 22, 26))
            cornerRadius = dp(18).toFloat()
        }

        root.setPadding(dp(14), dp(10), dp(14), dp(8))
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val footer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(6), dp(10), dp(10))
        }
        footer.addView(
            btn("Fechar") { closeNow() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
        )
        addView(footer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        build()
    }

    private fun dp(v: Int) = (v * density).roundToInt()

    override fun onDetachedFromWindow() {
        removeCallbacks(saveRunnable)
        host.persist() // garante que a última mudança foi gravada
        super.onDetachedFromWindow()
    }

    // ---------- captura de tecla (qualquer tecla, inclusive Ctrl/Alt/Shift/F1-F12) ----------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (listenTarget == 0 && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) closeNow()
            return true
        }
        if (listenTarget == 0) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val code = event.keyCode
            val target = listenTarget
            listenTarget = 0
            when (code) {
                KeyEvent.KEYCODE_BACK -> hint = ""
                KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D ->
                    hint = "W, A, S e D são reservadas para o joystick."
                else -> {
                    hint = ""
                    assignKey(target, code)
                }
            }
            build()
        }
        return true
    }

    // ---------- edição (aplicação imediata) ----------

    private fun mod(f: (HudButton) -> HudButton) {
        val s = host.settings()
        host.update(s.copy(buttons = s.buttons.map { if (it.id == buttonId) f(it) else it })) // já vale no motor
        removeCallbacks(saveRunnable)
        postDelayed(saveRunnable, 400)
    }

    private fun assignKey(target: Int, code: Int) {
        when (target) {
            2 -> mod { it.copy(shootKey = code) }
            3 -> updateGlobal { it.copy(pauseKey = code) }
            else -> mod { it.copy(key = code) }
        }
    }

    private fun updateGlobal(transform: (com.example.keymapper.data.AppSettings) -> com.example.keymapper.data.AppSettings) {
        host.update(transform(host.settings()))
        removeCallbacks(saveRunnable)
        postDelayed(saveRunnable, 350)
    }

    /** Seção global em tabela compacta; cada valor válido é aplicado sem reconstruir o campo. */
    private fun buildBsSettings() {
        add(label("Configuração BS", 16f, Color.WHITE, true))
        add(label("Ajustes globais do mouse • aplicação imediata", 11f, dim))
        add(label("X usa escala lógica de 0 a 1000, convertida para a área do jogo; não são pixels físicos.", 10f, dim))
        val s = host.settings()
        add(numberSettingRow("Sensibilidade do mouse", s.bsMouseSensitivity.toString(), InputType.TYPE_CLASS_NUMBER) { raw ->
            raw.toIntOrNull()?.takeIf { it in 0..200 }?.let { value ->
                updateGlobal { it.copy(bsMouseSensitivity = value) }
            }
        })
        add(numberSettingRow("Relação de sensibilidade", s.bsSensitivityRatio.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { raw ->
            raw.replace(',', '.').toFloatOrNull()?.takeIf { it in 0.01f..10f }?.let { value ->
                updateGlobal { it.copy(bsSensitivityRatio = value) }
            }
        })
        add(numberSettingRow("Ajuste", s.bsFineTune.toString(), InputType.TYPE_CLASS_NUMBER) { raw ->
            raw.toIntOrNull()?.takeIf { it in 1..1_000_000 }?.let { value ->
                updateGlobal { it.copy(bsFineTune = value) }
            }
        })
        add(numberSettingRow("Olhar livre posição X", s.bsLookPositionX.toString(), InputType.TYPE_CLASS_NUMBER) { raw ->
            raw.toIntOrNull()?.takeIf { it in 0..1000 }?.let { value ->
                updateGlobal { it.copy(bsLookPositionX = value) }
            }
        })
        add(option("Suspender", "Tecla que alterna a suspensão do mapeamento. Padrão: NUM3."))
        add(btn(
            if (listenTarget == 3) "Pressione a tecla de suspensão..." else "Suspender: ${if (host.settings().pauseKey == KeyEvent.KEYCODE_NUMPAD_3) "NUM3" else keyLabel(host.settings().pauseKey)}",
            listenTarget == 3
        ) {
            listenTarget = 3
            hint = ""
            build()
        })
        if (hint.isNotEmpty()) add(label(hint, 11f, Color.YELLOW))
        val divider = View(context).apply {
            setBackgroundColor(Color.argb(100, 130, 140, 155))
        }
        add(divider)
        add(label("Configuração do botão selecionado", 14f, Color.WHITE, true))
    }

    private fun numberSettingRow(title: String, initial: String, inputTypeValue: Int, onValid: (String) -> Unit): View {
        val field = EditText(context).apply {
            setText(initial)
            setTextColor(Color.WHITE)
            setHintTextColor(dim)
            textSize = 14f
            inputType = inputTypeValue
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.argb(255, 17, 25, 38))
                setCornerRadius(dp(7).toFloat())
                setStroke(dp(1), Color.argb(255, 65, 82, 105))
            }
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val raw = s?.toString().orEmpty()
                if (raw.isNotBlank() && raw != "-" && raw != "." && raw != ",") onValid(raw)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 12f
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            addView(field, LinearLayout.LayoutParams(dp(110), dp(42)))
            setPadding(0, dp(2), 0, dp(2))
        }
    }

    private fun closeNow() {
        removeCallbacks(saveRunnable)
        Haptics.tick(this)
        onDone(true)
    }

    private fun setKeyDirect(target: Int, code: Int) {
        listenTarget = 0
        hint = ""
        assignKey(target, code)
        build()
    }

    private fun setKind(kind: Int) {
        mod { b ->
            b.copy(kind = kind, macro = if (kind == Kind.STANDARD) b.macro else emptyList())
        }
        build()
    }

    private fun deleteButton() {
        val s = host.settings()
        host.update(
            s.copy(buttons = s.buttons.filter { it.id != buttonId }
                .map { it.copy(macro = it.macro.filter { m -> m != buttonId }) })
        )
        host.persist()
        onDone(true)
    }

    // ---------- construção da UI ----------

    private fun build() {
        root.removeAllViews()
        val b = host.settings().buttons.firstOrNull { it.id == buttonId }
        if (b == null) {
            onDone(true)
            return
        }
        when (b.kind) {
            Kind.AIM -> buildAim(b)
            Kind.FIRE -> { buildBsSettings(); buildFire(b) }
            else -> { buildBsSettings(); buildStandard(b) }
        }
    }

    private fun moreButton() {
        add(btn(if (advancedOpen) "Ocultar mais opções" else "Mais opções", advancedOpen) {
            advancedOpen = !advancedOpen
            build()
        })
    }

    // ===== PADRÃO =====

    private fun buildStandard(b: HudButton) {
        val s = host.settings()
        add(label("Botão padrão  •  ${keyLabel(b.key)}", 16f, Color.WHITE, true))
        if (hint.isNotEmpty()) add(label(hint, 12f, Color.argb(255, 255, 200, 0)))
        typeRow(b)
        keyBlock(b, 1, "Tecla (mapeamento)", "Qualquer tecla: letras, números, F1-F12, Ctrl, Alt, Shift ou botão do mouse.")

        add(option("Modo de toque", modeHelp(b.mode)))
        add(row(
            btn("Toque simples", b.mode == Mode.TAP) { mod { it.copy(mode = Mode.TAP) }; build() },
            btn("Segurar", b.mode == Mode.HOLD) { mod { it.copy(mode = Mode.HOLD) }; build() }
        ))
        add(row(
            btn("Alternar (toggle)", b.mode == Mode.TOGGLE) { mod { it.copy(mode = Mode.TOGGLE) }; build() },
            btn("Duplo toque", b.mode == Mode.DOUBLE) { mod { it.copy(mode = Mode.DOUBLE) }; build() }
        ))
        add(slider(
            "Tempo de resposta (ms)", "Atraso entre apertar a tecla e o toque acontecer. 0 = imediato. De 0 a 500 ms.",
            b.delayMs.toFloat(), 0f, 500f, 5f, 0f, 500f, "%.0f"
        ) { v -> mod { it.copy(delayMs = v.roundToInt()) } })
        add(label("Não há repetição automática (rapid fire): um aperto nunca vira vários toques.", 11f, dim))

        moreButton()
        if (advancedOpen) {
            val others = s.buttons.filter { it.id != b.id }
            if (others.isNotEmpty()) {
                add(option("Macro (opcional)", "Escolha botões para acionar juntos. Com macro, este botão aciona só os escolhidos, não o próprio ponto."))
                val chips = ArrayList<View>()
                for (o in others) {
                    chips.add(btn(o.displayName() + " (" + keyLabel(o.key) + ")", b.macro.contains(o.id)) {
                        mod { cur ->
                            cur.copy(macro = if (cur.macro.contains(o.id)) cur.macro - o.id else cur.macro + o.id)
                        }
                        build()
                    })
                }
                for (chunk in chips.chunked(2)) add(row(*chunk.toTypedArray()))
            }
            appearance(b)
            add(btn("Apagar botão", false, red) { deleteButton() })
        }
    }

    // ===== TIRO =====

    private fun buildFire(b: HudButton) {
        val s = host.settings()
        add(label("Botão de tiro  •  ${keyLabel(b.key)}", 16f, Color.WHITE, true))
        if (hint.isNotEmpty()) add(label(hint, 12f, Color.argb(255, 255, 200, 0)))
        typeRow(b)
        keyBlock(b, 1, "Tecla de tiro", "Qualquer tecla ou botão do mouse (padrão: M1).")

        add(option("Modo de tiro", "Segurar: o toque desce ao apertar e só sobe quando você solta a tecla. Toque único: um toque curto por aperto."))
        add(row(
            btn("Toque único", b.mode == Mode.TAP) { mod { it.copy(mode = Mode.TAP) }; build() },
            btn("Segurar (hold)", b.mode != Mode.TAP) { mod { it.copy(mode = Mode.HOLD) }; build() }
        ))
        add(label("Não há rajada automática (burst): um aperto nunca vira vários toques.", 11f, dim))

        moreButton()
        if (advancedOpen) {
            add(option("Mirar ao atirar", "Segurar o tiro aciona também o botão de mira escolhido (e usa a sensibilidade dele)."))
            val aims = s.buttons.filter { it.kind == Kind.AIM }
            if (aims.isEmpty()) {
                add(label("Nenhum botão de mira. Crie um (+) e mude o tipo para Mira.", 11f, dim))
            } else {
                val opts = ArrayList<View>()
                opts.add(btn("Desligado", b.linkAimId < 0) { mod { it.copy(linkAimId = -1) }; build() })
                for (a in aims) {
                    opts.add(btn(a.displayName(), b.linkAimId == a.id) {
                        mod { it.copy(linkAimId = a.id) }
                        build()
                    })
                }
                for (chunk in opts.chunked(3)) add(row(*chunk.toTypedArray()))
            }
            appearance(b)
            add(btn("Apagar botão", false, red) { deleteButton() })
        }
    }

    // ===== MIRA =====

    private fun buildAim(b: HudButton) {
        add(label("Configuração BS", 16f, Color.WHITE, true))
        add(label("Ajustes numéricos da mira • aplicação imediata", 11f, dim))
        add(numberSettingRow("Sensibilidade do mouse", b.aimMouseSens.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { raw ->
            raw.replace(',', '.').toFloatOrNull()?.takeIf { it in 0f..200f }?.let { value -> mod { it.copy(aimMouseSens = value) } }
        })
        add(numberSettingRow("Relação de sensibilidade X", b.aimSensX.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { raw ->
            raw.replace(',', '.').toFloatOrNull()?.takeIf { it in 0.01f..10f }?.let { value -> mod { it.copy(aimSensX = value) } }
        })
        add(numberSettingRow("Relação de sensibilidade Y", b.aimSensY.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { raw ->
            raw.replace(',', '.').toFloatOrNull()?.takeIf { it in 0.01f..10f }?.let { value -> mod { it.copy(aimSensY = value) } }
        })
        add(numberSettingRow("Ajuste / Fine Tune", b.aimGeneral.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { raw ->
            raw.replace(',', '.').toFloatOrNull()?.takeIf { it in 1f..1_000_000f }?.let { value -> mod { it.copy(aimGeneral = value) } }
        })
        add(numberSettingRow("Olhar livre posição X", b.aimPosX.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { raw ->
            raw.replace(',', '.').toFloatOrNull()?.takeIf { it in 0f..1000f }?.let { value -> mod { it.copy(aimPosX = value) } }
        })
        add(numberSettingRow("Olhar livre posição Y", b.aimPosY.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { raw ->
            raw.replace(',', '.').toFloatOrNull()?.takeIf { it in 0f..1000f }?.let { value -> mod { it.copy(aimPosY = value) } }
        })
        add(toggle("Suspender (Toggle) · tecla NUM3", "Ligado: um toque ativa a mira e o próximo desativa. Desligado: a mira vale enquanto a tecla atribuída ao botão estiver pressionada. NUM3 é a tecla global de suspensão, configurável na tabela global.", b.aimToggle) { v ->
            mod { it.copy(aimToggle = v) }
        })
        add(label("Posições X/Y usam escala lógica de 0 a 1000; 199 é o valor inicial, não pixels físicos.", 10f, dim))
        if (hint.isNotEmpty()) add(label(hint, 12f, Color.argb(255, 255, 200, 0)))
        typeRow(b)
        add(slider(
            "Deadzone (zona morta)", "Ignora micro-movimentos do mouse para estabilizar a mira. 0 = desligado.",
            b.aimDeadzone, 0f, 5f, 0.1f, 0f, 100f, "%.1f"
        ) { v -> mod { it.copy(aimDeadzone = v) } })
        rotationBlock(b)
        add(label("Controle de recuo automático não é oferecido neste app.", 11f, dim))

        keyBlock(b, 1, "Tecla de mira", "Tecla que ativa a mira.")

        moreButton()
        if (advancedOpen) buildAimAdvanced(b)
    }

    private fun buildAimAdvanced(b: HudButton) {
        add(label("Tiro e sensibilidade", 14f, Color.WHITE, true))
        keyBlock(b, 2, "Tecla de tiro (Shoot)", "Tecla de tiro desta mira. Enquanto ela estiver pressionada, valem os multiplicadores de tiro abaixo.")
        add(label("Fine Tune é editado na tabela Configuração BS acima (16240 = referência neutra).", 11f, dim))
        add(slider(
            "Multiplicador de tiro X", "Sensibilidade horizontal extra enquanto a tecla de tiro está pressionada. 1.0 = sem mudança.",
            b.shootSensX, 0.1f, 5f, 0.05f, 0f, 1000f, "%.2f"
        ) { v -> mod { it.copy(shootSensX = v) } })
        add(slider(
            "Multiplicador de tiro Y", "Sensibilidade vertical extra enquanto a tecla de tiro está pressionada.",
            b.shootSensY, 0.1f, 5f, 0.05f, 0f, 1000f, "%.2f"
        ) { v -> mod { it.copy(shootSensY = v) } })
        add(label("Sensibilidade do mouse é editada na tabela Configuração BS acima (69 = referência neutra).", 11f, dim))
        add(slider(
            "Aceleração do mouse", "0 = nenhuma (1:1). Valores maiores aceleram movimentos rápidos. De 0 a 10.",
            b.aimAccelLevel, 0f, 10f, 0.5f, 0f, 10f, "%.1f"
        ) { v -> mod { it.copy(aimAccelLevel = v) } })
        add(slider(
            "Multiplicador de zoom", "Divide a sensibilidade: mira com mais zoom (ex.: 4x) exige movimento mais lento. 1.0 = sem efeito.",
            b.aimZoom, 1f, 8f, 0.1f, 1f, 20f, "%.1f"
        ) { v -> mod { it.copy(aimZoom = v) } })

        add(label("Mira (crosshair)", 14f, Color.WHITE, true))
        add(slider(
            "Tamanho da mira", "Cruz desenhada na tela enquanto a mira está ativa. De 0 a 10 (0 = oculta).",
            b.crossSize, 0f, 10f, 1f, 0f, 10f, "%.0f"
        ) { v -> mod { it.copy(crossSize = v) } })
        add(slider(
            "Opacidade da mira", "De 0 a 100 %. 0 = invisível.",
            b.crossOpacity, 0f, 100f, 5f, 0f, 100f, "%.0f"
        ) { v -> mod { it.copy(crossOpacity = v) } })
        add(slider(
            "Posição X da mira (%)", "Onde a cruz aparece e de onde o arrasto da câmera começa.",
            b.aimPosX / 10f, 0f, 100f, 0.5f, 0f, 100f, "%.1f"
        ) { v -> mod { it.copy(aimPosX = v * 10f) } })
        add(slider(
            "Posição Y da mira (%)", "0 = topo, 100 = base.",
            b.aimPosY / 10f, 0f, 100f, 0.5f, 0f, 100f, "%.1f"
        ) { v -> mod { it.copy(aimPosY = v * 10f) } })

        add(label("Opções", 14f, Color.WHITE, true))
        add(toggle("Inverter mira", "Mouse para cima = mira para baixo, só com esta mira ativa.", b.aimInvert) { v ->
            mod { it.copy(aimInvert = v) }
        })
        add(toggle("Atirar com a tecla de mira", "Ao ativar a mira, também aciona os botões ligados à tecla de tiro.", b.fireWithAim) { v ->
            mod { it.copy(fireWithAim = v) }
        })
        add(toggle("Mouse aim", "Ligado: o mouse move a câmera durante a mira. Desligado: o mouse fica parado durante a mira.", b.mouseAim) { v ->
            mod { it.copy(mouseAim = v) }
        })

        appearance(b)
        add(btn("Apagar botão", false, red) { deleteButton() })
    }

    // ---------- blocos reutilizáveis ----------

    /** Modo de rotação desta mira (sobrepõe o global em Configurações › Mira). */
    private fun rotationBlock(b: HudButton) {
        add(option(
            "Modo de rotação",
            "Como a câmera continua girando quando o mouse passa da borda. Centro: solta e toca no centro sem pausa (seguro, " +
                "serve em qualquer jogo). Infinito: sem limite de tela (jogos 3D). Automático: Infinito em shooters 3D conhecidos, " +
                "Centro nos demais. Desligado: a mira para na borda."
        ))
        add(row(
            btn("Usar o global", b.rotMode < 0) { mod { it.copy(rotMode = -1) }; build() },
            btn("Automático", b.rotMode == Rot.AUTO) { mod { it.copy(rotMode = Rot.AUTO) }; build() }
        ))
        add(row(
            btn("Somente Centro", b.rotMode == Rot.CENTER) { mod { it.copy(rotMode = Rot.CENTER) }; build() },
            btn("Somente Infinito", b.rotMode == Rot.INFINITE) { mod { it.copy(rotMode = Rot.INFINITE) }; build() }
        ))
        add(row(
            btn("Desligado (para na borda)", b.rotMode == Rot.OFF) { mod { it.copy(rotMode = Rot.OFF) }; build() }
        ))
    }

    /** Captura de tecla + atalhos M1/M2/M3. [target] 1 = tecla do botão, 2 = tecla de tiro (mira). */
    private fun keyBlock(b: HudButton, target: Int, title: String, help: String) {
        val current = if (target == 2) b.shootKey else b.key
        add(option(title, help))
        add(btn(if (listenTarget == target) "Pressione a tecla agora..." else "$title: ${keyLabel(current)}", listenTarget == target) {
            listenTarget = target
            hint = ""
            build()
        })
        add(row(
            btn("M1", current == -1) { setKeyDirect(target, -1) },
            btn("M2", current == -2) { setKeyDirect(target, -2) },
            btn("M3", current == -3) { setKeyDirect(target, -3) }
        ))
    }

    private fun appearance(b: HudButton) {
        add(label("Aparência e posição do botão", 14f, Color.WHITE, true))
        add(option("Nome do botão", "Aparece abaixo do botão no modo edição e nas listas. Deixe em branco para o nome automático."))
        add(nameField(b))
        add(slider(
            "Tamanho (% da área do jogo)", "Diâmetro do botão. Escala sozinho em qualquer monitor ou resolução.",
            b.size * 100f, 1f, 30f, 0.5f, 1f, 100f, "%.1f"
        ) { v -> mod { it.copy(size = v / 100f) } })
        add(slider(
            "Opacidade (%)", "Quanto o botão aparece no HUD. 100 = sólido; valores baixos deixam quase invisível.",
            b.opacity * 100f, 10f, 100f, 5f, 5f, 100f, "%.0f"
        ) { v -> mod { it.copy(opacity = v / 100f) } })
        add(slider(
            "Posição X (%)", "Onde o toque cai na horizontal. 0 = borda esquerda, 100 = direita. Ajuste fino de 0,1.",
            b.x * 100f, 0f, 100f, 0.1f, 0f, 100f, "%.1f"
        ) { v -> mod { it.copy(x = v / 100f) } })
        add(slider(
            "Posição Y (%)", "Onde o toque cai na vertical. 0 = topo, 100 = base. Posicione sobre o botão do jogo.",
            b.y * 100f, 0f, 100f, 0.1f, 0f, 100f, "%.1f"
        ) { v -> mod { it.copy(y = v / 100f) } })
    }

    private fun typeRow(b: HudButton) {
        add(option("Tipo do botão", "Padrão: simula uma tecla. Tiro: segurar contínuo. Mira: sensibilidade própria, suspender e deadzone. Toque em Mira para ver as opções da mira."))
        add(row(
            btn("Padrão", b.kind == Kind.STANDARD) { setKind(Kind.STANDARD) },
            btn("Tiro", b.kind == Kind.FIRE) { setKind(Kind.FIRE) },
            btn("Mira", b.kind == Kind.AIM) { setKind(Kind.AIM) }
        ))
    }

    private fun modeHelp(mode: Int) = when (mode) {
        Mode.TAP -> "Toque simples: um toque curto por aperto da tecla."
        Mode.TOGGLE -> "Alternar: aperte uma vez para ligar e outra para desligar (ex.: agachar)."
        Mode.DOUBLE -> "Duplo toque: um aperto envia dois toques curtos seguidos."
        else -> "Segurar: o toque desce ao apertar e sobe só quando você solta a tecla."
    }

    // ---------- componentes ----------

    private fun add(v: View) {
        root.addView(
            v,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dp(3), 0, dp(3)) }
        )
    }

    private fun label(t: String, size: Float = 13f, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(context).apply {
            text = t
            setTextColor(color)
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(2))
        }

    /** Campo de texto para o nome do botão (aplica ao concluir ou ao sair do campo). */
    private fun nameField(b: HudButton): View {
        val et = EditText(context)
        et.apply {
            setText(b.name)
            setHint(b.displayName())
            setHintTextColor(dim)
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        fun commit() {
            val t = et.text.toString().trim().take(24)
            if (t != b.name) mod { it.copy(name = t) }
        }
        et.setOnEditorActionListener { _, _, _ ->
            commit()
            true
        }
        et.setOnFocusChangeListener { _, has -> if (!has) commit() }
        return et
    }

    /** Título + explicação (o "?" de cada opção). */
    private fun option(title: String, help: String): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 13f
        })
        addView(TextView(context).apply {
            text = help
            setTextColor(dim)
            textSize = 11f
        })
    }

    /** Linha com título, explicação e um interruptor. */
    private fun toggle(title: String, help: String, value: Boolean, onChange: (Boolean) -> Unit): View {
        val self = this
        val sw = Switch(context)
        sw.isChecked = value
        sw.setOnCheckedChangeListener { _, checked ->
            onChange(checked)
            Haptics.tick(self)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(option(title, help), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(sw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun btn(t: String, selected: Boolean = false, base: Int = gray, onClick: () -> Unit) =
        Button(context).apply {
            text = t
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(if (selected) Color.argb(255, 0, 140, 90) else base)
                cornerRadius = dp(10).toFloat()
            }
            minHeight = 0
            minimumHeight = dp(40)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { onClick() }
        }

    private fun row(vararg views: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        for (v in views) {
            addView(
                v,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
            )
        }
    }

    /**
     * Slider com [−] [+] e campo numérico. O slider cobre a faixa prática [lo, hi];
     * o campo aceita qualquer valor entre [absLo] e [absHi]. Cada mudança vale na hora.
     */
    private fun slider(
        title: String, help: String, value: Float,
        lo: Float, hi: Float, step: Float, absLo: Float, absHi: Float, fmt: String,
        onChange: (Float) -> Unit
    ): View {
        val steps = ((hi - lo) / step).roundToInt()
        var cur = value.coerceIn(absLo, absHi)
        val sb = SeekBar(context)
        val et = EditText(context)
        val self = this

        fun fmtv(v: Float) = String.format(Locale.US, fmt, v)
        fun progressOf(v: Float) = ((v.coerceIn(lo, hi) - lo) / step).roundToInt().coerceIn(0, steps)

        fun setValue(v: Float) {
            cur = v.coerceIn(absLo, absHi)
            sb.progress = progressOf(cur)
            et.setText(fmtv(cur))
            onChange(cur)
            Haptics.tick(self)
        }

        sb.max = steps
        sb.progress = progressOf(cur)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                cur = (lo + p * step).coerceIn(absLo, absHi)
                et.setText(fmtv(cur))
                onChange(cur)
            }

            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {
                Haptics.tick(self)
            }
        })

        et.apply {
            setText(fmtv(cur))
            setTextColor(Color.WHITE)
            textSize = 13f
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            minWidth = dp(72)
            gravity = Gravity.CENTER
            setSelectAllOnFocus(true)
        }

        fun commit() {
            val v = et.text.toString().replace(',', '.').toFloatOrNull()
            if (v == null) {
                et.setText(fmtv(cur))
            } else {
                setValue(v)
            }
        }
        et.setOnEditorActionListener { _, _, _ ->
            commit()
            true
        }
        et.setOnFocusChangeListener { _, has -> if (!has) commit() }

        val line = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(btn("−") { setValue(cur - step) }, LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(sb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(btn("+") { setValue(cur + step) }, LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(et, LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(option(title, help))
            addView(line)
        }
    }
}
