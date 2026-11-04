package com.example.keymapper.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Toast
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.HudButton
import com.example.keymapper.data.Kind
import com.example.keymapper.data.Mode
import com.example.keymapper.data.MacroAction
import com.example.keymapper.data.displayName
import com.example.keymapper.data.keyLabel
import com.example.keymapper.domain.MappingEditorPolicy
import com.example.keymapper.domain.HudPlacementPolicy
import com.example.keymapper.util.Haptics
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * HUD translúcido. Posições são frações da área do jogo (Host.area()), convertidas
 * para o sistema de coordenadas desta janela, então o desenho coincide com o toque injetado.
 *
 * No modo edição: tocar seleciona, arrastar o corpo ou a alça move após o touch slop,
 * e um toque curto nos comandos da barra executa apenas uma ação.
 */
class HudView(ctx: Context, private val host: Host) : View(ctx) {

    interface Host {
        fun settings(): AppSettings
        fun update(s: AppSettings)
        fun persist()
        fun setEditing(on: Boolean)
        fun arm()
        fun closeAll()
        fun openConfig(id: Int)
        fun area(): RectF          // área do jogo, em pixels do monitor
        fun uiDensity(): Float     // densidade efetiva da interface
        // menu rápido
        fun openQuickMenu()
        fun quickMenuClosed()
        fun isPaused(): Boolean
        fun togglePause()
        fun toggleHud()
        fun openApp()
        fun activeAimId(): Int     // botão de mira ativo agora (-1 = nenhum)
    }

    private var editingSnapshot: AppSettings? = null
    var editing = false
        set(value) {
            if (value && !field && editingSnapshot == null) editingSnapshot = host.settings()
            if (!value && field) editingSnapshot = null
            field = value
        }
    var armed = false

    private class Grip(val id: Int, val x: Float, val y: Float)

    private var selected = NONE
    private var armedKind = Kind.STANDARD   // família do próximo controle criado com o +
    private var armedMode = Mode.HOLD       // Tap/Hold mantêm modos distintos na biblioteca
    private var dragging = false
    private var bodyTouchCandidate = NONE
    private var pressedHit: Pair<RectF, () -> Unit>? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop.toFloat()
    private var grabDx = 0f
    private var grabDy = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hits = ArrayList<Pair<RectF, () -> Unit>>()
    private val grips = ArrayList<Grip>()
    private var dp = 1f
    private var ga = 1f
    private var aL = 0f
    private var aT = 0f
    private var aW = 1f
    private var aH = 1f
    private val loc = IntArray(2)
    // O retículo de captura é desenhado nesta mesma janela HUD. Uma janela overlay separada
    // sobre o ponto de ACTION_DOWN pode elevar a opacidade combinada e disparar BLOCK_UNTRUSTED_TOUCHES.
    private var captureCursorVisible = false
    private var captureCursorScreenX = 0f
    private var captureCursorScreenY = 0f

    private companion object {
        const val NONE = -1000
        const val JOY = -10
        const val LOOK = -11
    }

    // ---------- geometria ----------

    private fun updateArea() {
        dp = host.uiDensity()
        getLocationOnScreen(loc)
        val a = host.area()
        aL = a.left - loc[0]
        aT = a.top - loc[1]
        aW = a.width().coerceAtLeast(1f)
        aH = a.height().coerceAtLeast(1f)
    }

    private fun px(f: Float) = aL + f * aW
    private fun py(f: Float) = aT + f * aH
    private fun am() = min(aW, aH)

    /** Atualiza a posição visual sem criar uma segunda janela TYPE_APPLICATION_OVERLAY. */
    fun setCaptureCursorPosition(screenX: Float, screenY: Float, visible: Boolean) {
        captureCursorScreenX = screenX
        captureCursorScreenY = screenY
        captureCursorVisible = visible
        postInvalidateOnAnimation()
    }

    fun setCaptureCursorVisible(visible: Boolean) {
        if (captureCursorVisible == visible) return
        captureCursorVisible = visible
        postInvalidateOnAnimation()
    }

    /** Raio do botão em pixels (tamanho do botão × tamanho global). */
    private fun radius(b: HudButton, s: AppSettings) = b.size * b.visualScale * s.buttonScale * am() / 2

