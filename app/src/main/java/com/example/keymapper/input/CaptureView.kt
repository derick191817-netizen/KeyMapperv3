package com.example.keymapper.input

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/**
 * Pointer Capture (Android 8+): o sistema entrega o movimento relativo bruto
 * do mouse, sem aceleração e sem cursor. Também recebe as teclas.
 */
class CaptureView(
    context: Context,
    private val onDelta: (Float, Float) -> Unit,
    private val onButton: (Int, Boolean) -> Unit,
    private val onKey: (Int, Boolean) -> Unit
) : View(context) {

    private var want = false
    private var lastButtons = 0

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setOnCapturedPointerListener { _, e -> handle(e); true }
    }

    fun start() {
        want = true
        requestFocus()
        if (hasWindowFocus()) requestPointerCapture()
    }

    fun stop() {
        want = false
        releasePointerCapture()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && want) requestPointerCapture()
    }

    private fun handle(e: MotionEvent) {
        val bs = e.buttonState
        val changed = bs xor lastButtons
        if (changed != 0) {
            val bits = intArrayOf(
                MotionEvent.BUTTON_PRIMARY, MotionEvent.BUTTON_SECONDARY, MotionEvent.BUTTON_TERTIARY
            )
            for (bit in bits) {
                if ((changed and bit) != 0) onButton(bit, (bs and bit) != 0)
            }
            lastButtons = bs
        }
        if (e.actionMasked == MotionEvent.ACTION_MOVE || e.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            var sx = e.x
            var sy = e.y
            for (i in 0 until e.historySize) {
                sx += e.getHistoricalX(i)
                sy += e.getHistoricalY(i)
            }
            onDelta(sx, sy)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0) onKey(keyCode, true)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        onKey(keyCode, false)
        return true
    }
}
