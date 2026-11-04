package com.example.keymapper.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/** Bolha flutuante com engrenagem: toque abre/fecha o modo de edição; arraste para mover. */
class BubbleView(
    ctx: Context,
    private val dp: Float,
    private val onTap: () -> Unit,
    private val onDrag: (Int, Int) -> Unit
) : View(ctx) {

    var active = false
        set(v) {
            field = v
            postInvalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = (52 * dp).toInt()
        setMeasuredDimension(s, s)
    }

    override fun onDraw(c: Canvas) {
        val r = width / 2f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(75, 0, 0, 0)
        c.drawCircle(r + 1.5f * dp, r + 2.5f * dp, r - 1 * dp, paint)
        paint.color = if (active) Color.rgb(20, 86, 82) else Color.rgb(13, 27, 39)
        c.drawCircle(r, r, r - 2 * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f * dp
        paint.color = if (active) Color.rgb(112, 224, 206) else Color.rgb(91, 151, 170)
        c.drawCircle(r, r, r - 2 * dp, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (active) Color.rgb(180, 255, 237) else Color.rgb(222, 241, 248)
        paint.textSize = 26 * dp
        paint.textAlign = Paint.Align.CENTER
        c.drawText("⚙", r, r - (paint.descent() + paint.ascent()) / 2, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.rawX
                lastY = e.rawY
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - lastX
                val dy = e.rawY - lastY
                if (!moved && hypot(dx, dy) > 8 * dp) moved = true
                if (moved) {
                    onDrag(dx.toInt(), dy.toInt())
                    lastX = e.rawX
                    lastY = e.rawY
                }
            }
            MotionEvent.ACTION_UP -> if (!moved) onTap()
        }
        return true
    }
}
