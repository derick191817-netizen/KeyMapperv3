import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
package com.example.keymapper.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.keymapper.data.HudButton
import com.example.keymapper.data.Kind
import com.example.keymapper.data.Mode
import kotlin.math.cos
import kotlin.math.sin

/** Desenha um botão do HUD no estilo global escolhido (0 clássico, 1 neon, 2 BlueStacks, 3 minimalista, 4 ícones). */
object ButtonRenderer {
    private val rect = RectF()
    private val path = Path()

    fun draw(
        c: Canvas, p: Paint, style: Int, b: HudButton, label: String,
        cx: Float, cy: Float, r: Float, alpha: Float, ring: Int?, dp: Float
    ) {
        val o = (b.opacity * alpha).coerceIn(0.05f, 1f)
        val ts = (r * 0.55f).coerceAtLeast(10f * dp)
        when (style) {
            1 -> neon(c, p, b, label, cx, cy, r, o, alpha, ring, dp, ts)
            2 -> blueStacks(c, p, label, cx, cy, r, o, alpha, ring, dp, ts)
            3 -> minimal(c, p, label, cx, cy, r, o, ring, dp, ts)
            4 -> icons(c, p, b, label, cx, cy, r, o, alpha, ring, dp, ts)
            else -> classic(c, p, label, cx, cy, r, o, alpha, ring, dp, ts)
        }
    }

    private fun a(v: Float, o: Float) = (v * o).toInt().coerceIn(0, 255)

    private fun label(c: Canvas, p: Paint, t: String, cx: Float, cy: Float, size: Float) {
        p.textSize = size
        p.textAlign = Paint.Align.CENTER
        c.drawText(t, cx, cy - (p.descent() + p.ascent()) / 2, p)
    }

