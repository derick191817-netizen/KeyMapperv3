package com.example.keymapper.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import android.util.Log
import android.view.WindowManager.LayoutParams as WLP
import com.example.keymapper.display.DisplayHelper
import com.example.keymapper.display.DisplayUtils
import com.example.keymapper.input.CaptureView
import com.example.keymapper.input.CursorReanchorPolicy
import com.example.keymapper.input.VirtualCursorController
import com.example.keymapper.input.InputState
import com.example.keymapper.input.InputStateMachine
import com.example.keymapper.diagnostics.InputDiagnostics

/**
 * Janelas SYSTEM_ALERT_WINDOW (TYPE_APPLICATION_OVERLAY) no monitor alvo (celular ou DeX).
 *
 * O HUD transparente pode atravessar toques, mas a janela de captura NÃO é transparente ao foco:
 *  - HUD (botões): tela cheia, transparente, não focável e não tocável durante a execução.
 *  - Captura (mouse/teclado): janela 1x1, focável e não tocável. Ao ativá-la, esta janela passa a
 *    ser a janela focada; isso pode retirar o foco do jogo e afetar sua execução em alguns títulos.
 *  - Pointer Capture pertence à janela solicitante. Não é captura global e não preserva o foco
 *    do jogo por garantia. Essa limitação é exposta nas configurações e no diagnóstico.
 *  - Bolha/menu rápido não focáveis; painel de edição focável somente enquanto está aberto.
 * Sem FLAG_SECURE; PixelFormat.TRANSLUCENT em todas; nenhuma SurfaceView/TextureView.
 */
