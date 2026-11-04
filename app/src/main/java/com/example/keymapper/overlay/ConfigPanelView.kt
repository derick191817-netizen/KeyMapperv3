package com.example.keymapper.overlay

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.InputFilter
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.view.MotionEvent
import android.widget.CheckBox
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
import com.example.keymapper.data.MacroAction
import com.example.keymapper.data.MacroStep
import com.example.keymapper.data.MacroStepPolicy
import com.example.keymapper.data.Rot
import com.example.keymapper.data.displayName
import com.example.keymapper.data.keyLabel
import com.example.keymapper.domain.MappingEditorPolicy
import com.example.keymapper.domain.InputBindingPolicy
import com.example.keymapper.domain.NumericSettingPolicy
import com.example.keymapper.util.Haptics
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Painel de configuração do botão, por cima do jogo. Alterações discretas são aplicadas ao estado
 * em memória; campos numéricos são confirmados ao concluir a edição ou perder foco. Salvar persiste,
 * enquanto Cancelar restaura o snapshot original.
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
    private val gray = Color.rgb(26, 39, 51)
    private val red = Color.rgb(95, 35, 50)
    private val dim = Color.rgb(159, 181, 196)
    private val accent = Color.rgb(112, 224, 206)

    /** Snapshot imutável usado para restaurar o estado anterior se o usuário cancelar. */
    private val originalSettings = host.settings()
    private var completed = false
    private var globalSettingsOpen = false
    private data class NumericFieldCommit(val field: EditText, val commit: () -> Boolean)
    private val numericFieldCommits = mutableListOf<NumericFieldCommit>()

    init {
        orientation = VERTICAL
        isFocusable = true
        isFocusableInTouchMode = true
        background = GradientDrawable().apply {
            setColor(Color.rgb(9, 15, 21))
            setStroke(dp(1), Color.rgb(42, 65, 77))
            cornerRadius = dp(22).toFloat()
        }

        root.setPadding(dp(14), dp(10), dp(14), dp(8))
        addView(buildPanelHeader(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(8), dp(8), dp(8), dp(4))
        })
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val footer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(6), dp(10), dp(10))
        }
        footer.addView(
            btn("Cancelar", false, red) { finish(false) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
        )
        footer.addView(
            btn("Salvar", true, gray) { finish(true) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
        )
        footer.background = GradientDrawable().apply {
            setColor(Color.rgb(15, 24, 32))
            cornerRadius = dp(16).toFloat()
        }
        addView(footer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(8), dp(4), dp(8), dp(8))
        })

        build()
    }

    private fun dp(v: Int) = (v * density).roundToInt()

    private fun buildPanelHeader(): View {
        val button = host.settings().buttons.firstOrNull { it.id == buttonId }
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(20, 48, 59), Color.rgb(18, 29, 42), Color.rgb(14, 19, 23))
            ).apply { cornerRadius = dp(16).toFloat() }

            val emblem = TextView(context).apply {
                text = "KM"
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(7, 31, 34))
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(accent)
                    cornerRadius = dp(11).toFloat()
                }
            }
            addView(emblem, LayoutParams(dp(42), dp(42)))

            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(11), 0, dp(8), 0)
                addView(TextView(context).apply {
                    text = "KEYMAPPER STUDIO"
                    textSize = 9f
                    letterSpacing = 0.12f
                    setTextColor(accent)
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = button?.displayName() ?: "Configuração do controle"
                    textSize = 16f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = "PROPRIEDADES E MAPEAMENTO"
                    textSize = 9f
                    letterSpacing = 0.08f
                    setTextColor(dim)
                })
            }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(btn("×", false, red) { finish(true) }, LayoutParams(dp(42), dp(42)))
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // FLAG_WATCH_OUTSIDE_TOUCH envia ACTION_OUTSIDE para esta janela.
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            closeNow()
            return true
        }
        if (captureMouseBinding(event)) return true
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (captureMouseBinding(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    /** Enquanto uma associação está armada, cliques do mouse são capturados aqui, não ativam o painel. */
    private fun captureMouseBinding(event: MotionEvent): Boolean {
        val target = listenTarget
        if (target == 0 || event.pointerCount == 0) return false
        val mouseSource = event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE ||
            (event.source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
        if (!mouseSource) return false
        val bit = when (event.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS -> event.actionButton
            MotionEvent.ACTION_DOWN -> InputBindingPolicy.mouseButtonMask(
                InputBindingPolicy.firstMouseButtonCode(event.buttonState) ?: return false
            ) ?: return false
            else -> return false
        }
        if (target == 3) {
            listenTarget = 0
            hint = "O atalho global precisa ser uma tecla física; botões do mouse não são aceitos aqui."
            build()
            return true
        }
        val code = InputBindingPolicy.mouseButtonCode(bit) ?: run {
            hint = "Esse botão não foi reconhecido pelo Android ou pelo driver do mouse."
            build()
            return true
        }
        listenTarget = 0
        hint = ""
        assignKey(target, code)
        build()
        return true
    }

    override fun onDetachedFromWindow() {
        // Se o Android remover o painel por rotação/encerramento do overlay, preserve o rascunho.
        // Um Cancelar já restaura o snapshot e marca a edição como concluída antes do detach.
        if (!completed) {
            // Garante que campos válidos sejam confirmados antes do detach. Rascunhos inválidos
            // nunca são enviados ao modelo; o último valor válido continua sendo persistido.
            findFocus()?.clearFocus()
            commitNumericFields()
            completed = true
            host.persist()
        }
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
            if (code == KeyEvent.KEYCODE_UNKNOWN) {
                hint = "Essa tecla não foi reconhecida. Pressione outra tecla ou cancele."
                build()
                return true
            }
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
    }

    private fun assignKey(target: Int, code: Int) {
        val settings = host.settings()
        if (target == 3) {
            if (code == settings.editKey) {
                hint = "A tecla de suspender não pode ser igual à tecla de abrir o editor."
                return
            }
            hint = ""
            updateGlobal { it.copy(pauseKey = code) }
            return
        }
        if (MappingEditorPolicy.conflictsWithGlobalShortcut(code, settings.editKey, settings.pauseKey)) {
            hint = "Essa tecla já é um atalho global do KeyMapper. Escolha outra tecla."
            return
        }
        if (target == 1) {
            val conflict = MappingEditorPolicy.findConflict(
                candidateCode = code,
                targetControlId = buttonId,
                bindings = settings.buttons.map {
                    MappingEditorPolicy.KeyBinding(it.id, it.displayName(), it.key)
                }
            )
            if (conflict != null) {
                hint = "${keyLabel(code)} já está atribuída a '${conflict.label}'. Troque a outra associação primeiro."
                return
            }
        }
        hint = ""
        when (target) {
            2 -> mod { it.copy(shootKey = code) }
            else -> mod { it.copy(key = code) }
        }
    }

    private fun updateGlobal(transform: (com.example.keymapper.data.AppSettings) -> com.example.keymapper.data.AppSettings) {
        host.update(transform(host.settings()))
    }

    /** Painel compacto inspirado no editor de controles BlueStacks. */
    private fun buildBsSettings() {
        add(btn(
            if (globalSettingsOpen) "Ocultar ajustes globais de mouse/câmera" else "Ajustes globais de mouse/câmera",
            globalSettingsOpen
        ) {
            globalSettingsOpen = !globalSettingsOpen
            build()
        })
        if (!globalSettingsOpen) return
        val s = host.settings()
        add(TextView(context).apply {
            text = "Ajustes globais de mouse e câmera"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(8))
        })
        add(tableHeader("Parâmetro", "Valor"))
        add(divider())

        add(tableKeyRow("Suspender", keyLabel(s.pauseKey)) {
            listenTarget = 3
            hint = "Pressione uma tecla para definir Suspender"
            build()
        })
        add(divider())
        add(tableNumberRow("Sensibilidade do mouse", s.bsMouseSensitivity.toString(), InputType.TYPE_CLASS_NUMBER) { raw ->
            val value = NumericSettingPolicy.intValue(raw, 0..200) ?: return@tableNumberRow false
            updateGlobal { it.copy(bsMouseSensitivity = value) }
            true
        })
        add(divider())
        add(tableNumberRow("Relação de sensibilidade", s.bsSensitivityRatio.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED) { raw ->
            val value = NumericSettingPolicy.floatValue(raw, 0.01f, 10f) ?: return@tableNumberRow false
            updateGlobal { it.copy(bsSensitivityRatio = value) }
            true
        })
        add(divider())
        add(tableNumberRow("Ajuste", s.bsFineTune.toString(), InputType.TYPE_CLASS_NUMBER) { raw ->
            val value = NumericSettingPolicy.intValue(raw, 1..1_000_000) ?: return@tableNumberRow false
            updateGlobal { it.copy(bsFineTune = value) }
            true
        })
        add(divider())
        add(tableNumberRow("Posição X da câmera livre", s.bsLookPositionX.toString(), InputType.TYPE_CLASS_NUMBER) { raw ->
            val value = NumericSettingPolicy.intValue(raw, 0..1000) ?: return@tableNumberRow false
            updateGlobal { it.copy(bsLookPositionX = value) }
            true
        })
        add(divider())
        add(sectionTitle("Modo de câmera"))
        add(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val enabled = s.lookMode != Rot.OFF
            addView(CheckBox(context).apply {
                text = "Movimento contínuo da câmera"
                setTextColor(Color.WHITE)
                isChecked = enabled
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(125, 211, 252))
                setOnCheckedChangeListener { _, checked ->
                    updateGlobal { it.copy(lookMode = if (checked) Rot.AUTO else Rot.OFF, relativeMouse = false) }
                }
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
        })
        add(option(
            "Padrão global",
            "Esta opção define a estratégia padrão. Um controle Sight/Aim pode sobrescrevê-la individualmente nas propriedades do botão."
        ))
        add(divider())
        add(sectionTitle("CONFIGURAÇÃO DO BOTÃO SELECIONADO"))
    }

    private fun tableHeader(left: String, right: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(5), dp(8), dp(5))
        addView(TextView(context).apply {
            text = left
            setTextColor(Color.rgb(125, 211, 252))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = right
            setTextColor(Color.rgb(125, 211, 252))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(112), ViewGroup.LayoutParams.WRAP_CONTENT))
        setBackgroundColor(Color.rgb(22, 31, 37))
    }

    private fun tableKeyRow(title: String, value: String, onClick: () -> Unit): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(2), dp(4), dp(2))
        addView(TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 13f
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        addView(btn(value, false, gray, onClick), LinearLayout.LayoutParams(dp(112), dp(38)))
    }

    /**
     * Campo numérico transacional: texto digitado permanece como rascunho até Done ou perda de foco.
     * Valores inválidos são destacados e nunca chegam ao motor/configuração.
     */
    private fun numericField(
        initial: String,
        inputTypeValue: Int,
        onCommit: (String) -> Boolean
    ): EditText {
        val normalStroke = Color.rgb(47, 77, 91)
        val invalidStroke = Color.rgb(220, 76, 76)
        var lastCommitted = initial
        val field = EditText(context).apply {
            setText(initial)
            setTextColor(Color.WHITE)
            setHintTextColor(dim)
            textSize = 14f
            inputType = inputTypeValue
            setSingleLine(true)
            setShowSoftInputOnFocus(true)
            isFocusable = true
            isFocusableInTouchMode = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            background = GradientDrawable().apply {
                setColor(Color.rgb(13, 24, 33))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), normalStroke)
            }
        }

        fun setFieldStroke(color: Int) {
            (field.background.mutate() as? GradientDrawable)?.setStroke(dp(1), color)
        }

        fun commit(): Boolean {
            val raw = field.text?.toString()?.trim().orEmpty()
            if (raw == lastCommitted) {
                field.error = null
                setFieldStroke(normalStroke)
                return true
            }
            val accepted = raw.isNotEmpty() && runCatching { onCommit(raw) }.getOrDefault(false)
            if (accepted) {
                lastCommitted = raw
                field.error = null
                setFieldStroke(normalStroke)
                return true
            }
            field.error = "Valor inválido ou fora do intervalo"
            setFieldStroke(invalidStroke)
            return false
        }

        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                field.error = null
                setFieldStroke(normalStroke)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (commit()) {
                    field.clearFocus()
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.hideSoftInputFromWindow(field.windowToken, 0)
                }
                true
            } else false
        }
        field.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) commit()
        }
        field.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                v.requestFocus()
                v.post {
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            false
        }
        numericFieldCommits += NumericFieldCommit(field) { commit() }
        return field
    }

    private fun tableNumberRow(
        title: String,
        initial: String,
        inputTypeValue: Int,
        onValid: (String) -> Boolean
    ): View {
        val field = numericField(initial, inputTypeValue, onCommit = onValid)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(2), dp(4), dp(2))
            addView(TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 13f
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            addView(field, LinearLayout.LayoutParams(dp(112), dp(38)))
        }
    }

    private fun numberSettingRow(
        title: String,
        initial: String,
        inputTypeValue: Int,
        onValid: (String) -> Boolean
    ): View {
        val field = numericField(initial, inputTypeValue, onCommit = onValid)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 12f
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            addView(field, LinearLayout.LayoutParams(dp(118), dp(42)))
            setPadding(0, dp(2), 0, dp(2))
        }
    }

    private fun commitNumericFields(): Boolean {
        var allValid = true
        numericFieldCommits.forEach { entry ->
            if (!entry.commit()) allValid = false
        }
        return allValid
    }

    private fun closeNow() = finish(true)

    private fun finish(saveChanges: Boolean) {
        if (completed) return
        // O campo pode continuar focado quando o usuário toca em Salvar/Cancelar ou no X.
        findFocus()?.clearFocus()
        if (saveChanges && !commitNumericFields()) {
            numericFieldCommits.firstOrNull { it.field.error != null }?.field?.let { invalid ->
                invalid.requestFocus()
                invalid.post {
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.showSoftInput(invalid, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            return
        }
        completed = true
        if (saveChanges) {
            host.persist()
        } else {
            host.update(originalSettings)
            host.persist()
        }
        Haptics.tick(this)
        onDone(saveChanges)
    }

    private fun setKeyDirect(target: Int, code: Int) {
        listenTarget = 0
        hint = ""
        assignKey(target, code)
        build()
    }

    private fun setKind(kind: Int) {
        mod { b ->
            val safeMode = when (kind) {
                Kind.FIRE -> if (b.mode == Mode.TAP) Mode.TAP else Mode.HOLD
                else -> if (kind == Kind.STANDARD) b.mode else Mode.HOLD
            }
            b.copy(
                kind = kind,
                mode = safeMode,
                macro = if (kind == Kind.STANDARD) b.macro else emptyList(),
                isCombo = kind == Kind.STANDARD && b.isCombo,
                sequence = if (kind == Kind.MACRO) b.sequence else emptyList()
            )
        }
        build()
    }

    private fun deleteButton() {
        val s = host.settings()
        host.update(
            s.copy(buttons = s.buttons.filter { it.id != buttonId }
                .map { it.copy(
                    macro = it.macro.filter { m -> m != buttonId },
                    sequence = it.sequence.filter { step -> step.action == MacroAction.WAIT || step.targetId != buttonId },
                    linkAimId = if (it.linkAimId == buttonId) -1 else it.linkAimId
                ) })
        )
        completed = true
        host.persist()
        onDone(true)
    }

    // ---------- construção da UI ----------

    private fun build() {
        // Não destrói o rascunho de um campo inválido ao reconstruir o painel.
        if (!commitNumericFields()) return
        numericFieldCommits.clear()
        root.removeAllViews()
        val b = host.settings().buttons.firstOrNull { it.id == buttonId }
        if (b != null && b.kind == Kind.STANDARD &&
            (b.isCombo || b.name.equals("Rapid Tap", true))
        ) {
            advancedOpen = true
        }
        if (b?.kind == Kind.MACRO) advancedOpen = true
        if (b == null) {
            completed = true
            onDone(true)
            return
        }
        buildBsSettings()
        if (hint.isNotEmpty()) add(label(hint, 12f, Color.argb(255, 255, 200, 0)))
        when (b.kind) {
            Kind.AIM -> buildAim(b)
            Kind.FIRE -> buildFire(b)
            Kind.SWIPE -> buildSwipe(b)
            Kind.MACRO -> buildMacro(b)
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
        add(label("${b.displayName()}  •  ${keyLabel(b.key)}", 16f, Color.WHITE, true))
        typeRow(b)
        keyBlock(b, 1, "Tecla (mapeamento)", "Pressione uma tecla física ou clique em um botão do mouse; também há opções explícitas abaixo.")

        if (!b.isCombo && b.macro.isEmpty()) {
            add(option("Modo de toque", modeHelp(b.mode)))
            add(row(
                btn("Toque simples", b.mode == Mode.TAP) {
                    mod { it.copy(mode = Mode.TAP, name = if (it.name.equals("Rapid Tap", true)) "Tap" else it.name) }
                    build()
                },
                btn("Segurar", b.mode == Mode.HOLD) {
                    mod { it.copy(mode = Mode.HOLD, name = if (it.name.equals("Rapid Tap", true)) "Hold" else it.name) }
                    build()
                }
            ))
            add(row(
                btn("Alternar (toggle)", b.mode == Mode.TOGGLE) {
                    mod { it.copy(mode = Mode.TOGGLE, name = if (it.name.equals("Rapid Tap", true)) "Hold" else it.name) }
                    build()
                },
                btn("Duplo toque", b.mode == Mode.DOUBLE) {
                    mod { it.copy(mode = Mode.DOUBLE, name = if (it.name.equals("Rapid Tap", true)) "Hold" else it.name) }
                    build()
                }
            ))
        } else {
            add(option("Combo simultâneo", "Este controle aciona apenas os destinos selecionados. Não executa um toque no próprio marcador."))
        }
        add(slider(
            "Tempo de resposta (ms)", "Atraso entre apertar a tecla e o toque acontecer. 0 = imediato. De 0 a 500 ms.",
            b.delayMs.toFloat(), 0f, 500f, 5f, 0f, 500f, "%.0f"
        ) { v -> mod { it.copy(delayMs = v.roundToInt()) } })
        if (advancedOpen && !b.isCombo && b.macro.isEmpty()) {
            add(divider())
            add(option("Rapid Tap", "Repete toques somente enquanto a entrada física estiver pressionada. A soltura cancela imediatamente; Fire não usa essa repetição."))
            add(btn(if (b.mode == Mode.RAPID) "Desativar Rapid Tap" else "Ativar Rapid Tap", b.mode == Mode.RAPID) {
                mod { current ->
                    if (current.mode == Mode.RAPID) {
                        current.copy(
                            mode = Mode.HOLD,
                            name = if (current.name.equals("Rapid Tap", true)) "Hold" else current.name
                        )
                    } else {
                        current.copy(
                            mode = Mode.RAPID,
                            macro = emptyList(),
                            isCombo = false,
                            name = if (current.name.equals("Tap", true) || current.name.equals("Hold", true)) "Rapid Tap" else current.name
                        )
                    }
                }
                build()
            })
            if (b.mode == Mode.RAPID) {
                add(slider(
                    "Intervalo de repetição (ms)", "Tempo entre toques; valores menores aumentam a frequência. Mínimo de 40 ms para permitir a liberação de cada toque.",
                    b.rapidIntervalMs.toFloat(), 40f, 2_000f, 10f, 40f, 2_000f, "%.0f"
                ) { v -> mod { it.copy(rapidIntervalMs = v.roundToInt()) } })
            }
        }

        // Propriedades comuns não ficam escondidas em "Mais opções": cada botão tem nome,
        // tamanho, opacidade e posição editáveis logo na primeira abertura do painel.
        appearance(b)
        add(btn("Apagar botão", false, red) { deleteButton() })
        moreButton()
        if (advancedOpen) {
            add(divider())
            add(option("Combo simultâneo", "Aciona vários controles no mesmo pressionamento. É diferente de uma macro sequencial; o Combo vazio não produz toque."))
            add(btn(if (b.isCombo || b.macro.isNotEmpty()) "Desativar Combo" else "Ativar Combo", b.isCombo || b.macro.isNotEmpty()) {
                mod { cur ->
                    val enable = !(cur.isCombo || cur.macro.isNotEmpty())
                    cur.copy(
                        isCombo = enable,
                        macro = if (enable) cur.macro else emptyList(),
                        mode = Mode.HOLD,
                        name = when {
                            enable && (cur.name.equals("Tap", true) || cur.name.equals("Hold", true) || cur.name.equals("Rapid Tap", true) || cur.name.isBlank()) -> "Combo"
                            !enable && cur.name.equals("Combo", true) -> "Hold"
                            else -> cur.name
                        }
                    )
                }
                build()
            })
            if (b.isCombo || b.macro.isNotEmpty()) {
                val others = s.buttons.filter { MappingEditorPolicy.isComboTargetEligible(b.id, it) }
                if (others.isEmpty()) {
                    add(label("Adicione outro controle ao perfil para selecioná-lo como destino do Combo.", 11f, dim))
                } else {
                    val chips = ArrayList<View>()
                    for (o in others) {
                        chips.add(btn(o.displayName() + " (" + keyLabel(o.key) + ")", b.macro.contains(o.id)) {
                            mod { cur ->
                                cur.copy(
                                    macro = if (cur.macro.contains(o.id)) cur.macro - o.id else cur.macro + o.id,
                                    isCombo = true,
                                    mode = Mode.HOLD
                                )
                            }
                            build()
                        })
                    }
                    for (chunk in chips.chunked(2)) add(row(*chunk.toTypedArray()))
                }
            }
        }
    }

    // ===== TIRO =====

    private fun buildFire(b: HudButton) {
        val s = host.settings()
        add(label("Fire · Disparo  •  ${keyLabel(b.key)}", 16f, Color.WHITE, true))
        typeRow(b)
        keyBlock(b, 1, "Tecla de tiro", "Tecla ou botão do mouse para acionar o disparo; escolha o mouse nos botões abaixo.")

        add(option("Modo de tiro", "Segurar: o toque desce ao apertar e só sobe quando você solta a tecla. Toque único: um toque curto por aperto."))
        add(row(
            btn("Toque único", b.mode == Mode.TAP) { mod { it.copy(mode = Mode.TAP) }; build() },
            btn("Segurar (hold)", b.mode != Mode.TAP) { mod { it.copy(mode = Mode.HOLD) }; build() }
        ))
        add(label("Não há rajada automática (burst): um aperto nunca vira vários toques.", 11f, dim))

        // Propriedades visuais e exclusão ficam disponíveis sem precisar expandir o painel.
        appearance(b)
        add(btn("Apagar botão", false, red) { deleteButton() })
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
        }
    }

    // ===== SWIPE =====

    private fun buildSwipe(b: HudButton) {
        add(label("Swipe / Arrastar  •  ${keyLabel(b.key)}", 16f, Color.WHITE, true))
        typeRow(b)
        add(option("Gesto de arraste", "Ao pressionar a entrada, o toque começa na posição X/Y do controle, percorre a trajetória e termina no destino configurado. Soltar antes do fim cancela o gesto e libera o ponteiro."))
        keyBlock(b, 1, "Tecla ou botão de ativação", "Associe uma tecla ou um botão físico do mouse.")
        add(divider())
        add(sectionTitle("1 · DESTINO"))
        add(slider(
            "Destino X (%)", "0 = borda esquerda; 100 = direita.",
            b.swipeEndX * 100f, 0f, 100f, 1f, 0f, 100f, "%.0f"
        ) { v -> mod { it.copy(swipeEndX = v / 100f) } })
        add(slider(
            "Destino Y (%)", "0 = topo; 100 = base.",
            b.swipeEndY * 100f, 0f, 100f, 1f, 0f, 100f, "%.0f"
        ) { v -> mod { it.copy(swipeEndY = v / 100f) } })
        add(slider(
            "Duração (ms)", "Duração total do arraste. A soltura da tecla cancela e encerra o toque imediatamente.",
            b.swipeDurationMs.toFloat(), 50f, 2_000f, 10f, 50f, 2_000f, "%.0f"
        ) { v -> mod { it.copy(swipeDurationMs = v.roundToInt()) } })
        add(divider())
        add(sectionTitle("2 · ORIGEM"))
        add(label("A origem é a posição X/Y do marcador do Swipe. Arraste o marcador no HUD ou ajuste as posições na seção de aparência.", 11f, dim))
        appearance(b)
        add(btn("Apagar Swipe", false, red) { deleteButton() })
    }

    // ===== MACRO SEQUENCIAL =====

    private fun buildMacro(b: HudButton) {
        val settings = host.settings()
        add(label("Macro sequencial  •  ${keyLabel(b.key)}", 16f, Color.WHITE, true))
        typeRow(b)
        keyBlock(b, 1, "Atalho de execução", "Segure a tecla ou o botão do mouse para executar a sequência. Soltar cancela a etapa atual e libera o toque.")
        add(option(
            "Execução em sequência",
            "As etapas são executadas uma por vez: pausa, toque, pressionamento temporário ou arraste. Não é o Combo simultâneo."
        ))
        add(slider(
            "Repetições da sequência", "Quantidade de execuções completas enquanto o atalho permanecer pressionado. Limitado a 20 ciclos.",
            b.sequenceRepeatCount.toFloat(), 1f, MacroStepPolicy.MAX_REPEATS.toFloat(), 1f,
            1f, MacroStepPolicy.MAX_REPEATS.toFloat(), "%.0f"
        ) { value -> mod { it.copy(sequenceRepeatCount = MacroStepPolicy.repeatCount(value.roundToInt())) } })

        add(divider())
        add(sectionTitle("ETAPAS · ${b.sequence.size}/${MacroStepPolicy.MAX_STEPS}"))
        if (b.sequence.isEmpty()) {
            add(label("Nenhuma etapa configurada. Adicione uma pausa ou escolha um controle existente como destino.", 11f, dim))
        }
        b.sequence.take(MacroStepPolicy.MAX_STEPS).forEachIndexed { index, step ->
            val target = settings.buttons.firstOrNull { it.id == step.targetId }
            val summary = when (step.action) {
                MacroAction.WAIT -> "${index + 1}. Pausa · ${step.durationMs} ms"
                MacroAction.TAP -> "${index + 1}. Toque · ${target?.displayName() ?: "Destino ausente"} · 25 ms"
                MacroAction.HOLD -> "${index + 1}. Segurar · ${target?.displayName() ?: "Destino ausente"} · ${step.durationMs} ms"
                MacroAction.SWIPE -> "${index + 1}. Arrastar · ${target?.displayName() ?: "Destino ausente"} · ${step.durationMs} ms"
                else -> "${index + 1}. Etapa inválida"
            }
            add(option(summary, "Etapas em ordem. O Macro usa o ponto salvo do controle de destino; Swipe usa também o destino X/Y dele."))
            if (step.action == MacroAction.WAIT || step.action == MacroAction.HOLD || step.action == MacroAction.SWIPE) {
                val minDuration = if (step.action == MacroAction.SWIPE) 50 else MacroStepPolicy.MIN_DURATION_MS
                val maxDuration = if (step.action == MacroAction.SWIPE) 2_000 else MacroStepPolicy.MAX_DURATION_MS
                add(slider(
                    "Duração da etapa ${index + 1} (ms)", "Tempo da pausa, do pressionamento ou do arraste.",
                    step.durationMs.toFloat(), minDuration.toFloat(), maxDuration.toFloat(),
                    10f, minDuration.toFloat(), maxDuration.toFloat(), "%.0f"
                ) { value ->
                    mod { current ->
                        current.copy(sequence = current.sequence.mapIndexed { i, item ->
                            if (i == index) item.copy(durationMs = MacroStepPolicy.durationMs(item.action, value.roundToInt())) else item
                        })
                    }
                })
            }
            add(btn("Remover etapa ${index + 1}", false, red) {
                mod { current -> current.copy(sequence = current.sequence.filterIndexed { i, _ -> i != index }) }
                build()
            })
            add(divider())
        }

        val canAdd = b.sequence.size < MacroStepPolicy.MAX_STEPS
        add(sectionTitle("ADICIONAR ETAPA"))
        add(row(btn("Pausa", false) {
            if (canAdd) {
                mod { it.copy(sequence = (it.sequence + MacroStep(MacroAction.WAIT, -1, 250)).take(MacroStepPolicy.MAX_STEPS)) }
                build()
            }
        }))
        val tapTargets = settings.buttons.filter { MappingEditorPolicy.isMacroTargetEligible(b.id, MacroAction.TAP, it) }
        val holdTargets = settings.buttons.filter { MappingEditorPolicy.isMacroTargetEligible(b.id, MacroAction.HOLD, it) }
        val swipeTargets = settings.buttons.filter { MappingEditorPolicy.isMacroTargetEligible(b.id, MacroAction.SWIPE, it) }
        add(label("Toque · escolha um controle Tap/Hold ou Fire", 11f, dim))
        addMacroTargetButtons(b, MacroAction.TAP, tapTargets, canAdd)
        add(label("Segurar · escolha um controle Tap/Hold ou Fire", 11f, dim))
        addMacroTargetButtons(b, MacroAction.HOLD, holdTargets, canAdd)
        add(label("Arrastar · escolha um controle Swipe", 11f, dim))
        addMacroTargetButtons(b, MacroAction.SWIPE, swipeTargets, canAdd)
        if (!canAdd) add(label("Limite de ${MacroStepPolicy.MAX_STEPS} etapas atingido.", 11f, dim))

        add(option(
            "Cancelamento e segurança",
            "Soltar o atalho, pausar o KeyMapper, perder a sessão ou liberar todas as entradas cancela a macro e envia UP para qualquer toque ainda pressionado."
        ))
        appearance(b)
        add(btn("Apagar Macro", false, red) { deleteButton() })
    }

    private fun addMacroTargetButtons(owner: HudButton, action: Int, targets: List<HudButton>, enabled: Boolean) {
        if (targets.isEmpty()) {
            add(label("Nenhum destino compatível cadastrado. Adicione primeiro um controle apropriado.", 11f, dim))
            return
        }
        val buttons = targets.take(48).map { target ->
            btn("${target.displayName()} · ${keyLabel(target.key)}", false) {
                if (enabled) {
                    val duration = if (action == MacroAction.TAP) 25 else 250
                    mod { current ->
                        current.copy(sequence = (current.sequence + MacroStep(action, target.id, duration))
                            .take(MacroStepPolicy.MAX_STEPS))
                    }
                    build()
                }
            }.apply { isEnabled = enabled }
        }
        buttons.chunked(2).forEach { group -> add(row(*group.toTypedArray())) }
        if (targets.size > 48) add(label("Mais ${targets.size - 48} destinos não exibidos; use a lista de controles para reduzir duplicatas.", 10f, dim))
    }

    // ===== MIRA =====

    private fun buildAim(b: HudButton) {
        add(label("Editor de controles", 17f, Color.WHITE, true))
        add(label("Configuração da mira · alterações aplicadas imediatamente", 11f, dim))

        add(divider())
        add(sectionTitle("1 · TECLAS"))
        keyBlock(b, 1, "Tecla de mira", "Tecla física ou botão do mouse que ativa a mira.")
        keyBlock(b, 2, "Tecla de tiro", "Tecla associada ao disparo enquanto esta mira está ativa.")
        add(label("Modo Suspender", 12f, Color.WHITE))
        add(btn(
            if (listenTarget == 3) "Pressione a tecla de suspensão…" else "Suspender: ${keyLabel(host.settings().pauseKey)}",
            listenTarget == 3
        ) {
            listenTarget = 3
            hint = ""
            build()
        })
        add(toggle("Alternar mira", "Ligado: cada pressionamento alterna entre mira ativa e suspensa.", b.aimToggle) { value ->
            mod { it.copy(aimToggle = value) }
        })

        add(divider())
        add(sectionTitle("2 · SENSIBILIDADE"))
        add(numberSettingRow("Sensibilidade do mouse", b.aimMouseSens.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { raw ->
            val value = NumericSettingPolicy.floatValue(raw, 0f, 200f) ?: return@numberSettingRow false
            mod { it.copy(aimMouseSens = value) }
            true
        })
        add(numberSettingRow("Relação X", b.aimSensX.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { raw ->
            val value = NumericSettingPolicy.floatValue(raw, 0.01f, 10f) ?: return@numberSettingRow false
            mod { it.copy(aimSensX = value) }
            true
        })
        add(numberSettingRow("Relação Y", b.aimSensY.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { raw ->
            val value = NumericSettingPolicy.floatValue(raw, 0.01f, 10f) ?: return@numberSettingRow false
            mod { it.copy(aimSensY = value) }
            true
        })
        add(numberSettingRow("Ajuste (Fine Tune)", b.aimGeneral.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { raw ->
            val value = NumericSettingPolicy.floatValue(raw, 1f, 1_000_000f) ?: return@numberSettingRow false
            mod { it.copy(aimGeneral = value) }
            true
        })

        add(divider())
        add(sectionTitle("3 · POSIÇÃO"))
        add(numberSettingRow("Posição X", b.aimPosX.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { raw ->
            val value = NumericSettingPolicy.floatValue(raw, 0f, 1000f) ?: return@numberSettingRow false
            mod { it.copy(aimPosX = value) }
            true
        })
        add(numberSettingRow("Posição Y", b.aimPosY.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { raw ->
            val value = NumericSettingPolicy.floatValue(raw, 0f, 1000f) ?: return@numberSettingRow false
            mod { it.copy(aimPosY = value) }
            true
        })
        add(label("X/Y usam escala lógica de 0 a 1000. Os valores padrão 199, 199, 69, 0,9 e 16240 são preservados.", 10f, dim))
        if (hint.isNotEmpty()) add(label(hint, 11f, Color.YELLOW))
        moreButton()
        if (advancedOpen) {
            add(divider())
            typeRow(b)
            add(slider(
                "Deadzone", "Ignora micro-movimentos do mouse. 0 = desligado.",
                b.aimDeadzone, 0f, 5f, 0.1f, 0f, 100f, "%.1f"
            ) { v -> mod { it.copy(aimDeadzone = v) } })
            add(slider(
                "Raio de recentralização (AUTO/Nonstop)",
                "Inspirado no Radius do Aiming Mode: 0% preserva o limite retangular antigo; valores maiores recentralizam em um círculo. Usado nos modos AUTO/Nonstop; Boundary (OFF) continua limitado e INFINITE usa mouse relativo nativo. O raio nunca ultrapassa os limites seguros do toque.",
                b.aimBoundaryRadiusPct, 0f, 45f, 1f, 0f, 45f, "%.0f%%"
            ) { v -> mod { it.copy(aimBoundaryRadiusPct = v) } })
            rotationBlock(b)
            buildAimAdvanced(b)
        }
    }

    private fun buildAimAdvanced(b: HudButton) {
        add(label("Tiro e sensibilidade", 14f, Color.WHITE, true))
        keyBlock(b, 2, "Tecla de tiro (Shoot)", "Tecla de tiro desta mira. Enquanto ela estiver pressionada, valem os multiplicadores de tiro abaixo.")
        add(label("Fine Tune é editado na tabela Ajustes globais de mouse/câmera acima (16240 = referência neutra).", 11f, dim))
        add(slider(
            "Multiplicador de tiro X", "Sensibilidade horizontal extra enquanto a tecla de tiro está pressionada. 1.0 = sem mudança.",
            b.shootSensX, 0.1f, 5f, 0.05f, 0f, 1000f, "%.2f"
        ) { v -> mod { it.copy(shootSensX = v) } })
        add(slider(
            "Multiplicador de tiro Y", "Sensibilidade vertical extra enquanto a tecla de tiro está pressionada.",
            b.shootSensY, 0.1f, 5f, 0.05f, 0f, 1000f, "%.2f"
        ) { v -> mod { it.copy(shootSensY = v) } })
        add(label("Sensibilidade do mouse é editada na tabela Ajustes globais de mouse/câmera acima (69 = referência neutra).", 11f, dim))
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

        add(label("Comportamento de contato", 14f, Color.WHITE, true))
        val inheritAutoRelease = b.aimAutoReleaseMs == -1
        add(toggle(
            "Herdar liberação automática global",
            "Ligado: usa o tempo global da aba Mira. Desligado: esta mira possui um tempo próprio.",
            inheritAutoRelease
        ) { useGlobal ->
            mod {
                it.copy(
                    aimAutoReleaseMs = if (useGlobal) -1 else host.settings().lookIdleMs.coerceIn(0, 60_000)
                )
            }
            build()
        })
        if (inheritAutoRelease) {
            add(label("Tempo global atual: ${host.settings().lookIdleMs} ms (0 = manter contato).", 11f, dim))
        } else {
            add(slider(
                "Liberação automática desta mira (ms)",
                "0 mantém o toque até desativar a mira; valores positivos liberam depois que o mouse fica parado.",
                b.aimAutoReleaseMs.toFloat(), 0f, 5000f, 100f, 0f, 60_000f, "%.0f"
            ) { v -> mod { it.copy(aimAutoReleaseMs = v.toInt().coerceIn(0, 60_000)) } })
        }

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
            "Movimento contínuo da câmera",
            "Escolha se esta mira herda a configuração global ou usa uma estratégia própria. Nonstop mantém o contato global ativo durante a troca, mas o jogo recebe temporariamente dois ponteiros; alguns jogos não aceitam esse gesto."
        ))
        add(row(
            btn("Usar o global", b.rotMode < 0) { mod { it.copy(rotMode = -1) }; build() },
            btn("Automático", b.rotMode == Rot.AUTO || b.rotMode == Rot.CENTER) { mod { it.copy(rotMode = Rot.AUTO) }; build() }
        ))
        add(row(
            btn("Nonstop", b.rotMode == Rot.NONSTOP) { mod { it.copy(rotMode = Rot.NONSTOP) }; build() },
            btn("Relativo nativo", b.rotMode == Rot.INFINITE) { mod { it.copy(rotMode = Rot.INFINITE) }; build() }
        ))
        add(row(
            btn("Limite ativo", b.rotMode == Rot.OFF) { mod { it.copy(rotMode = Rot.OFF) }; build() }
        ))
    }

    /** Captura de tecla + atalhos M1..M5. [target] 1 = tecla do botão, 2 = tecla de tiro (mira). */
    private fun keyBlock(b: HudButton, target: Int, title: String, help: String) {
        val current = if (target == 2) b.shootKey else b.key
        add(option(title, help))
        add(btn(if (listenTarget == target) "Pressione a tecla agora..." else "$title: ${keyLabel(current)}", listenTarget == target) {
            listenTarget = target
            hint = ""
            build()
        })
        add(row(
            btn("Clique Esquerdo", current == -1) { setKeyDirect(target, -1) },
            btn("Clique Direito", current == -2) { setKeyDirect(target, -2) }
        ))
        add(row(
            btn("Botão do Meio", current == -3) { setKeyDirect(target, -3) },
            btn("Lateral Voltar", current == -4) { setKeyDirect(target, -4) }
        ))
        add(row(
            btn("Lateral Avançar", current == -5) { setKeyDirect(target, -5) },
            btn("Roda ↑", current == KeyEvent.KEYCODE_PAGE_UP) { setKeyDirect(target, KeyEvent.KEYCODE_PAGE_UP) }
        ))
        add(row(
            btn("Roda ↓", current == KeyEvent.KEYCODE_PAGE_DOWN) { setKeyDirect(target, KeyEvent.KEYCODE_PAGE_DOWN) },
            btn("Roda ←", current == KeyEvent.KEYCODE_DPAD_LEFT) { setKeyDirect(target, KeyEvent.KEYCODE_DPAD_LEFT) }
        ))
        add(row(
            btn("Roda →", current == KeyEvent.KEYCODE_DPAD_RIGHT) { setKeyDirect(target, KeyEvent.KEYCODE_DPAD_RIGHT) }
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
        add(option("Tipo do controle", "Tap/Hold/Combo usam controles padrão; Fire dispara; Aim/Sight controla a câmera; Swipe arrasta; Macro executa uma sequência temporizada."))
        add(row(
            btn("Padrão", b.kind == Kind.STANDARD) { setKind(Kind.STANDARD) },
            btn("Fire", b.kind == Kind.FIRE) { setKind(Kind.FIRE) },
            btn("Aim/Sight", b.kind == Kind.AIM) { setKind(Kind.AIM) }
        ))
        add(row(
            btn("Swipe", b.kind == Kind.SWIPE) { setKind(Kind.SWIPE) },
            btn("Macro", b.kind == Kind.MACRO) { setKind(Kind.MACRO) }
        ))
    }

    private fun modeHelp(mode: Int) = when (mode) {
        Mode.TAP -> "Toque simples: um toque curto por aperto da tecla."
        Mode.TOGGLE -> "Alternar: aperte uma vez para ligar e outra para desligar (ex.: agachar)."
        Mode.DOUBLE -> "Duplo toque: um aperto envia dois toques curtos seguidos."
        Mode.RAPID -> "Rapid Tap: repete toques enquanto a entrada física permanece pressionada."
        else -> "Segurar: o toque desce ao apertar e sobe só quando você solta a tecla."
    }

    // ---------- componentes ----------

    private fun sectionTitle(title: String): TextView = TextView(context).apply {
        text = title
        setTextColor(accent)
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        letterSpacing = 0.08f
        setPadding(0, dp(5), 0, dp(3))
    }

    private fun divider(): View = View(context).apply {
        setBackgroundColor(Color.rgb(44, 76, 88))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(4)
            bottomMargin = dp(4)
        }
    }

    private fun add(v: View) {
        root.addView(
            v,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dp(5), 0, dp(5)) }
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
            filters = arrayOf(InputFilter.LengthFilter(24))
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        var latest = b.name
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val next = s?.toString().orEmpty().take(24)
                if (next != latest) {
                    latest = next
                    mod { it.copy(name = next) }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        fun commit() {
            val t = et.text.toString().trim().take(24)
            if (t != latest) {
                latest = t
                mod { it.copy(name = t) }
            }
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
                setColor(if (selected) Color.rgb(22, 91, 88) else base)
                setStroke(dp(1), if (selected) Color.argb(105, Color.red(accent), Color.green(accent), Color.blue(accent)) else Color.rgb(53, 73, 86))
                cornerRadius = dp(12).toFloat()
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