    private fun classic(
        c: Canvas, p: Paint, t: String, cx: Float, cy: Float, r: Float,
        o: Float, alpha: Float, ring: Int?, dp: Float, ts: Float
    ) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(a(204f, o), 0, 0, 0)
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2 * dp
        p.color = ring ?: Color.argb(a(170f, alpha), 255, 255, 255)
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        label(c, p, t, cx, cy, ts)
    }

    private fun neonColor(b: HudButton): Int = when {
        b.kind == Kind.AIM -> Color.rgb(0, 255, 140)
        b.kind == Kind.FIRE -> Color.rgb(255, 70, 70)
        b.macro.isNotEmpty() -> Color.rgb(255, 140, 0)
        b.mode == Mode.TOGGLE -> Color.rgb(255, 0, 200)
        b.mode == Mode.TAP -> Color.rgb(255, 230, 0)
        else -> Color.rgb(0, 220, 255)
    }

    private fun neon(
        c: Canvas, p: Paint, b: HudButton, t: String, cx: Float, cy: Float, r: Float,
        o: Float, alpha: Float, ring: Int?, dp: Float, ts: Float
    ) {
        val col = neonColor(b)
        p.style = Paint.Style.FILL
        p.color = Color.argb(a(150f, o), 0, 0, 0)
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        for (i in 3 downTo 1) { // brilho: anéis concêntricos com alpha decrescente
            p.strokeWidth = (2 + i * 3) * dp
            p.color = Color.argb(a(60f / i, o), Color.red(col), Color.green(col), Color.blue(col))
            c.drawCircle(cx, cy, r, p)
        }
        p.strokeWidth = 2 * dp
        p.color = ring ?: Color.argb(a(255f, o), Color.red(col), Color.green(col), Color.blue(col))
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.FILL
        p.color = Color.argb(a(255f, alpha), Color.red(col), Color.green(col), Color.blue(col))
        label(c, p, t, cx, cy, ts)
    }

    private fun blueStacks(
        c: Canvas, p: Paint, t: String, cx: Float, cy: Float, r: Float,
        o: Float, alpha: Float, ring: Int?, dp: Float, ts: Float
    ) {
        rect.set(cx - r, cy - r, cx + r, cy + r)
        val rad = r * 0.28f
        p.style = Paint.Style.FILL
        p.color = Color.argb(a(150f, o), 48, 54, 70)
        c.drawRoundRect(rect, rad, rad, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * dp
        p.color = ring ?: Color.argb(a(140f, alpha), 255, 255, 255)
        c.drawRoundRect(rect, rad, rad, p)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        label(c, p, t, cx, cy, ts)
    }

    private fun minimal(
        c: Canvas, p: Paint, t: String, cx: Float, cy: Float, r: Float,
        o: Float, ring: Int?, dp: Float, ts: Float
    ) {
        if (ring != null) { // só aparece o contorno quando selecionado/destacado
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.5f * dp
            p.color = ring
            c.drawCircle(cx, cy, r, p)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3 * dp
        p.color = Color.argb(a(220f, o), 0, 0, 0)
        label(c, p, t, cx, cy, ts)
        p.style = Paint.Style.FILL
        p.color = Color.argb(a(255f, o), 255, 255, 255)
        label(c, p, t, cx, cy, ts)
    }

    private fun icons(
        c: Canvas, p: Paint, b: HudButton, t: String, cx: Float, cy: Float, r: Float,
        o: Float, alpha: Float, ring: Int?, dp: Float, ts: Float
    ) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(a(204f, o), 0, 0, 0)
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2 * dp
        p.color = ring ?: Color.argb(a(170f, alpha), 255, 255, 255)
        c.drawCircle(cx, cy, r, p)
        if (!icon(c, p, b, cx, cy, r, dp)) {
            p.style = Paint.Style.FILL
            p.color = Color.WHITE
            label(c, p, t, cx, cy, ts)
        }
    }

    /** Ícones vetoriais (tiro, mira, pular, agachar, recarregar), escolhidos pelo nome/tipo do botão. */
    private fun icon(c: Canvas, p: Paint, b: HudButton, cx: Float, cy: Float, r: Float, dp: Float): Boolean {
        val n = b.name.lowercase()
        val which = when {
            b.kind == Kind.AIM || n.contains("mira") -> 1
            b.kind == Kind.FIRE || n.contains("tiro") -> 0
            n.contains("pular") -> 2
            n.contains("agach") -> 3
            n.contains("recarreg") -> 4
            else -> -1
        }
        if (which < 0) return false
        p.style = Paint.Style.STROKE
        p.strokeWidth = (r * 0.13f).coerceAtLeast(1.5f * dp)
        p.strokeCap = Paint.Cap.ROUND
        p.color = Color.WHITE
        when (which) {
            0 -> { // tiro: mira com ponto central
                c.drawCircle(cx, cy, r * 0.3f, p)
                for (k in 0..3) {
                    val ang = k * Math.PI / 2
                    val dx = cos(ang).toFloat()
                    val dy = sin(ang).toFloat()
                    c.drawLine(cx + dx * r * 0.45f, cy + dy * r * 0.45f, cx + dx * r * 0.68f, cy + dy * r * 0.68f, p)
                }
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy, r * 0.08f, p)
            }
            1 -> { // mira (luneta)
                c.drawCircle(cx, cy, r * 0.5f, p)
                c.drawLine(cx - r * 0.72f, cy, cx - r * 0.2f, cy, p)
                c.drawLine(cx + r * 0.2f, cy, cx + r * 0.72f, cy, p)
                c.drawLine(cx, cy - r * 0.72f, cx, cy - r * 0.2f, p)
                c.drawLine(cx, cy + r * 0.2f, cx, cy + r * 0.72f, p)
            }
            2, 3 -> { // seta para cima / para baixo
                val s = if (which == 2) -1f else 1f
                c.drawLine(cx, cy - s * r * 0.42f, cx, cy + s * r * 0.42f, p)
                path.reset()
                path.moveTo(cx - r * 0.3f, cy + s * r * 0.12f)
                path.lineTo(cx, cy + s * r * 0.42f)
                path.lineTo(cx + r * 0.3f, cy + s * r * 0.12f)
                c.drawPath(path, p)
            }
            else -> { // recarregar: arco com ponta de seta
                rect.set(cx - r * 0.45f, cy - r * 0.45f, cx + r * 0.45f, cy + r * 0.45f)
                c.drawArc(rect, -40f, 280f, false, p)
                val px = cx + r * 0.45f * cos(Math.toRadians(-40.0)).toFloat()
                val py = cy + r * 0.45f * sin(Math.toRadians(-40.0)).toFloat()
                p.style = Paint.Style.FILL
                path.reset()
                path.moveTo(px - r * 0.05f, py - r * 0.3f)
                path.lineTo(px + r * 0.28f, py + r * 0.02f)
                path.lineTo(px - r * 0.12f, py + r * 0.12f)
                path.close()
                c.drawPath(path, p)
            }
        }
        p.strokeCap = Paint.Cap.BUTT
        return true
    }
}