    private fun centerOf(id: Int, s: AppSettings): Pair<Float, Float>? = when (id) {
        JOY -> px(s.joyX) to py(s.joyY)
        LOOK -> px(s.lookX) to py(s.lookY)
        else -> s.buttons.firstOrNull { it.id == id }?.let { px(it.x) to py(it.y) }
    }

    // ---------- desenho ----------

    override fun onDraw(c: Canvas) {
        hits.clear()
        grips.clear()
        updateArea()
        val s = host.settings()
        // Excluir um controle no painel de propriedades não pode deixar seleção/ações fantasma.
        if (selected >= 0 && s.buttons.none { it.id == selected }) selected = NONE
        ga = s.hudAlpha
        // cruz de mira: na mira ativa (jogo) ou como prévia do botão de mira selecionado (edição)
        val aimNow = host.activeAimId()
        if (aimNow >= 0) drawCrosshair(c, s, aimNow)
        else if (editing && selected >= 0) drawCrosshair(c, s, selected)
        if (!editing) drawCaptureCursor(c, s)
        if (!editing && !s.showHud) return
        val m = am()

        if (editing) {
            // contorno da área do jogo (letterbox / janela)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * dp
            paint.color = Color.argb(140, 0, 200, 255)
            paint.pathEffect = DashPathEffect(floatArrayOf(10 * dp, 6 * dp), 0f)
            c.drawRect(aL, aT, aL + aW, aT + aH, paint)
            paint.pathEffect = null
            drawGridLines(c, s)
        }

        drawArea(c, px(s.lookX), py(s.lookY), s.lookR * m, "CÂMERA", selected == LOOK, LOOK)
        drawArea(c, px(s.joyX), py(s.joyY), s.joyR * m, "WASD", selected == JOY, JOY)

        for (b in s.buttons) {
            val cx = px(b.x)
            val cy = py(b.y)
            val r = radius(b, s)
            val ring: Int? = if (editing && selected == b.id) Color.YELLOW else null
            if (b.kind == Kind.SWIPE) drawSwipePath(c, b)
            val label = (if (b.kind == Kind.MACRO) "M" else keyLabel(b.key)) + when {
                b.kind == Kind.AIM -> "◎"
                b.kind == Kind.SWIPE -> "↗"
                b.isCombo || b.macro.isNotEmpty() -> if (b.macro.isEmpty()) "C" else "+"
                b.kind == Kind.STANDARD && b.mode == Mode.TOGGLE -> "•"
                b.kind == Kind.STANDARD && b.mode == Mode.RAPID -> "R"
                else -> ""
            }
            ButtonRenderer.draw(c, paint, s.hudStyle, b, label, cx, cy, r, ga, ring, dp)
            if (editing) {
                drawGrip(c, b.id, cx, cy + r + 14 * dp)
                text(c, b.displayName(), cx, cy + r + 38 * dp, 12 * dp, Color.WHITE)
                if (selected == b.id) chip(c, "⚙", cx + r, cy - r) { host.openConfig(b.id) }
            }
        }

        // A barra e a biblioteca são exclusivas do modo de edição; não devem ficar desenhadas
        // por cima do jogo durante a partida normal.
        if (editing) {
            drawToolbar(c)
            drawButtonTray(c)
            drawPanel(c, width.toFloat(), height.toFloat())
        }
    }

