import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
package com.example.keymapper.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color as AndroidColor
import android.view.View
import com.example.keymapper.data.AppSettings

/** Cursor visual leve; a janela é pequena, transparente e nunca recebe toque. */
internal class VirtualCursorView(
    context: Context,
    private val settings: () -> AppSettings,
    private val density: Float
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = settings()
        val cx = width / 2f
        val cy = height / 2f
        val r = (s.cursorSize.coerceIn(8f, 64f) * density / 2f).coerceAtMost(width / 2f - 2f)
        paint.color = s.cursorColor
        paint.strokeWidth = (2f * density).coerceAtLeast(1.5f)
        paint.style = Paint.Style.STROKE
        when (s.cursorType) {
            1 -> { // anel
                canvas.drawCircle(cx, cy, r * 0.72f, paint)
                canvas.drawCircle(cx, cy, (r * 0.72f) + paint.strokeWidth, Paint(paint).apply { color = AndroidColor.BLACK; strokeWidth = paint.strokeWidth * 2f })
            }
            2 -> { // ponto
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, (r * 0.22f).coerceAtLeast(2f), paint)
            }
            3 -> { // seta simples
                paint.style = Paint.Style.FILL
                val path = Path().apply {
                    moveTo(cx - r * 0.55f, cy - r * 0.9f)
                    lineTo(cx - r * 0.25f, cy + r * 0.85f)
                    lineTo(cx + r * 0.05f, cy + r * 0.28f)
                    lineTo(cx + r * 0.55f, cy + r * 0.55f)
                    lineTo(cx + r * 0.7f, cy + r * 0.28f)
                    lineTo(cx + r * 0.18f, cy + r * 0.02f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            else -> { // cruz
                val arm = r * 0.72f
                canvas.drawLine(cx - arm, cy, cx + arm, cy, paint)
                canvas.drawLine(cx, cy - arm, cx, cy + arm, paint)
                canvas.drawCircle(cx, cy, (r * 0.16f).coerceAtLeast(2f), paint)
            }
        }
    }
}
