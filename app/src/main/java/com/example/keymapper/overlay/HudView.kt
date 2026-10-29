package com.example.keymapper.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.View
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.HudButton
import com.example.keymapper.data.Kind
import com.example.keymapper.data.Mode
import com.example.keymapper.data.displayName
import com.example.keymapper.data.keyLabel
import com.example.keymapper.util.Haptics
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * HUD translúcido. Posições são frações da área do jogo (Host.area()), convertidas
 * para o sistema de coordenadas desta janela, então o desenho coincide com o toque injetado.
 *
 * No modo edição: tocar no CORPO do botão só seleciona; para MOVER é preciso arrastar a
 * ALÇA (grip) do botão. Isso evita deslocar botões por acidente.
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

    var editing = false
    var armed = false

    private class Grip(val id: Int, val x: Float, val y: Float)

    private var selected = NONE
    private var armedKind = Kind.STANDARD   // tipo do próximo botão criado com o +
    private var dragging = false
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

    /** Raio do botão em pixels (tamanho do botão × tamanho global). */
    private fun radius(b: HudButton, s: AppSettings) = b.size * s.buttonScale * am() / 2

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
        ga = s.hudAlpha
        // cruz de mira: na mira ativa (jogo) ou como prévia do botão de mira selecionado (edição)
        val aimNow = host.activeAimId()
        if (aimNow >= 0) drawCrosshair(c, s, aimNow)
        else if (editing && selected >= 0) drawCrosshair(c, s, selected)
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

        drawArea(c, px(s.lookX), py(s.lookY), s.lookR * m, "OLHAR", selected == LOOK, LOOK)
        drawArea(c, px(s.joyX), py(s.joyY), s.joyR * m, "WASD", selected == JOY, JOY)

        for (b in s.buttons) {
            val cx = px(b.x)
            val cy = py(b.y)
            val r = radius(b, s)
            val ring: Int? = if (editing && selected == b.id) Color.YELLOW else null
            val label = keyLabel(b.key) + when {
                b.kind == Kind.AIM -> "◎"
                b.macro.isNotEmpty() -> "+"
                b.kind == Kind.STANDARD && b.mode == Mode.TOGGLE -> "•"
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
        paint.color = if (editing && sel) Color.YELLOW else Color.argb(90, 255, 255, 255)
        c.drawCircle(cx, cy, r, paint)
        text(c, label, cx, cy, 14 * dp, Color.argb(140, 255, 255, 255))
        if (editing) drawGrip(c, id, cx, cy - r)
    }

    /** Alça de arrastar: 6 pontinhos. Só ela move o botão. */
    private fun drawGrip(c: Canvas, id: Int, gx: Float, gy: Float) {
        paint.style = Paint.Style.FILL
        paint.color = if (selected == id) Color.YELLOW else Color.argb(230, 0, 0, 0)
        c.drawCircle(gx, gy, 13 * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * dp
        paint.color = Color.argb(220, 255, 255, 255)
        c.drawCircle(gx, gy, 13 * dp, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (selected == id) Color.BLACK else Color.WHITE
        for (i in 0..1) for (j in 0..2) {
            c.drawCircle(gx + (i - 0.5f) * 5 * dp, gy + (j - 1) * 5 * dp, 1.5f * dp, paint)
        }
        grips.add(Grip(id, gx, gy))
    }

    private fun chip(c: Canvas, label: String, cx: Float, cy: Float, action: () -> Unit) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(220, 0, 0, 0)
        c.drawCircle(cx, cy, 17 * dp, paint)
        text(c, label, cx, cy, 16 * dp, Color.WHITE)
        hits.add(RectF(cx - 20 * dp, cy - 20 * dp, cx + 20 * dp, cy + 20 * dp) to action)
    }

    private fun pill(c: Canvas, label: String, cx: Float, cy: Float, on: Boolean, action: () -> Unit) {
        val r = RectF(cx - 30 * dp, cy - 15 * dp, cx + 30 * dp, cy + 15 * dp)
        paint.style = Paint.Style.FILL
        paint.color = if (on) Color.argb(235, 0, 140, 90) else Color.argb(220, 0, 0, 0)
        c.drawRoundRect(r, 15 * dp, 15 * dp, paint)
        text(c, label, cx, cy, 13 * dp, Color.WHITE)
        hits.add(r to action)
    }

    private fun drawToolbar(c: Canvas) {
        val cx = 70 * dp
        val cy = 74 * dp
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(204, 0, 0, 0)
        c.drawCircle(cx, cy, 56 * dp, paint)
        text(c, "W", cx, cy - 28 * dp, 16 * dp, Color.WHITE)
        text(c, "A", cx - 28 * dp, cy, 16 * dp, Color.WHITE)
        text(c, "S", cx, cy + 28 * dp, 16 * dp, Color.WHITE)
        text(c, "D", cx + 28 * dp, cy, 16 * dp, Color.WHITE)
        chip(c, "X", 150 * dp, 30 * dp) { host.closeAll() }
        chip(c, "⚙", 195 * dp, 30 * dp) { host.setEditing(!editing) }
        chip(c, "+", 240 * dp, 30 * dp) {
            if (editing) {
                armed = true
                host.arm()
            }
        }
        if (editing) {
            text(
                c, "MODO EDIÇÃO  •  arraste a alça (pontinhos) para mover  •  toque no botão e na engrenagem para configurar",
                width / 2f, height - 92 * dp, 12 * dp, Color.argb(230, 255, 255, 255)
            )
        }
        if (editing && armed) {
            text(c, "Novo botão: escolha o tipo e pressione uma tecla (ou M1/M2)", 400 * dp, 30 * dp, 13 * dp, Color.WHITE)
            pill(c, "Padrão", 300 * dp, 80 * dp, armedKind == Kind.STANDARD) { armedKind = Kind.STANDARD }
            pill(c, "Tiro", 370 * dp, 80 * dp, armedKind == Kind.FIRE) { armedKind = Kind.FIRE }
            pill(c, "Mira", 440 * dp, 80 * dp, armedKind == Kind.AIM) { armedKind = Kind.AIM }
            chip(c, "M1", 150 * dp, 80 * dp) { addButton(-1) }
            chip(c, "M2", 195 * dp, 80 * dp) { addButton(-2) }
        }
    }

    /** Biblioteca lateral: toque cria um controle no centro da área do jogo. */
    private fun drawButtonTray(c: Canvas) {
        val x = width - 58f * dp
        val startY = (height * 0.5f - 104f * dp).coerceAtLeast(100f * dp)
        val items = listOf(
            Triple("TAP", Color.rgb(45, 105, 165), { addControl(Kind.STANDARD, KeyEvent.KEYCODE_SPACE) }),
            Triple("FIRE", Color.rgb(165, 55, 55), { addControl(Kind.FIRE, -1) }),
            Triple("AIM", Color.rgb(30, 135, 100), { addControl(Kind.AIM, -2) }),
            Triple("WASD", Color.rgb(95, 80, 160), { centerJoystick() })
        )
        val w = 48f * dp
        val h = 42f * dp
        val gap = 9f * dp
        items.forEachIndexed { index, item ->
            val top = startY + index * (h + gap)
            val rect = RectF(x - w, top, x, top + h)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(235, 17, 23, 33)
            c.drawRoundRect(rect, 9f * dp, 9f * dp, paint)
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

    private fun addControl(kind: Int, key: Int) {
        armedKind = kind
        addButton(key)
    }

    private fun drawPanel(c: Canvas, w: Float, h: Float) {
        if (selected == NONE) return
        val items = ArrayList<Pair<String, () -> Unit>>()
        if (selected >= 0) {
            items.add("Configurar" to { host.openConfig(selected) })
            items.add("Apagar" to { delete() })
        } else {
            items.add("Raio -" to { sizeDelta(-0.01f) })
            items.add("Raio +" to { sizeDelta(0.01f) })
        }
        val bw = 100 * dp
        val bh = 40 * dp
        val gap = 8 * dp
        val total = items.size * bw + (items.size - 1) * gap
        var x = (w - total) / 2
        val y = h - bh - 24 * dp
        for ((label, act) in items) {
            val r = RectF(x, y, x + bw, y + bh)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(225, 30, 30, 30)
            c.drawRoundRect(r, 10 * dp, 10 * dp, paint)
            text(c, label, r.centerX(), r.centerY(), 14 * dp, Color.WHITE)
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
        val kind = armedKind
        val id = (host.settings().buttons.maxOfOrNull { it.id } ?: -1) + 1
        val name = when (kind) {
            Kind.FIRE -> "Tiro"
            Kind.AIM -> "Mira"
            else -> "Padrão"
        }
        mutate { it.copy(buttons = it.buttons + HudButton(id, key, 0.5f, 0.5f, kind = kind, name = name)) }
        selected = id
        armed = false
        armedKind = Kind.STANDARD
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

    private fun delete() {
        val id = selected
        mutate { s ->
            s.copy(buttons = s.buttons.filter { it.id != id }
                .map { it.copy(macro = it.macro.filter { m -> m != id }) })
        }
        selected = NONE
        host.persist()
        Haptics.tick(this)
    }

    private fun pick(x: Float, y: Float): Int {
        val s = host.settings()
        val m = am()
        for (b in s.buttons.asReversed()) {
            if (hypot(x - px(b.x), y - py(b.y)) <= radius(b, s)) return b.id
        }
        if (hypot(x - px(s.joyX), y - py(s.joyY)) <= s.joyR * m) return JOY
        if (hypot(x - px(s.lookX), y - py(s.lookY)) <= s.lookR * m) return LOOK
        return NONE
    }

    private fun moveSelected(cx: Float, cy: Float) {
        var fx = ((cx - aL) / aW).coerceIn(0f, 1f)
        var fy = ((cy - aT) / aH).coerceIn(0f, 1f)
        val g = host.settings().gridSnap
        if (g > 0) { // encaixa na grade
            fx = (fx * g).roundToInt() / g.toFloat()
            fy = (fy * g).roundToInt() / g.toFloat()
        }
        mutate { s ->
            when (selected) {
                JOY -> s.copy(joyX = fx, joyY = fy)
                LOOK -> s.copy(lookX = fx, lookY = fy)
                else -> s.copy(buttons = s.buttons.map {
                    if (it.id == selected) it.copy(x = fx, y = fy) else it
                })
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editing) return false
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateArea()
                // Os últimos itens desenhados ficam visualmente por cima; a hit-test deve seguir a mesma ordem.
                val hit = hits.asReversed().firstOrNull { it.first.contains(x, y) }
                if (hit != null) {
                    hit.second.invoke()
                    invalidate()
                    return true
                }
                // 1) alça: único jeito de MOVER
                val g = grips.firstOrNull { hypot(x - it.x, y - it.y) <= 20 * dp }
                if (g != null) {
                    selected = g.id
                    dragging = true
                    val c = centerOf(g.id, host.settings())
                    grabDx = (c?.first ?: x) - x
                    grabDy = (c?.second ?: y) - y
                    Haptics.tick(this)
                    invalidate()
                    return true
                }
                // 2) corpo: só seleciona
                selected = pick(x, y)
                dragging = false
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (dragging) moveSelected(x + grabDx, y + grabDy)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                host.persist()
                Haptics.tick(this)
            }
        }
        return true
    }
}