    private fun drawCrosshair(c: Canvas, s: AppSettings, id: Int) {
        val b = s.buttons.firstOrNull { it.id == id && it.kind == Kind.AIM } ?: return
        if (b.crossSize <= 0f || b.crossOpacity <= 0f) return
        val cx = px((b.aimPosX / 1000f).coerceIn(0f, 1f))
        val cy = py((b.aimPosY / 1000f).coerceIn(0f, 1f))
        val len = (4f + b.crossSize * 3f) * dp
        val gap = len * 0.35f
        val alpha = (255 * (b.crossOpacity / 100f)).toInt().coerceIn(0, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * dp
        paint.color = Color.argb(alpha, 0, 255, 120)
        c.drawLine(cx - len, cy, cx - gap, cy, paint)
        c.drawLine(cx + gap, cy, cx + len, cy, paint)
        c.drawLine(cx, cy - len, cx, cy - gap, paint)
        c.drawLine(cx, cy + gap, cx, cy + len, paint)
        paint.style = Paint.Style.FILL
        c.drawCircle(cx, cy, 1.5f * dp, paint)
    }

    private fun drawSwipePath(c: Canvas, b: HudButton) {
        val sx = px(b.x)
        val sy = py(b.y)
        val ex = px(b.swipeEndX)
        val ey = py(b.swipeEndY)
        val dx = ex - sx
        val dy = ey - sy
        val length = hypot(dx, dy).coerceAtLeast(1f)
        val ux = dx / length
        val uy = dy / length
        val head = 10f * dp
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * dp
        paint.color = Color.argb(220, 177, 145, 255)
        c.drawLine(sx, sy, ex, ey, paint)
        c.drawLine(ex, ey, ex - ux * head - uy * head * 0.55f, ey - uy * head + ux * head * 0.55f, paint)
        c.drawLine(ex, ey, ex - ux * head + uy * head * 0.55f, ey - uy * head - ux * head * 0.55f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(210, 177, 145, 255)
        c.drawCircle(ex, ey, 4f * dp, paint)
    }

    /**
     * Retículo de captura desenhado dentro do HUD existente. Isso evita uma segunda janela
     * não confiável sobre o próprio ponto de toque virtual em Android 12+.
     */
    private fun drawCaptureCursor(c: Canvas, s: AppSettings) {
        if (!captureCursorVisible || !s.cursorVisible) return
        val cx = captureCursorScreenX - loc[0]
        val cy = captureCursorScreenY - loc[1]
        if (!cx.isFinite() || !cy.isFinite()) return
        val r = (s.cursorSize.coerceIn(8f, 64f) * dp / 2f).coerceAtMost(34f * dp)
        paint.color = s.cursorColor
        paint.strokeWidth = (2f * dp).coerceAtLeast(1.5f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        when (s.cursorType) {
            1 -> c.drawCircle(cx, cy, r * 0.72f, paint)
            2 -> {
                paint.style = Paint.Style.FILL
                c.drawCircle(cx, cy, (r * 0.22f).coerceAtLeast(2f * dp), paint)
            }
            3 -> {
                paint.style = Paint.Style.FILL
                val path = android.graphics.Path().apply {
                    moveTo(cx - r * 0.55f, cy - r * 0.9f)
                    lineTo(cx - r * 0.25f, cy + r * 0.85f)
                    lineTo(cx + r * 0.05f, cy + r * 0.28f)
                    lineTo(cx + r * 0.55f, cy + r * 0.55f)
                    lineTo(cx + r * 0.7f, cy + r * 0.28f)
                    lineTo(cx + r * 0.18f, cy + r * 0.02f)
                    close()
                }
                c.drawPath(path, paint)
            }
            else -> {
                val arm = r * 0.72f
                c.drawLine(cx - arm, cy, cx + arm, cy, paint)
                c.drawLine(cx, cy - arm, cx, cy + arm, paint)
                paint.style = Paint.Style.FILL
                c.drawCircle(cx, cy, (r * 0.16f).coerceAtLeast(2f * dp), paint)
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawGridLines(c: Canvas, s: AppSettings) {
        val g = s.gridSnap
        if (g <= 0) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(45, 255, 255, 255)
        for (i in 1 until g) {
            val x = aL + aW * i / g
            val y = aT + aH * i / g
            c.drawLine(x, aT, x, aT + aH, paint)
            c.drawLine(aL, y, aL + aW, y, paint)
        }
    }

    private fun text(c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        c.drawText(t, x, y - (paint.descent() + paint.ascent()) / 2, paint)
    }

    private fun drawArea(c: Canvas, cx: Float, cy: Float, r: Float, label: String, sel: Boolean, id: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(if (editing) 50 else 22, 0, 0, 0)
        c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * dp
        paint.color = if (editing && sel) Color.rgb(112, 224, 206) else Color.argb(105, 135, 175, 194)
        c.drawCircle(cx, cy, r, paint)
        text(c, label, cx, cy, 14 * dp, Color.argb(140, 255, 255, 255))
        if (editing) drawGrip(c, id, cx, cy - r)
    }

    /** Alça de arrastar: 6 pontinhos. Só ela move o botão. */
    private fun drawGrip(c: Canvas, id: Int, gx: Float, gy: Float) {
        paint.style = Paint.Style.FILL
        paint.color = if (selected == id) Color.rgb(112, 224, 206) else Color.rgb(16, 31, 43)
        c.drawCircle(gx, gy, 13 * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * dp
        paint.color = Color.argb(220, 255, 255, 255)
        c.drawCircle(gx, gy, 13 * dp, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (selected == id) Color.rgb(7, 28, 32) else Color.rgb(224, 244, 249)
        for (i in 0..1) for (j in 0..2) {
            c.drawCircle(gx + (i - 0.5f) * 5 * dp, gy + (j - 1) * 5 * dp, 1.5f * dp, paint)
        }
        grips.add(Grip(id, gx, gy))
    }

    private fun chip(c: Canvas, label: String, cx: Float, cy: Float, action: () -> Unit) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(14, 29, 41)
        c.drawCircle(cx, cy, 17 * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * dp
        paint.color = Color.rgb(86, 144, 164)
        c.drawCircle(cx, cy, 17 * dp, paint)
        text(c, label, cx, cy, 16 * dp, Color.rgb(220, 244, 247))
        hits.add(RectF(cx - 20 * dp, cy - 20 * dp, cx + 20 * dp, cy + 20 * dp) to action)
    }

    private fun pill(c: Canvas, label: String, cx: Float, cy: Float, on: Boolean, action: () -> Unit) {
        val r = RectF(cx - 30 * dp, cy - 15 * dp, cx + 30 * dp, cy + 15 * dp)
        paint.style = Paint.Style.FILL
        paint.color = if (on) Color.rgb(22, 91, 88) else Color.rgb(14, 29, 41)
        c.drawRoundRect(r, 15 * dp, 15 * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * dp
        paint.color = if (on) Color.rgb(112, 224, 206) else Color.rgb(69, 103, 122)
        c.drawRoundRect(r, 15 * dp, 15 * dp, paint)
        text(c, label, cx, cy, 13 * dp, Color.rgb(230, 247, 249))
        hits.add(r to action)
    }

    private fun drawToolbar(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val margin = 22f * dp
        val chipGap = 42f * dp
        val closeX = (w - margin).coerceAtLeast(22f * dp)
        val editX = (closeX - chipGap).coerceAtLeast(22f * dp)
        val cancelX = (editX - chipGap).coerceAtLeast(22f * dp)
        val addX = (cancelX - chipGap).coerceAtLeast(22f * dp)
        val topY = (30f * dp).coerceAtMost((h * 0.18f).coerceAtLeast(22f * dp))

        chip(c, "X", closeX, topY) {
            // Fechar durante a edição também desfaz a sessão; evita sair por um caminho que ignore Cancelar.
            if (editing) finishEditing(saveChanges = false)
            host.closeAll()
        }
        if (editing) {
            chip(c, "✓", editX, topY) { finishEditing(saveChanges = true) }
            chip(c, "↶", cancelX, topY) { finishEditing(saveChanges = false) }
            chip(c, "+", addX, topY) {
                armed = true
                host.arm()
                invalidate()
            }
        } else {
            chip(c, "⚙", editX, topY) { host.setEditing(true) }
        }

        if (!armed) {
            val cx = min(70f * dp, w * 0.27f)
            val cy = min(110f * dp, h * 0.32f).coerceAtLeast(64f * dp)
            val radius = min(56f * dp, min(w * 0.24f, h * 0.16f)).coerceAtLeast(30f * dp)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(225, 13, 26, 39)
            c.drawCircle(cx, cy, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * dp
            paint.color = Color.argb(170, 91, 171, 191)
            c.drawCircle(cx, cy, radius, paint)
            paint.style = Paint.Style.FILL
            val offset = radius * 0.5f
            text(c, "W", cx, cy - offset, 16 * dp, Color.WHITE)
            text(c, "A", cx - offset, cy, 16 * dp, Color.WHITE)
            text(c, "S", cx, cy + offset, 16 * dp, Color.WHITE)
            text(c, "D", cx + offset, cy, 16 * dp, Color.WHITE)
        }

        if (editing) {
            val helpY = (h - 26f * dp).coerceAtLeast(138f * dp)
            text(
                c, "EDIT MODE  ·  arraste os controles  ·  toque em ⚙ para configurar",
                w / 2f, helpY, 11 * dp, Color.rgb(190, 239, 238)
            )
        }
        if (editing && armed) {
            text(c, "Biblioteca HUD · escolha uma ferramenta e associe a entrada", w / 2f, topY + 36f * dp, 11 * dp, Color.WHITE)
            val center = w / 2f
            val pillGap = min(64f * dp, ((w / 2f - 32f * dp) / 2f).coerceAtLeast(27f * dp))
            val menuY = topY + 78f * dp
            val library = listOf(
                Triple("Tap", Kind.STANDARD, Mode.TAP),
                Triple("Hold", Kind.STANDARD, Mode.HOLD),
                Triple("Fire", Kind.FIRE, Mode.HOLD),
                Triple("Aim", Kind.AIM, Mode.HOLD),
                Triple("Swipe", Kind.SWIPE, Mode.HOLD),
                Triple("Macro", Kind.MACRO, Mode.HOLD)
            )
            library.forEachIndexed { index, item ->
                val row = index / 3
                val column = index % 3
                val offset = column - 1
                val selectedType = armedKind == item.second && armedMode == item.third
                pill(c, item.first, center + offset * pillGap, menuY + row * 36f * dp, selectedType) {
                    armedKind = item.second
                    armedMode = item.third
                    invalidate()
                }
            }
            val mouseGap = min(64f * dp, (w / 2f - 30f * dp).coerceAtLeast(30f * dp))
            chip(c, "M1", center - mouseGap, menuY + 78f * dp) { addButton(-1) }
            chip(c, "ABC", center, menuY + 78f * dp) { addButton(0) }
            chip(c, "M2", center + mouseGap, menuY + 78f * dp) { addButton(-2) }
        }
    }

    /** Preserves the same session checkpoint when OverlayManager rebuilds windows after screen changes. */
    fun setEditingSnapshot(snapshot: AppSettings?) {
        editingSnapshot = snapshot
    }

    /** Salvar mantém o estado editado; Cancelar restaura o snapshot de entrada da sessão inteira. */
    fun finishEditing(saveChanges: Boolean) {
        if (!editing) return
        armed = false
        armedKind = Kind.STANDARD
        armedMode = Mode.HOLD
        if (!saveChanges) {
            editingSnapshot?.let { host.update(it) }
            Toast.makeText(context, "Alterações da sessão descartadas.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Perfil salvo.", Toast.LENGTH_SHORT).show()
        }
        host.persist()
        Haptics.tick(this)
        host.setEditing(false)
    }

    /** Biblioteca lateral: toque cria um controle no centro da área do jogo. */
    private fun drawButtonTray(c: Canvas) {
        val x = (width - 12f * dp).coerceAtLeast(52f * dp)
        // O atalho lateral fica reservado à ação exclusiva de centralizar o joystick.
        // Criar Tecla/Tiro/Mira é feito pelo botão +, evitando duas bibliotecas concorrentes.
        val items = listOf(
            Triple("WASD", Color.rgb(95, 80, 160), { centerJoystick() })
        )
        val w = min(48f * dp, (width - 16f * dp).coerceAtLeast(36f * dp))
        val h = 38f * dp
        val gap = 6f * dp
        val totalHeight = items.size * h + (items.size - 1) * gap
        val startY = ((height - totalHeight) / 2f).coerceAtLeast(8f * dp)
        items.forEachIndexed { index, item ->
            val top = startY + index * (h + gap)
            val rect = RectF(x - w, top, x, top + h)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(245, 15, 30, 43)
            c.drawRoundRect(rect, 10f * dp, 10f * dp, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * dp
            paint.color = Color.rgb(59, 91, 108)
            c.drawRoundRect(rect, 10f * dp, 10f * dp, paint)
            paint.style = Paint.Style.FILL
            paint.color = item.second
            c.drawRoundRect(RectF(rect.left, rect.top, rect.left + 4f * dp, rect.bottom), 3f * dp, 3f * dp, paint)
            text(c, item.first, rect.centerX() + 1f * dp, rect.centerY(), 10f * dp, Color.WHITE)
            hits.add(rect to item.third)
        }
    }

    private fun centerJoystick() {
        mutate { it.copy(joyX = 0.5f, joyY = 0.5f) }
        selected = JOY
        host.persist()
        Haptics.tick(this)
    }

    private fun drawPanel(c: Canvas, w: Float, h: Float) {
        if (selected == NONE) return
        val items = ArrayList<Pair<String, () -> Unit>>()
        if (selected >= 0) {
            items.add("Configurar" to { host.openConfig(selected) })
            items.add("Duplicar" to { duplicateSelected() })
            items.add("Apagar" to { delete() })
        } else {
            items.add("Raio -" to { sizeDelta(-0.01f) })
            items.add("Raio +" to { sizeDelta(0.01f) })
        }
        val gap = 8f * dp
        val bh = min(40f * dp, (h - 16f * dp).coerceAtLeast(28f * dp))
        val bw = if (items.isEmpty()) 0f else min(100f * dp, (w - 24f * dp - (items.size - 1) * gap) / items.size).coerceAtLeast(54f * dp)
        val total = items.size * bw + (items.size - 1) * gap
        var x = ((w - total) / 2f).coerceIn(8f * dp, (w - total - 8f * dp).coerceAtLeast(8f * dp))
        val y = (h - bh - 12f * dp).coerceIn(8f * dp, (h - bh - 8f * dp).coerceAtLeast(8f * dp))
        for ((label, act) in items) {
            val r = RectF(x, y, x + bw, y + bh)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(242, 15, 31, 44)
            c.drawRoundRect(r, 12 * dp, 12 * dp, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * dp
            paint.color = Color.rgb(76, 127, 146)
            c.drawRoundRect(r, 12 * dp, 12 * dp, paint)
            paint.style = Paint.Style.FILL
            text(c, label, r.centerX(), r.centerY(), 14 * dp, Color.rgb(226, 245, 248))
            hits.add(r to act)
            x += bw + gap
        }
    }

    // ---------- edição ----------

    private fun mutate(f: (AppSettings) -> AppSettings) {
        host.update(f(host.settings()))
        invalidate()
    }

    fun addButton(key: Int) {
        if (!editing) return
        val current = host.settings()
        if (current.buttons.size >= 128) {
            Toast.makeText(context, "Limite de 128 controles por perfil atingido.", Toast.LENGTH_SHORT).show()
            armed = false
            invalidate()
            return
        }
        val kind = armedKind
        val id = MappingEditorPolicy.nextAvailableId(current.buttons.map { it.id }, current.buttons.size)
        if (id == null) {
            Toast.makeText(context, "Não foi possível reservar um ID para o novo controle.", Toast.LENGTH_SHORT).show()
            armed = false
            invalidate()
            return
        }
        val name = when {
            kind == Kind.FIRE -> "Fire"
            kind == Kind.AIM -> "Aim / Sight"
            kind == Kind.SWIPE -> "Swipe"
            kind == Kind.MACRO -> "Macro"
            armedMode == Mode.TAP -> "Tap"
            armedMode == Mode.RAPID -> "Rapid Tap"
            else -> "Hold"
        }
        val bindings = current.buttons.map { MappingEditorPolicy.KeyBinding(it.id, it.displayName(), it.key) }
        val requestedKey = if (kind == Kind.FIRE && key == 0) {
            MappingEditorPolicy.defaultFireBinding(current.editKey, current.pauseKey, bindings)
        } else key
        val conflict = MappingEditorPolicy.findConflict(
            candidateCode = requestedKey,
            targetControlId = id,
            bindings = bindings
        )
        val assignedKey = if (conflict == null || requestedKey == 0) requestedKey else {
            Toast.makeText(
                context,
                "${com.example.keymapper.data.keyLabel(key)} já está atribuída a '${conflict.label}'. O novo controle ficará sem tecla até você escolher outra.",
                Toast.LENGTH_LONG
            ).show()
            0
        }
        updateArea()
        // A política nova melhora somente controles padrão de uso geral. Tipos especializados
        // permanecem no comportamento legado e não recebem automação ou assistência adicional.
        // Todos os tipos usam a mesma política de colisão; Tiro/Mira não devem nascer empilhados.
        val placement = findFreeButtonPosition(
            settings = current,
            candidateRadiusPx = 0.07f * current.buttonScale * am() / 2f
        )
        mutate {
            it.copy(buttons = it.buttons + HudButton(
                id = id,
                key = assignedKey,
                x = placement.x,
                y = placement.y,
                kind = kind,
                mode = if (kind == Kind.FIRE) Mode.HOLD else armedMode,
                name = name,
                swipeEndX = (placement.x + 0.18f).coerceIn(0.02f, 0.98f),
                swipeEndY = placement.y
            ))
        }
        selected = id
        armed = false
        armedKind = Kind.STANDARD
        armedMode = Mode.HOLD
        host.persist()
        Haptics.tick(this)
        host.openConfig(id)
    }

    private fun sizeDelta(d: Float) {
        mutate { s ->
            when (selected) {
                JOY -> s.copy(joyR = (s.joyR + d).coerceIn(0.03f, 0.3f))
                LOOK -> s.copy(lookR = (s.lookR + d * 2).coerceIn(0.05f, 0.9f))
                else -> s.copy(buttons = s.buttons.map {
                    if (it.id == selected) it.copy(size = (it.size + d).coerceIn(0.03f, 0.3f)) else it
                })
            }
        }
        host.persist()
        Haptics.tick(this)
    }


    private fun duplicateSelected() {
        val s = host.settings()
        val original = s.buttons.firstOrNull { it.id == selected } ?: return
        if (s.buttons.size >= 128) {
            Toast.makeText(context, "Limite de 128 controles por perfil atingido.", Toast.LENGTH_SHORT).show()
            return
        }
        val id = MappingEditorPolicy.nextAvailableId(s.buttons.map { it.id }, s.buttons.size) ?: run {
            Toast.makeText(context, "Não foi possível reservar um ID para o controle duplicado.", Toast.LENGTH_SHORT).show()
            return
        }
        updateArea()
        val r = radius(original, s)
        val legacyPreferred = HudPlacementPolicy.Point(
            MappingEditorPolicy.boundedCenter(original.x + 0.045f, r / aW.coerceAtLeast(1f)),
            MappingEditorPolicy.boundedCenter(original.y + 0.045f, r / aH.coerceAtLeast(1f))
        )
        // Duplicatas de Tecla/Tiro/Mira recebem a mesma prevenção de colisão.
        val placement = findFreeButtonPosition(
            settings = s,
            candidateRadiusPx = r,
            excludedControlId = original.id,
            preferred = legacyPreferred
        )
        val duplicate = original.copy(
            id = id,
            key = 0, // duplicar não deve criar conflito nem disparar uma ação sem confirmação
            x = placement.x,
            y = placement.y,
            name = (original.displayName() + " cópia").take(64),
            shootKey = if (original.kind == Kind.AIM) 0 else original.shootKey,
            linkAimId = if (original.kind == Kind.FIRE) -1 else original.linkAimId
        )
        mutate { it.copy(buttons = it.buttons + duplicate) }
        selected = id
        armed = false
        host.persist()
        Haptics.tick(this)
        invalidate()
    }

    /**
     * Selects a safe, visible position for a new or duplicated control. It considers existing
     * HUD controls and the movement joystick, but does not treat the free-look viewport as an
     * occupied button because ordinary controls are allowed to live inside that region.
     */
    private fun findFreeButtonPosition(
        settings: AppSettings,
        candidateRadiusPx: Float,
        excludedControlId: Int? = null,
        preferred: HudPlacementPolicy.Point = HudPlacementPolicy.Point(0.5f, 0.5f)
    ): HudPlacementPolicy.Point {
        val occupied = ArrayList<HudPlacementPolicy.OccupiedCircle>(settings.buttons.size + 1)
        settings.buttons.asSequence()
            .filter { it.id != excludedControlId }
            .forEach { button ->
                occupied += HudPlacementPolicy.OccupiedCircle(
                    x = button.x,
                    y = button.y,
                    radiusPx = radius(button, settings)
                )
            }
        occupied += HudPlacementPolicy.OccupiedCircle(
            x = settings.joyX,
            y = settings.joyY,
            radiusPx = settings.joyR * am()
        )
        return HudPlacementPolicy.findPosition(
            widthPx = aW,
            heightPx = aH,
            candidateRadiusPx = candidateRadiusPx,
            occupied = occupied,
            preferred = preferred
        )
    }

    private fun delete() {
        val id = selected
        mutate { s ->
            s.copy(buttons = s.buttons.filter { it.id != id }
                .map { it.copy(
                    macro = it.macro.filter { m -> m != id },
                    sequence = it.sequence.filter { step -> step.action == MacroAction.WAIT || step.targetId != id },
                    linkAimId = if (it.linkAimId == id) -1 else it.linkAimId
                ) })
        }
        selected = NONE
        armed = false
        host.persist()
        Haptics.tick(this)
    }

    private fun pick(x: Float, y: Float): Int {
        val s = host.settings()
        val m = am().coerceAtLeast(1f)
        // Em áreas sobrepostas, seleciona o centro mais próximo em relação ao raio de cada controle.
        val candidates = ArrayList<Pair<Int, Float>>()
        for (b in s.buttons.asReversed()) {
            val r = radius(b, s).coerceAtLeast(1f)
            val d = hypot(x - px(b.x), y - py(b.y))
            if (d <= r) candidates.add(b.id to d / r)
        }
        val joyR = (s.joyR * m).coerceAtLeast(1f)
        val joyD = hypot(x - px(s.joyX), y - py(s.joyY))
        if (joyD <= joyR) candidates.add(JOY to joyD / joyR)
        val lookR = (s.lookR * m).coerceAtLeast(1f)
        val lookD = hypot(x - px(s.lookX), y - py(s.lookY))
        if (lookD <= lookR) candidates.add(LOOK to lookD / lookR)
        return candidates.minByOrNull { it.second }?.first ?: NONE
    }

    private fun clampCenter(value: Float, marginPixels: Float, spanPixels: Float): Float =
        MappingEditorPolicy.boundedCenter(value, marginPixels / spanPixels.coerceAtLeast(1f))

    private fun moveSelected(cx: Float, cy: Float) {
        var fx = MappingEditorPolicy.normalizedPosition((cx - aL) / aW)
        var fy = MappingEditorPolicy.normalizedPosition((cy - aT) / aH)
        val s = host.settings()
        val g = s.gridSnap
        if (g > 0) { // encaixa na grade
            fx = (fx * g).roundToInt() / g.toFloat()
            fy = (fy * g).roundToInt() / g.toFloat()
        }
        val margin = when (selected) {
            JOY -> s.joyR * am()
            LOOK -> s.lookR * am()
            else -> s.buttons.firstOrNull { it.id == selected }?.let { radius(it, s) } ?: 0f
        }
        fx = clampCenter(fx, margin, aW)
        fy = clampCenter(fy, margin, aH)
        mutate { current ->
            when (selected) {
                JOY -> current.copy(joyX = fx, joyY = fy)
                LOOK -> current.copy(lookX = fx, lookY = fy)
                else -> current.copy(buttons = current.buttons.map {
                    if (it.id == selected) it.copy(x = fx, y = fy) else it
                })
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editing) {
            pressedHit = null
            bodyTouchCandidate = NONE
            dragging = false
            return false
        }
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateArea()
                touchDownX = x
                touchDownY = y
                pressedHit = null
                // Desenhado por último = por cima; só dispara a ação se o toque terminar como clique.
                val hit = hits.asReversed().firstOrNull { it.first.contains(x, y) }
                if (hit != null) {
                    pressedHit = RectF(hit.first) to hit.second
                    bodyTouchCandidate = NONE
                    dragging = false
                    return true
                }
                // A alça permite mover com precisão; arrastar o corpo também funciona depois do slop.
                val grip = grips.asReversed().firstOrNull { hypot(x - it.x, y - it.y) <= 20 * dp }
                if (grip != null) {
                    selected = grip.id
                    bodyTouchCandidate = NONE
                    dragging = true
                    val center = centerOf(grip.id, host.settings())
                    grabDx = (center?.first ?: x) - x
                    grabDy = (center?.second ?: y) - y
                    Haptics.tick(this)
                    invalidate()
                    return true
                }
                selected = pick(x, y)
                bodyTouchCandidate = selected
                val center = centerOf(selected, host.settings())
                grabDx = (center?.first ?: x) - x
                grabDy = (center?.second ?: y) - y
                dragging = false
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressedHit != null) {
                    // Gestos iniciados em ações da barra nunca viram arraste de um controle.
                    if (!MappingEditorPolicy.isTap(touchDownX, touchDownY, x, y, touchSlop)) pressedHit = null
                    return true
                }
                if (!dragging && bodyTouchCandidate != NONE &&
                    !MappingEditorPolicy.isTap(touchDownX, touchDownY, x, y, touchSlop)) {
                    selected = bodyTouchCandidate
                    dragging = true
                    Haptics.tick(this)
                    invalidate()
                }
                if (dragging) moveSelected(x + grabDx, y + grabDy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val pressed = pressedHit
                pressedHit = null
                if (pressed != null) {
                    if (MappingEditorPolicy.isTap(touchDownX, touchDownY, x, y, touchSlop) && pressed.first.contains(x, y)) {
                        pressed.second.invoke()
                        invalidate()
                    }
                    return true
                }
                if (dragging) {
                    dragging = false
                    host.persist()
                    Haptics.tick(this)
                }
                bodyTouchCandidate = NONE
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedHit = null
                bodyTouchCandidate = NONE
                if (dragging) {
                    dragging = false
                    host.persist()
                }
                return true
            }
        }
        return true
    }
}