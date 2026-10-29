package com.example.keymapper.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as WLP
import com.example.keymapper.display.DisplayHelper
import com.example.keymapper.display.DisplayUtils
import com.example.keymapper.input.CaptureView
import com.example.keymapper.input.InputState
import com.example.keymapper.input.InputStateMachine
import com.example.keymapper.diagnostics.InputDiagnostics

/**
 * Janelas SYSTEM_ALERT_WINDOW (TYPE_APPLICATION_OVERLAY) no monitor alvo (celular ou DeX).
 *
 * Anti tela preta: NENHUMA janela em tela cheia rouba o foco do jogo.
 *  - HUD (botões): tela cheia, TRANSPARENTE, NÃO focável e (no jogo) NÃO tocável: os toques passam para o jogo.
 *  - Captura (mouse/teclado): janela de 1x1 pixel, focável, não tocável. Só ela recebe foco.
 *  - Bolha, menu rápido e painel do botão: janelas pequenas, não focáveis (o painel só é focável
 *    enquanto está aberto, para digitar).
 * Sem FLAG_SECURE; PixelFormat.TRANSLUCENT em todas; nenhuma SurfaceView/TextureView.
 */
class OverlayManager(
    private val ctx: Context,
    private val displays: DisplayHelper,
    private val host: HudView.Host,
    private val refreshHz: () -> Int,
    private val onDelta: (Float, Float) -> Unit,
    private val onButton: (Int, Boolean) -> Unit,
    private val onKey: (Int, Boolean) -> Unit
) {
    private var wm: WindowManager? = null
    private var wctx: Context? = null
    private var bubble: BubbleView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panel: ConfigPanelView? = null
    private var quick: QuickMenuView? = null
    private var virtualCursor: VirtualCursorView? = null
    private var cursorLp: WindowManager.LayoutParams? = null
    private var cursorX = 0f
    private var cursorY = 0f
    private var cursorWindowSize = 0
    private var captureEnabled = false
    private var cursorBoundsWidth = 0
    private var cursorBoundsHeight = 0

    var capture: CaptureView? = null
        private set
    var hud: HudView? = null
        private set

    @Volatile
    var display: Display? = null
        private set

    val quickMenuOpen: Boolean get() = quick != null

    /** Modo compatível (padrão): sem aceleração por GPU nas janelas de overlay. */
    private fun hw(): Int = if (host.settings().safeOverlay) 0 else WLP.FLAG_HARDWARE_ACCELERATED

    private fun base(): Int =
        WLP.FLAG_LAYOUT_IN_SCREEN or WLP.FLAG_LAYOUT_NO_LIMITS or WLP.FLAG_NOT_TOUCH_MODAL or hw()

    /** HUD: nunca focável. No jogo também NÃO tocável (os toques atravessam); na edição recebe toques. */
    private fun hudFlags(edit: Boolean): Int =
        base() or WLP.FLAG_NOT_FOCUSABLE or (if (edit) 0 else WLP.FLAG_NOT_TOUCHABLE)

    /** Captura de jogo: focável apenas quando o painel/editor não está em uso. */
    private fun captureFlags(focusable: Boolean = true): Int =
        base() or WLP.FLAG_NOT_TOUCHABLE or (if (focusable) 0 else WLP.FLAG_NOT_FOCUSABLE)

    /** Tira a janela de captura da disputa de foco enquanto a interface precisa de mouse/teclado normais. */
    private fun setCaptureFocusable(focusable: Boolean) {
        val c = capture ?: return
        val manager = wm ?: return
        try {
            val lp = c.layoutParams as? WindowManager.LayoutParams ?: return
            lp.flags = captureFlags(focusable)
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            manager.updateViewLayout(c, lp)
            if (focusable && panel == null && quick == null && hud?.editing != true) {
                c.post { if (capture === c && captureEnabled) { c.requestFocus(); c.start() } }
            }
        } catch (_: Exception) { }
    }

    fun show() {
        hide()
        if (!android.provider.Settings.canDrawOverlays(ctx)) return
        try {
            val d = displays.target()
            display = d
            val wc = displays.windowContext(d)
            wctx = wc
            val manager = wc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm = manager
            val s = host.settings()
            // só fixa a taxa de atualização quando o usuário escolheu uma (fixar sempre pode causar troca de modo da tela)
            val hz = refreshHz()
            val mode = if (hz > 0) displays.pickMode(d, hz) else null

            fun params(w: Int, h: Int, flags: Int): WindowManager.LayoutParams {
                val lp = WindowManager.LayoutParams(w, h, WLP.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT)
                if (mode != null) {
                    lp.preferredDisplayModeId = mode.modeId
                    lp.preferredRefreshRate = mode.refreshRate
                }
                return lp
            }

            val dens = host.uiDensity()
            val info = DisplayUtils.info(wc, d)
            cursorBoundsWidth = info.width
            cursorBoundsHeight = info.height

            // HUD transparente (alpha 0.8 = limite para os toques atravessarem a janela no Android 12+)
            if (s.hudWindow) {
                val h = HudView(wc, host)
                val hp = params(WLP.MATCH_PARENT, WLP.MATCH_PARENT, hudFlags(false))
                hp.alpha = 0.8f
                manager.addView(h, hp)
                hud = h
            }

            // captura: janelinha de 1x1 pixel (não cobre o jogo)
            if (s.captureWindow) {
                val c = CaptureView(wc, { dx, dy -> moveVirtualCursor(dx, dy); onDelta(dx, dy) }, onButton, onKey)
                val cp = params(1, 1, captureFlags())
                cp.gravity = Gravity.TOP or Gravity.START
                manager.addView(c, cp)
                capture = c
                c.start()
                captureEnabled = true
            }

            // Cursor visual opcional. Janela pequena, transparente e não tocável; não bloqueia o jogo.
            if (s.captureWindow) {
                val cursorSize = (72f * dens).toInt().coerceAtLeast(40)
                val cursor = VirtualCursorView(wc, { host.settings() }, dens).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                val cp = params(cursorSize, cursorSize,
                    WLP.FLAG_NOT_FOCUSABLE or WLP.FLAG_NOT_TOUCHABLE or WLP.FLAG_NOT_TOUCH_MODAL or WLP.FLAG_LAYOUT_NO_LIMITS or hw())
                cp.gravity = Gravity.TOP or Gravity.START
                cursorX = info.width / 2f
                cursorY = info.height / 2f
                cp.x = (cursorX - cursorSize / 2f).toInt()
                cp.y = (cursorY - cursorSize / 2f).toInt()
                manager.addView(cursor, cp)
                virtualCursor = cursor
                cursorLp = cp
                cursorWindowSize = cursorSize
                updateCursorVisibility()
            }

            // bolha com a engrenagem (canto superior direito, arrastável)
            val b = BubbleView(
                wc, dens,
                onTap = { if (hud?.editing == true) host.setEditing(false) else host.openQuickMenu() },
                onDrag = { dx, dy -> moveBubble(dx, dy) }
            )
            val blp = WindowManager.LayoutParams(
                WLP.WRAP_CONTENT, WLP.WRAP_CONTENT, WLP.TYPE_APPLICATION_OVERLAY,
                WLP.FLAG_NOT_FOCUSABLE or WLP.FLAG_NOT_TOUCH_MODAL or WLP.FLAG_LAYOUT_NO_LIMITS or hw(),
                PixelFormat.TRANSLUCENT
            )
            blp.gravity = Gravity.TOP or Gravity.START
            blp.x = info.width - (70 * dens).toInt()
            blp.y = (20 * dens).toInt()
            manager.addView(b, blp)
            bubble = b
            bubbleLp = blp
        } catch (e: Exception) {
            // monitor sumiu durante a montagem: limpa e deixa o serviço tentar de novo
            hide()
        }
    }

    private fun updateCursorVisibility() {
        val cursor = virtualCursor ?: return
        val shouldShow = host.settings().cursorVisible && captureEnabled && capture != null &&
            panel == null && quick == null && hud?.editing != true
        cursor.visibility = if (shouldShow) View.VISIBLE else View.INVISIBLE
        cursor.invalidate()
    }

    private fun moveVirtualCursor(dx: Float, dy: Float) {
        val cursor = virtualCursor ?: return
        if (cursor.visibility != View.VISIBLE) return
        if (cursorBoundsWidth <= 0 || cursorBoundsHeight <= 0) return
        cursorX = (cursorX + dx).coerceIn(0f, cursorBoundsWidth.toFloat())
        cursorY = (cursorY + dy).coerceIn(0f, cursorBoundsHeight.toFloat())
        val lp = cursorLp ?: return
        lp.x = (cursorX - cursorWindowSize / 2f).toInt()
        lp.y = (cursorY - cursorWindowSize / 2f).toInt()
        try {
            wm?.updateViewLayout(cursor, lp)
            cursor.invalidate()
        } catch (_: Exception) { }
    }

    private fun moveBubble(dx: Int, dy: Int) {
        val lp = bubbleLp ?: return
        val v = bubble ?: return
        lp.x += dx
        lp.y += dy
        try {
            wm?.updateViewLayout(v, lp)
        } catch (e: Exception) {
        }
    }

    fun setEditing(on: Boolean) {
        val h = hud ?: return
        try {
            val lp = h.layoutParams as? WindowManager.LayoutParams ?: return
            lp.flags = hudFlags(on)
            wm?.updateViewLayout(h, lp)
        } catch (e: Exception) {
            return
        }
        h.editing = on
        if (!on) {
            h.armed = false
            closeConfig()
        }
        bubble?.active = on
        h.postInvalidate()
        updateCursorVisibility()
        if (on) {
            capture?.stop()
            captureEnabled = false
            setCaptureFocusable(false)
        } else {
            setCaptureFocusable(true)
            capture?.start()
            captureEnabled = true
        }
    }

    // ---------- menu rápido ----------

    fun openQuickMenu() {
        val wc = wctx ?: return
        val manager = wm ?: return
        val d = display ?: return
        closeQuickMenu(false)
        try {
            val dens = host.uiDensity()
            val info = DisplayUtils.info(wc, d)
            val q = QuickMenuView(wc, host, dens) { closeQuickMenu() }
            val w = (260 * dens).toInt()
            val lp = WindowManager.LayoutParams(
                w, WLP.WRAP_CONTENT, WLP.TYPE_APPLICATION_OVERLAY,
                WLP.FLAG_NOT_FOCUSABLE or WLP.FLAG_LAYOUT_NO_LIMITS or WLP.FLAG_NOT_TOUCH_MODAL or
                    WLP.FLAG_WATCH_OUTSIDE_TOUCH or hw(),
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            val bx = bubbleLp?.x ?: (info.width - w)
            val by = bubbleLp?.y ?: 0
            lp.x = (bx + (52 * dens).toInt() - w).coerceIn(0, (info.width - w).coerceAtLeast(0))
            lp.y = (by + (60 * dens).toInt()).coerceAtLeast(0)
            manager.addView(q, lp)
            quick = q
            updateCursorVisibility()
        } catch (e: Exception) {
            quick = null
        }
    }

    /** [notify] avisa o serviço (para religar a captura do mouse). */
    fun closeQuickMenu(notify: Boolean = true) {
        val q = quick ?: return
        try {
            wm?.removeView(q)
        } catch (e: Exception) {
        }
        quick = null
        updateCursorVisibility()
        if (notify) host.quickMenuClosed()
    }

    // ---------- painel do botão ----------

    /**
     * Painel de configuração. Em tela deitada vira um painel lateral (do lado oposto ao botão, sem
     * cobri-lo e com todas as opções visíveis); em tela em pé, um bottom sheet.
     */
    fun openConfig(id: Int) {
        val wc = wctx ?: return
        val manager = wm ?: return
        val d = display ?: return
        closeConfig()
        try {
            val dens = host.uiDensity()
            val screen = DisplayUtils.info(wc, d)
            val b = host.settings().buttons.firstOrNull { it.id == id }
            // O painel precisa de mouse/teclado normais: encerra Pointer Capture e remove
            // a janela 1x1 da cadeia de foco antes de adicionar os campos editáveis.
            capture?.stop()
            captureEnabled = false
            setCaptureFocusable(false)
            val p = ConfigPanelView(wc, host, id, dens) { closeConfig() }.apply {
                isFocusable = true
                isFocusableInTouchMode = true
            }
            val landscape = screen.width > screen.height
            val width: Int
            val height: Int
            val gravity: Int
            if (landscape) {
                width = minOf((380 * dens).toInt(), (screen.width * 0.5f).toInt())
                height = (screen.height * 0.96f).toInt()
                gravity = (if (b != null && b.x > 0.5f) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
            } else {
                width = minOf(screen.width, (560 * dens).toInt())
                height = (screen.height * 0.6f).toInt()
                gravity = Gravity.CENTER_HORIZONTAL or (if (b != null && b.y > 0.5f) Gravity.TOP else Gravity.BOTTOM)
            }
            val lp = WindowManager.LayoutParams(
                width, height, WLP.TYPE_APPLICATION_OVERLAY,
                WLP.FLAG_NOT_TOUCH_MODAL or WLP.FLAG_WATCH_OUTSIDE_TOUCH or hw(),
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = gravity
            // Não force o teclado a ficar oculto: a janela deve aceitar foco e permitir que
            // cada EditText solicite o IME ao ser tocado.
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            manager.addView(p, lp)
            panel = p
            InputStateMachine.transition(InputState.CONFIGURATION, "painel de configuração aberto", force = true)
            InputDiagnostics.setMode("CONFIGURATION", "painel de configuração aberto")
            updateCursorVisibility()
            p.post {
                p.isFocusable = true
                p.isFocusableInTouchMode = true
                p.requestFocus()
            }
        } catch (e: Exception) {
            panel = null
            if (hud?.editing != true) {
                setCaptureFocusable(true)
                capture?.start()
                captureEnabled = true
                updateCursorVisibility()
            }
        }
    }

    private fun closeConfig() {
        val p = panel ?: return
        try {
            wm?.removeView(p)
        } catch (e: Exception) {
        }
        panel = null
        InputStateMachine.transition(InputState.GAME_READY, "painel de configuração fechado", force = true)
        InputDiagnostics.setMode("GAME_READY", "painel de configuração fechado")
        updateCursorVisibility()
        // Não reativa a captura enquanto o editor de HUD estiver aberto; nesse estado
        // o mouse deve permanecer como ponteiro normal para arrastar/editar os controles.
        if (hud?.editing != true) {
            setCaptureFocusable(true)
            capture?.start()
            captureEnabled = true
            updateCursorVisibility()
        }
    }

    fun setCapture(on: Boolean) {
        if (panel != null || hud?.editing == true) {
            capture?.stop()
            captureEnabled = false
            setCaptureFocusable(false)
            updateCursorVisibility()
            return
        }
        if (on) {
            setCaptureFocusable(true)
            capture?.start()
            captureEnabled = true
        } else {
            capture?.stop()
            captureEnabled = false
            setCaptureFocusable(false)
        }
        updateCursorVisibility()
    }

    fun refresh() {
        hud?.postInvalidate()
        virtualCursor?.postInvalidate()
        updateCursorVisibility()
    }

    /** Remove tudo, uma janela por vez (se uma falhar, as outras ainda saem: sem vazamento). */
    fun hide() {
        closeConfig()
        closeQuickMenu(false)
        capture?.stop()
        captureEnabled = false
        val m = wm
        val views = listOf<View?>(hud, capture, bubble, virtualCursor)
        for (v in views) {
            if (v == null) continue
            try {
                m?.removeView(v)
            } catch (e: Exception) {
            }
        }
        hud = null
        capture = null
        bubble = null
        bubbleLp = null
        virtualCursor = null
        cursorLp = null
        cursorWindowSize = 0
        cursorBoundsWidth = 0
        cursorBoundsHeight = 0
        captureEnabled = false
        wm = null
        wctx = null
        InputStateMachine.transition(InputState.INACTIVE, "overlays removidos", force = true)
        InputDiagnostics.setMode("INACTIVE", "overlays removidos")
    }
}