class OverlayManager(
    private val ctx: Context,
    private val displays: DisplayHelper,
    private val host: HudView.Host,
    private val refreshHz: () -> Int,
    private val onDelta: (Float, Float, Long, Int) -> Unit,
    private val onButton: (Int, Boolean) -> Unit,
    private val onKey: (Int, Boolean) -> Unit,
    private val onScroll: (Float, Float, Int) -> Unit = { _, _, _ -> },
    private val onCaptureStateChanged: (Boolean) -> Unit = {},
    private val onCaptureRequestChanged: (Boolean) -> Unit = {}
) {
    private var wm: WindowManager? = null
    private var wctx: Context? = null
    private var bubble: BubbleView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panel: ConfigPanelView? = null
    private var quick: QuickMenuView? = null
    // O cursor de captura é desenhado na HudView. Não criar uma janela overlay adicional:
    // ela pode bloquear ACTION_DOWN por opacidade combinada no Android 12+.
    private var cursorX = 0f
    private var cursorY = 0f
    private var cursorAnchorX = 0f
    private var cursorAnchorY = 0f
    // Coordenada lógica cumulativa. Não é limitada às dimensões do display; a posição desenhada
    // é reancorada separadamente para que o retículo não fique preso em uma borda física.
    private val logicalCursor = VirtualCursorController()
    private val logicalCursorScratch = DoubleArray(2)
    private val cursorPositionScratch = FloatArray(2)
    private var captureEnabled = false
    private var captureWanted = false
    private var nativeMouseAvailable = false
    private var nativeKeyboardAvailable = false
    private var nativeCaptureEnabled = false
    private var nativeBackendPending = false
    /** Keeps the session rollback point when overlay windows are rebuilt after rotation/DeX resize. */
    private var editorSessionSnapshot: com.example.keymapper.data.AppSettings? = null
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
    fun canOpenConfig(): Boolean = wm != null && wctx != null && display != null

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
        } catch (e: Exception) {
            Log.w("KeyMapperOverlay", "Falha ao atualizar foco da janela de captura", e)
        }
    }

    fun show(): Boolean {
        hide()
        if (!android.provider.Settings.canDrawOverlays(ctx)) {
            InputDiagnostics.setMode("ERROR", "Permissão Exibir sobre outros apps não concedida")
            return false
        }
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
                h.setEditingSnapshot(editorSessionSnapshot)
                val hp = params(WLP.MATCH_PARENT, WLP.MATCH_PARENT, hudFlags(false))
                hp.alpha = 0.8f
                manager.addView(h, hp)
                hud = h
            }

            // O backend nativo é preferencial. A janela fica inicialmente não focável;
            // Pointer Capture só é ativado depois se o evdev não cobrir mouse e teclado.
            if (s.captureWindow || s.hudWindow) {
                val c = CaptureView(
                    wc,
                    { dx, dy, eventTimeNanos, deviceId ->
                        // Native evdev owns this class when it has a confirmed grab.
                        if (!(nativeCaptureEnabled && nativeMouseAvailable)) {
                            onPointerCaptureMouseDelta(dx, dy)
                            onDelta(dx, dy, eventTimeNanos, deviceId)
                        }
                    },
                    { button, down ->
                        if (!(nativeCaptureEnabled && nativeMouseAvailable)) onButton(button, down)
                    },
                    { keyCode, down ->
                        if (!(nativeCaptureEnabled && nativeKeyboardAvailable)) onKey(keyCode, down)
                    },
                    onCaptureStateChanged = { captured ->
                        if (captured) positionCursorAtLookAnchor(resetLogical = true)
                        updateCursorVisibility()
                        try { onCaptureStateChanged(captured) } catch (t: Throwable) {
                            Log.w("KeyMapperOverlay", "Falha notificando mudança de captura", t)
                        }
                    },
                    onScroll = { horizontal, vertical, deviceId ->
                        if (!(nativeCaptureEnabled && nativeMouseAvailable)) {
                            onScroll(horizontal, vertical, deviceId)
                        }
                    }
                )
                val cp = params(1, 1, captureFlags(focusable = false))
                cp.gravity = Gravity.TOP or Gravity.START
                manager.addView(c, cp)
                capture = c
                c.stop()
                captureWanted = s.captureWindow
                captureEnabled = false
            }

            // O retículo é desenhado pela HudView existente. Não adicionamos outra janela SAW
            // sobre o ponto de contato: Android 12+ calcula a opacidade combinada de overlays
            // não tocáveis e pode descartar o toque virtual quando esse limite é excedido.
            positionCursorAtLookAnchor(resetLogical = true)
            updateCursorVisibility()

            // bolha com a engrenagem (canto superior direito, arrastável)
            val b = BubbleView(
                wc, dens,
                onTap = { if (hud?.editing == true) hud?.finishEditing(saveChanges = true) else host.openQuickMenu() },
                onDrag = { dx, dy -> moveBubble(dx, dy) }
            )
            val blp = WindowManager.LayoutParams(
                WLP.WRAP_CONTENT, WLP.WRAP_CONTENT, WLP.TYPE_APPLICATION_OVERLAY,
                WLP.FLAG_NOT_FOCUSABLE or WLP.FLAG_NOT_TOUCH_MODAL or hw(),
                PixelFormat.TRANSLUCENT
            )
            blp.gravity = Gravity.TOP or Gravity.START
            blp.x = (info.width - (70 * dens).toInt()).coerceAtLeast(0)
            blp.y = (20 * dens).toInt().coerceAtMost((info.height - (56 * dens).toInt()).coerceAtLeast(0))
            manager.addView(b, blp)
            bubble = b
            bubbleLp = blp
            b.post {
                if (bubble !== b || bubbleLp !== blp) return@post
                val maxX = (info.width - b.width.coerceAtLeast(1)).coerceAtLeast(0)
                val maxY = (info.height - b.height.coerceAtLeast(1)).coerceAtLeast(0)
                blp.x = blp.x.coerceIn(0, maxX)
                blp.y = blp.y.coerceIn(0, maxY)
                runCatching { manager.updateViewLayout(b, blp) }
                    .onFailure { Log.w("KeyMapperOverlay", "Falha ao ajustar a bolha aos limites da tela", it) }
            }
            val hasAttachedWindow = listOf<View?>(hud, capture, bubble).any { it?.isAttachedToWindow == true }
            if (!hasAttachedWindow) {
                Log.e("KeyMapperOverlay", "Nenhuma janela de overlay foi anexada")
                hide()
                return false
            }
            applyCaptureBackendState()
            return true
        } catch (e: Exception) {
            // Monitor/janela pode desaparecer durante a montagem; registre a causa antes da limpeza.
            Log.e("KeyMapperOverlay", "Falha ao montar as janelas de overlay", e)
            hide()
            return false
        }
    }

    /** Posiciona o retículo lógico dentro da mesma janela HUD que já atravessa os toques. */
    private fun positionCursorAtLookAnchor(resetLogical: Boolean = false) {
        val targetHud = hud ?: return
        if (cursorBoundsWidth <= 0 || cursorBoundsHeight <= 0) return

        val s = host.settings()
        val area = host.area()
        val activeAim = s.buttons.firstOrNull { it.id == host.activeAimId() }
        val nx = (activeAim?.aimPosX?.div(1000f) ?: (s.bsLookPositionX / 1000f)).coerceIn(0f, 1f)
        val ny = (activeAim?.aimPosY?.div(1000f) ?: s.lookY).coerceIn(0f, 1f)
        val rawX = (area.left + nx * area.width()).coerceIn(0f, cursorBoundsWidth.toFloat())
        val rawY = (area.top + ny * area.height()).coerceIn(0f, cursorBoundsHeight.toFloat())
        val insetX = (cursorBoundsWidth * 0.02f).coerceAtMost(32f).coerceAtMost(cursorBoundsWidth / 2f)
        val insetY = (cursorBoundsHeight * 0.02f).coerceAtMost(32f).coerceAtMost(cursorBoundsHeight / 2f)
        cursorAnchorX = rawX.coerceIn(insetX, (cursorBoundsWidth - insetX).coerceAtLeast(insetX))
        cursorAnchorY = rawY.coerceIn(insetY, (cursorBoundsHeight - insetY).coerceAtLeast(insetY))
        cursorX = cursorAnchorX
        cursorY = cursorAnchorY
        if (resetLogical) logicalCursor.reset(cursorX.toDouble(), cursorY.toDouble())
        targetHud.setCaptureCursorPosition(cursorX, cursorY, true)
    }

    private fun updateCursorVisibility() {
        // Não cria/mostra uma janela independente para o cursor. O desenho é incorporado ao HUD,
        // preservando uma única camada de opacidade na coordenada onde o toque virtual começa.
        val actualCapture = (nativeCaptureEnabled && nativeMouseAvailable) || capture?.hasPointerCapture() == true
        val shouldShow = host.settings().cursorVisible && captureEnabled && actualCapture &&
            panel == null && quick == null && hud?.editing != true
        if (shouldShow && !(nativeCaptureEnabled && nativeMouseAvailable)) positionCursorAtLookAnchor()
        hud?.setCaptureCursorVisible(shouldShow)
    }

    private fun moveBubble(dx: Int, dy: Int) {
        val lp = bubbleLp ?: return
        val v = bubble ?: return
        val wc = wctx ?: return
        val d = display ?: return
        try {
            val info = DisplayUtils.info(wc, d)
            val viewWidth = v.width.takeIf { it > 0 } ?: (70 * host.uiDensity()).toInt()
            val viewHeight = v.height.takeIf { it > 0 } ?: (56 * host.uiDensity()).toInt()
            lp.x = (lp.x + dx).coerceIn(0, (info.width - viewWidth).coerceAtLeast(0))
            lp.y = (lp.y + dy).coerceIn(0, (info.height - viewHeight).coerceAtLeast(0))
            wm?.updateViewLayout(v, lp)
        } catch (e: Exception) {
            Log.w("KeyMapperOverlay", "Falha ao mover a bolha de controle", e)
        }
    }

    fun setEditing(on: Boolean) {
        val h = hud ?: return
        if (on && !h.editing) {
            if (editorSessionSnapshot == null) editorSessionSnapshot = host.settings()
            h.setEditingSnapshot(editorSessionSnapshot)
        }
        try {
            val lp = h.layoutParams as? WindowManager.LayoutParams ?: return
            lp.flags = hudFlags(on)
            wm?.updateViewLayout(h, lp)
        } catch (e: Exception) {
            Log.e("KeyMapperOverlay", "Falha ao alterar o modo de edição do HUD", e)
            return
        }
        h.editing = on
        if (!on) {
            h.armed = false
            closeConfig(restoreInput = false)
            editorSessionSnapshot = null
        }
        bubble?.active = on
        h.postInvalidate()
        updateCursorVisibility()
        if (on) {
            captureWanted = false
            onCaptureRequestChanged(false)
            capture?.stop()
            captureEnabled = false
            // O editor mantém a janela focável para consumir atalhos; não captura mouse.
            setCaptureFocusable(true)
            capture?.setEditorKeyBlock(true)
        } else {
            captureWanted = host.settings().captureWindow
            onCaptureRequestChanged(captureWanted)
            capture?.setEditorKeyBlock(false)
            applyCaptureBackendState()
        }
        updateCursorVisibility()
    }

    // ---------- menu rápido ----------

    fun openQuickMenu() {
        val wc = wctx ?: return
        val manager = wm ?: return
        val d = display ?: return
        captureWanted = false
        onCaptureRequestChanged(false)
        applyCaptureBackendState()
        closeQuickMenu(false)
        try {
            val dens = host.uiDensity()
            val info = DisplayUtils.info(wc, d)
            val margin = (8 * dens).toInt().coerceAtLeast(1)
            val width = minOf((300 * dens).toInt().coerceAtLeast(1), (info.width - 2 * margin).coerceAtLeast(1))
            val availableHeight = (info.height - 2 * margin).coerceAtLeast(1)
            val maxHeight = (availableHeight * 0.82f).toInt().coerceIn(1, availableHeight)
            val q = QuickMenuView(wc, host, dens) { closeQuickMenu() }
            val height = q.measureForWindow(width, maxHeight)
            val lp = WindowManager.LayoutParams(
                width, height, WLP.TYPE_APPLICATION_OVERLAY,
                WLP.FLAG_NOT_FOCUSABLE or WLP.FLAG_NOT_TOUCH_MODAL or
                    WLP.FLAG_WATCH_OUTSIDE_TOUCH or hw(),
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            val bubbleWidth = bubble?.width?.takeIf { it > 0 } ?: (70 * dens).toInt()
            val bubbleHeight = bubble?.height?.takeIf { it > 0 } ?: (56 * dens).toInt()
            val bx = bubbleLp?.x ?: (info.width - bubbleWidth).coerceAtLeast(0)
            val by = bubbleLp?.y ?: margin
            val minX = margin.coerceAtMost((info.width - width).coerceAtLeast(0))
            val maxX = (info.width - width - margin).coerceAtLeast(minX)
            lp.x = (bx + bubbleWidth - width).coerceIn(minX, maxX)
            val below = by + bubbleHeight + margin
            val above = by - height - margin
            val desiredY = if (below + height <= info.height - margin) below else above
            val minY = margin.coerceAtMost((info.height - height).coerceAtLeast(0))
            val maxY = (info.height - height - margin).coerceAtLeast(minY)
            lp.y = desiredY.coerceIn(minY, maxY)
            manager.addView(q, lp)
            quick = q
            updateCursorVisibility()
        } catch (e: Exception) {
            Log.e("KeyMapperOverlay", "Falha ao abrir o menu rápido", e)
            quick = null
            captureWanted = host.settings().captureWindow && hud?.editing != true
            onCaptureRequestChanged(captureWanted)
            applyCaptureBackendState()
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
        captureWanted = false
        onCaptureRequestChanged(false)
        applyCaptureBackendState()
        closeConfig(restoreInput = false)
        try {
            val dens = host.uiDensity()
            val screen = DisplayUtils.info(wc, d)
            val b = host.settings().buttons.firstOrNull { it.id == id }
            // O painel precisa de mouse/teclado normais: encerra Pointer Capture e remove
            // a janela 1x1 da cadeia de foco antes de adicionar os campos editáveis.
            capture?.setEditorKeyBlock(false)
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
                width = minOf((380 * dens).toInt(), (screen.width * 0.5f).toInt()).coerceAtLeast(1)
                height = (screen.height * 0.96f).toInt().coerceAtLeast(1)
                gravity = (if (b != null && b.x > 0.5f) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
            } else {
                width = minOf(screen.width, (560 * dens).toInt()).coerceAtLeast(1)
                height = (screen.height * 0.6f).toInt().coerceAtLeast(1)
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
            Log.e("KeyMapperOverlay", "Falha ao abrir a configuração do controle $id", e)
            Toast.makeText(wc, "Não foi possível abrir as configurações deste controle.", Toast.LENGTH_LONG).show()
            panel = null
            captureWanted = hud?.editing != true && host.settings().captureWindow
            onCaptureRequestChanged(captureWanted)
            if (hud?.editing == true) {
                captureEnabled = false
                setCaptureFocusable(true)
                capture?.setEditorKeyBlock(true)
            } else {
                capture?.setEditorKeyBlock(false)
                applyCaptureBackendState()
            }
            updateCursorVisibility()
        }
    }

    private fun closeConfig(restoreInput: Boolean = true) {
        val p = panel ?: return
        try {
            wm?.removeView(p)
        } catch (e: Exception) {
        }
        panel = null
        InputStateMachine.transition(InputState.GAME_READY, "painel de configuração fechado", force = true)
        InputDiagnostics.setMode("GAME_READY", "painel de configuração fechado")
        updateCursorVisibility()
        if (restoreInput) {
            // Não ativa Pointer Capture durante a edição: só consome teclado; o mouse fica livre para o HUD.
            captureWanted = hud?.editing != true && host.settings().captureWindow
            onCaptureRequestChanged(captureWanted)
            if (hud?.editing == true) {
                captureEnabled = false
                setCaptureFocusable(true)
                capture?.setEditorKeyBlock(true)
            } else {
                capture?.setEditorKeyBlock(false)
                applyCaptureBackendState()
            }
            updateCursorVisibility()
        }
    }

    /** Reseta o estado físico do capturador quando teclado/mouse é removido. */
    fun releasePhysicalInputs(deviceId: Int) {
        capture?.releasePhysicalInputs(deviceId)
    }

    fun releasePhysicalInputs() {
        capture?.releasePhysicalInputs()
    }

    fun setCapture(on: Boolean) {
        captureWanted = on
        onCaptureRequestChanged(on && panel == null && hud?.editing != true && quick == null)
        applyCaptureBackendState()
    }

    /** Evita tomar o foco do aplicativo de destino enquanto o UserService nativo inicializa. */
    fun setNativeCapturePending(pending: Boolean) {
        nativeBackendPending = pending
        applyCaptureBackendState()
    }

    /** Atualiza disponibilidade real do backend evdev e escolhe o fallback por classe de dispositivo. */
    fun setNativeCaptureState(mouseAvailable: Boolean, keyboardAvailable: Boolean, enabled: Boolean) {
        val wasNativeMouse = nativeMouseAvailable && nativeCaptureEnabled
        nativeMouseAvailable = mouseAvailable
        nativeKeyboardAvailable = keyboardAvailable
        nativeCaptureEnabled = enabled
        nativeBackendPending = false
        if (!wasNativeMouse && nativeMouseAvailable && nativeCaptureEnabled) {
            positionCursorAtLookAnchor(resetLogical = true)
        }
        applyCaptureBackendState()
    }

    /** Aplica evdev como caminho principal; Pointer Capture cobre apenas a classe não capturada. */
    private fun applyCaptureBackendState() {
        val c = capture ?: run {
            updateCursorVisibility()
            return
        }
        val editingNow = hud?.editing == true
        if (editingNow) {
            c.stop()
            c.setEditorKeyBlock(true)
            setCaptureFocusable(true)
            captureEnabled = false
            updateCursorVisibility()
            return
        }
        c.setEditorKeyBlock(false)
        val allowed = captureWanted && host.settings().captureWindow && panel == null && quick == null
        if (!allowed) {
            c.stop()
            setCaptureFocusable(false)
            captureEnabled = false
        } else {
            val nativeHandlesBoth = nativeCaptureEnabled && nativeMouseAvailable && nativeKeyboardAvailable
            if (nativeBackendPending) {
                c.stop()
                setCaptureFocusable(false)
                captureEnabled = false
            } else if (nativeHandlesBoth) {
                c.stop()
                setCaptureFocusable(false)
                captureEnabled = nativeMouseAvailable
            } else {
                // Se evdev capturou só mouse ou só teclado, Pointer Capture trata a classe restante.
                setCaptureFocusable(true)
                c.start()
                captureEnabled = true
            }
        }
        updateCursorVisibility()
    }

    /** Retículo atualizado também pelo fallback Pointer Capture/MotionEvent. */
    fun onPointerCaptureMouseDelta(dx: Float, dy: Float) {
        if (capture?.hasPointerCapture() != true) return
        updateCapturedCursorFromDelta(dx, dy)
    }

    /** Atualiza o retículo a partir dos deltas evdev, sem depender da janela com foco. */
    fun onNativeMouseDelta(dx: Float, dy: Float) {
        if (!nativeCaptureEnabled || !nativeMouseAvailable) return
        updateCapturedCursorFromDelta(dx, dy)
    }

    /** Um único caminho matemático mantém o cursor idêntico entre evdev e Pointer Capture. */
    private fun updateCapturedCursorFromDelta(dx: Float, dy: Float) {
        if (panel != null || quick != null || hud?.editing == true) return
        if (cursorBoundsWidth <= 1 || cursorBoundsHeight <= 1 || !dx.isFinite() || !dy.isFinite() ||
            (dx == 0f && dy == 0f)
        ) return
        if (cursorAnchorX == 0f && cursorAnchorY == 0f) positionCursorAtLookAnchor()

        // A referência lógica continua crescendo sem qualquer clamp de largura/altura. Somente
        // o retículo desenhado é reancorado dentro da área visível; o delta excedente não se perde.
        logicalCursor.advanceInto(dx, dy, logicalCursorScratch)
        if (!CursorReanchorPolicy.advanceInto(
                startX = cursorX,
                startY = cursorY,
                deltaX = dx,
                deltaY = dy,
                anchorX = cursorAnchorX,
                anchorY = cursorAnchorY,
                minX = 0f,
                maxX = cursorBoundsWidth.toFloat(),
                minY = 0f,
                maxY = cursorBoundsHeight.toFloat(),
                out = cursorPositionScratch
            )
        ) return
        cursorX = cursorPositionScratch[0]
        cursorY = cursorPositionScratch[1]
        hud?.setCaptureCursorPosition(cursorX, cursorY, true)
        hud?.setCaptureCursorVisible(host.settings().cursorVisible)
    }

    fun refresh() {
        hud?.postInvalidate()
        positionCursorAtLookAnchor()
        updateCursorVisibility()
    }

    /** Remove tudo, uma janela por vez (se uma falhar, as outras ainda saem: sem vazamento). */
    fun hide() {
        closeConfig(restoreInput = false)
        closeQuickMenu(false)
        capture?.setEditorKeyBlock(false)
        capture?.stop()
        captureEnabled = false
        captureWanted = false
        val m = wm
        val views = listOf<View?>(hud, capture, bubble)
        for (v in views) {
            if (v == null) continue
            try {
                m?.removeView(v)
            } catch (e: Exception) {
                Log.w("KeyMapperOverlay", "Falha ao remover uma janela de overlay", e)
            }
        }
        hud?.setCaptureCursorVisible(false)
        hud = null
        capture = null
        bubble = null
        bubbleLp = null
        cursorBoundsWidth = 0
        cursorBoundsHeight = 0
        cursorAnchorX = 0f
        cursorAnchorY = 0f
        cursorX = 0f
        cursorY = 0f
        logicalCursor.reset()
        captureEnabled = false
        wm = null
        wctx = null
        InputStateMachine.transition(InputState.INACTIVE, "overlays removidos", force = true)
        InputDiagnostics.setMode("INACTIVE", "overlays removidos")
    }
}
