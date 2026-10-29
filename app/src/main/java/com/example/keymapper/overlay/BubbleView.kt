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
        paint.color = if (active) Color.argb(235, 0, 140, 90) else Color.argb(180, 20, 20, 24)
        c.drawCircle(r, r, r - 2 * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * dp
        paint.color = Color.argb(200, 255, 255, 255)
        c.drawCircle(r, r, r - 2 * dp, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
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
