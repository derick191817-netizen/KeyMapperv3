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

    /** Captura: SEM FLAG_NOT_FOCUSABLE (precisa de foco para teclado/mouse), mas só 1x1 px e não tocável. */
    private fun captureFlags(): Int = base() or WLP.FLAG_NOT_TOUCHABLE

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
                val c = CaptureView(wc, onDelta, onButton, onKey)
                val cp = params(1, 1, captureFlags())
                cp.gravity = Gravity.TOP or Gravity.START
                manager.addView(c, cp)
                capture = c
                c.start()
            }

            // bolha com a engrenagem (canto superior direito, arrastável)
            val dens = host.uiDensity()
            val info = DisplayUtils.info(wc, d)
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
        if (on) capture?.stop() else capture?.start()
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
            val p = ConfigPanelView(wc, host, id, dens) { closeConfig() }
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
                WLP.FLAG_LAYOUT_NO_LIMITS or WLP.FLAG_NOT_TOUCH_MODAL or hw(),
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = gravity
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            manager.addView(p, lp)
            p.requestFocus()
            panel = p
        } catch (e: Exception) {
            panel = null
        }
    }

    private fun closeConfig() {
        val p = panel ?: return
        try {
            wm?.removeView(p)
        } catch (e: Exception) {
        }
        panel = null
    }

    fun setCapture(on: Boolean) {
        if (on) capture?.start() else capture?.stop()
    }

    fun refresh() {
        hud?.postInvalidate()
    }

    /** Remove tudo, uma janela por vez (se uma falhar, as outras ainda saem: sem vazamento). */
    fun hide() {
        capture?.stop()
        closeConfig()
        closeQuickMenu(false)
        val m = wm
        val views = listOf<View?>(hud, capture, bubble)
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
        wm = null
        wctx = null
    }
}
