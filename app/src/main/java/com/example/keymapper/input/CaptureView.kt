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

    private val heldKeys = LinkedHashSet<Int>()

    fun start() {
        want = true
        isFocusable = true
        requestFocus()
        if (hasWindowFocus()) requestPointerCapture()
    }

    fun stop() {
        want = false
        releasePointerCapture()
        releaseHeldInputs()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && want) {
            requestFocus()
            requestPointerCapture()
        } else if (!hasWindowFocus) {
            releaseHeldInputs()
        }
    }

    private fun releaseHeldInputs() {
        val bits = intArrayOf(
            MotionEvent.BUTTON_PRIMARY, MotionEvent.BUTTON_SECONDARY, MotionEvent.BUTTON_TERTIARY
        )
        for (bit in bits) if ((lastButtons and bit) != 0) onButton(bit, false)
        lastButtons = 0
        heldKeys.toList().forEach { onKey(it, false) }
        heldKeys.clear()
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
            // Com Pointer Capture, AXIS_RELATIVE_* representa o delta real. Somar e.x/y pode
            // introduzir saltos ou movimento acumulado em alguns dispositivos/firmwares.
            var sx = e.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
            var sy = e.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
            for (i in 0 until e.historySize) {
                sx += e.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, i)
                sy += e.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, i)
            }
            // Fallback para drivers que não publicam os eixos relativos.
            if (sx == 0f && sy == 0f && (e.x != 0f || e.y != 0f)) {
                sx = e.x
                sy = e.y
                for (i in 0 until e.historySize) {
                    sx += e.getHistoricalX(i)
                    sy += e.getHistoricalY(i)
                }
            }
            if (sx != 0f || sy != 0f) onDelta(sx, sy)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!want) return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0 && heldKeys.add(keyCode)) onKey(keyCode, true)
        // A captura focável precisa consumir as teclas para que não acionem atalhos do sistema.
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!want) return super.onKeyUp(keyCode, event)
        heldKeys.remove(keyCode)
        onKey(keyCode, false)
        return true
    }
}
