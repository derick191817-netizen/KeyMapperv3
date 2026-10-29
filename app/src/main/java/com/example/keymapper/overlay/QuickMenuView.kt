import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
package com.example.keymapper.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.example.keymapper.input.PointerSpeed
import com.example.keymapper.util.Haptics
import kotlin.math.roundToInt

/** Menu rápido (toque na bolha): pausar, esconder HUD, velocidade do ponteiro, abrir o app. */
class QuickMenuView(
    ctx: Context,
    private val host: HudView.Host,
    private val density: Float,
    private val onClose: () -> Unit
) : LinearLayout(ctx) {

    private val gray = Color.rgb(35, 43, 49)

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            setColor(Color.argb(248, 14, 19, 23))
            cornerRadius = dp(16).toFloat()
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        build()
    }

    private fun dp(v: Int) = (v * density).roundToInt()

    private fun build() {
        removeAllViews()
        addRow(TextView(context).apply {
            text = "Menu rápido"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        })

        addRow(btn(if (host.isPaused()) "Retomar mapeamento" else "Pausar mapeamento") {
            host.togglePause()
            onClose()
        })
        addRow(btn(if (host.settings().showHud) "Esconder HUD" else "Mostrar HUD") {
            host.toggleHud()
            onClose()
        })

        // velocidade do ponteiro 1..11 (meio = 6 = padrão)
        val tv = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        fun show(level: Int) {
            tv.text = "Velocidade do ponteiro: ${PointerSpeed.label(level)}"
        }
        val start = host.settings().pointerSpeed.coerceIn(1, 11)
        show(start)
        val sb = SeekBar(context)
        sb.max = 10
        sb.progress = start - 1
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                show(p + 1)
                host.update(host.settings().copy(pointerSpeed = p + 1))
            }

            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {
                host.persist()
                Haptics.tick(this@QuickMenuView)
            }
        })
        addRow(tv)
        addRow(sb)

        addRow(TextView(context).apply {
            text = "Ajustes rápidos"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(2))
        })

        fun addSensitivitySlider(label: String, initial: Float, horizontal: Boolean) {
            val value = TextView(context).apply {
                setTextColor(Color.rgb(161, 176, 184))
                textSize = 12f
            }
            fun updateLabel(v: Float) {
                value.text = "$label: ${String.format(java.util.Locale.US, "%.2f", v)}x"
            }
            updateLabel(initial)
            val slider = SeekBar(context).apply {
                max = 500
                progress = (initial.coerceIn(0f, 5f) * 100).roundToInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val v = progress / 100f
                        updateLabel(v)
                        val current = host.settings()
                        host.update(if (horizontal) current.copy(sensX = v) else current.copy(sensY = v))
                    }
                    override fun onStartTrackingTouch(seek: SeekBar) {}
                    override fun onStopTrackingTouch(seek: SeekBar) {
                        host.persist()
                        Haptics.tick(this@QuickMenuView)
                    }
                })
            }
            addRow(value)
            addRow(slider)
        }

        val currentSettings = host.settings()
        addSensitivitySlider("Sensibilidade X", currentSettings.sensX, true)
        addSensitivitySlider("Sensibilidade Y", currentSettings.sensY, false)
        addRow(btn("Centralizar sensibilidade (1,00x)") {
            host.update(host.settings().copy(sensX = 1f, sensY = 1f))
            host.persist()
            onClose()
        })

        addRow(btn("Editar HUD") {
            onClose()
            host.setEditing(true)
        })
        addRow(btn("Configuração BS") {
            val firstButton = host.settings().buttons.firstOrNull()
            if (firstButton != null) {
                // O painel BS está integrado ao editor de um controle. Abrir o modo de edição
                // primeiro evita que a janela de captura recupere o foco sobre os campos.
                host.setEditing(true)
                host.openConfig(firstButton.id)
            } else {
                host.openApp()
            }
            onClose()
        })
        addRow(btn("Ajustes avançados") {
            onClose()
            host.openApp()
        })
        addRow(btn("Fechar") { onClose() })
    }

    private fun addRow(v: View) {
        addView(
            v,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dp(4), 0, dp(4)) }
        )
    }

    private fun btn(t: String, onClick: () -> Unit) = Button(context).apply {
        text = t
        isAllCaps = false
        textSize = 13f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(gray)
            cornerRadius = dp(10).toFloat()
        }
        minHeight = 0
        minimumHeight = dp(42)
        setOnClickListener {
            Haptics.tick(this)
            onClick()
        }
    }

    /** Toque fora do menu fecha. */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            onClose()
            return true
        }
        return super.onTouchEvent(event)
    }
}
