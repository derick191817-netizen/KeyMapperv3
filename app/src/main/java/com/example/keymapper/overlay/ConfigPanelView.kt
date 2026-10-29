package com.example.keymapper.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
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
        if (target == 2) mod { it.copy(shootKey = code) } else mod { it.copy(key = code) }
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
            Kind.FIRE -> buildFire(b)
            else -> buildStandard(b)
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
        add(label("Botão de mira  •  ${keyLabel(b.key)}", 16f, Color.WHITE, true))
        if (hint.isNotEmpty()) add(label(hint, 12f, Color.argb(255, 255, 200, 0)))
        typeRow(b)

        add(slider(
            "Sensibilidade X", "Velocidade horizontal com a mira ativa. 1 = neutro (igual ao mouse), 10 = dez vezes mais rápido. Para ficar mais lento, digite menos de 1 (ex.: 0.5).",
            b.aimSensX, 1f, 10f, 0.1f, 0f, 1000f, "%.1f"
        ) { v -> mod { it.copy(aimSensX = v) } })
        add(slider(
            "Sensibilidade Y", "Velocidade vertical com a mira ativa. 1 = neutro, 10 = dez vezes mais rápido.",
            b.aimSensY, 1f, 10f, 0.1f, 0f, 1000f, "%.1f"
        ) { v -> mod { it.copy(aimSensY = v) } })
        add(toggle("Suspender (Toggle)", "Ligado: um toque na tecla ativa a mira e a mantém; o próximo toque desativa. Desligado: a mira vale só enquanto a tecla está pressionada.", b.aimToggle) { v ->
            mod { it.copy(aimToggle = v) }
        })
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
        add(slider(
            "Sensibilidade geral", "Multiplica toda a sensibilidade da mira. 1.0 = sem mudança. Digite até 1000.",
            b.aimGeneral, 0.1f, 5f, 0.05f, 0f, 1000f, "%.2f"
        ) { v -> mod { it.copy(aimGeneral = v) } })
        add(slider(
            "Multiplicador de tiro X", "Sensibilidade horizontal extra enquanto a tecla de tiro está pressionada. 1.0 = sem mudança.",
            b.shootSensX, 0.1f, 5f, 0.05f, 0f, 1000f, "%.2f"
        ) { v -> mod { it.copy(shootSensX = v) } })
        add(slider(
            "Multiplicador de tiro Y", "Sensibilidade vertical extra enquanto a tecla de tiro está pressionada.",
            b.shootSensY, 0.1f, 5f, 0.05f, 0f, 1000f, "%.2f"
        ) { v -> mod { it.copy(shootSensY = v) } })
        add(slider(
            "Sensibilidade do mouse", "Escala o movimento do mouse antes da aceleração.",
            b.aimMouseSens, 0.1f, 5f, 0.05f, 0f, 1000f, "%.2f"
        ) { v -> mod { it.copy(aimMouseSens = v) } })
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
            b.aimPosX * 100f, 0f, 100f, 0.5f, 0f, 100f, "%.1f"
        ) { v -> mod { it.copy(aimPosX = v / 100f) } })
        add(slider(
            "Posição Y da mira (%)", "0 = topo, 100 = base.",
            b.aimPosY * 100f, 0f, 100f, 0.5f, 0f, 100f, "%.1f"
        ) { v -> mod { it.copy(aimPosY = v / 100f) } })

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
