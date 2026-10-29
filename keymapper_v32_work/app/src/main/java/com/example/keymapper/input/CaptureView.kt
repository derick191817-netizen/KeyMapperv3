package com.example.keymapper.input

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/** Captura entrada física enquanto esta view possui foco e Pointer Capture. */
class CaptureView(
    context: Context,
    private val onDelta: (Float, Float) -> Unit,
    private val onButton: (Int, Boolean) -> Unit,
    private val onKey: (Int, Boolean) -> Unit
) : View(context) {

    companion object { private const val TAG = "KeyMapper" }

    @Volatile private var want = false
    private var lastButtons = 0
    private val heldKeys = LinkedHashSet<Int>()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setOnCapturedPointerListener { _, event ->
            if (!want) false else {
                handleMotion(event, captured = true)
                true
            }
        }
    }

    fun start() {
        want = true
        isFocusable = true
        isFocusableInTouchMode = true
        post {
            if (want) {
                requestFocus()
                if (hasWindowFocus() && !hasPointerCapture()) requestPointerCapture()
            }
        }
    }

    fun stop() {
        want = false
        if (hasPointerCapture()) releasePointerCapture()
        releaseHeldInputs()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && want) {
            post {
                if (want) {
                    requestFocus()
                    if (hasWindowFocus() && !hasPointerCapture()) requestPointerCapture()
                }
            }
        } else if (!hasWindowFocus) {
            if (hasPointerCapture()) releasePointerCapture()
            releaseHeldInputs()
        }
    }

    override fun onPointerCaptureChange(hasCapture: Boolean) {
        super.onPointerCaptureChange(hasCapture)
        Log.d(TAG, "Pointer Capture ${if (hasCapture) "ativado" else "desativado"}; want=$want, focus=${hasWindowFocus()}")
        if (hasCapture && !want) releasePointerCapture()
    }

    private fun releaseHeldInputs() {
        val bits = intArrayOf(MotionEvent.BUTTON_PRIMARY, MotionEvent.BUTTON_SECONDARY, MotionEvent.BUTTON_TERTIARY)
        for (bit in bits) if ((lastButtons and bit) != 0) safeButton(bit, false)
        lastButtons = 0
        heldKeys.toList().forEach { safeKey(it, false) }
        heldKeys.clear()
    }

    private fun safeButton(bit: Int, down: Boolean) {
        try { onButton(bit, down) } catch (t: Throwable) { Log.e(TAG, "Erro encaminhando botão do mouse", t) }
    }

    private fun safeKey(code: Int, down: Boolean) {
        try { onKey(code, down) } catch (t: Throwable) { Log.e(TAG, "Erro encaminhando tecla $code", t) }
    }

    private fun handleMotion(event: MotionEvent, captured: Boolean) {
        if (!want) return
        val state = event.buttonState
        val changed = state xor lastButtons
        if (changed != 0) {
            val bits = intArrayOf(MotionEvent.BUTTON_PRIMARY, MotionEvent.BUTTON_SECONDARY, MotionEvent.BUTTON_TERTIARY)
            for (bit in bits) if ((changed and bit) != 0) safeButton(bit, (state and bit) != 0)
            lastButtons = state
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            var dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
            var dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
            for (i in 0 until event.historySize) {
                dx += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, i)
                dy += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, i)
            }
            // Sem Pointer Capture, muitos drivers só fornecem coordenadas de hover; converte-as
            // em deltas para não encaminhar coordenadas absolutas como se fossem movimento relativo.
            if (!captured && dx == 0f && dy == 0f) {
                dx = event.x - fallbackX
                dy = event.y - fallbackY
            }
            if (captured && dx == 0f && dy == 0f && (event.x != 0f || event.y != 0f)) {
                dx = event.x
                dy = event.y
            }
            fallbackX = event.x
            fallbackY = event.y
            if (dx.isFinite() && dy.isFinite() && (dx != 0f || dy != 0f)) {
                Log.d(TAG, "Mouse $dx,$dy")
                try { onDelta(dx, dy) } catch (t: Throwable) { Log.e(TAG, "Erro encaminhando movimento do mouse", t) }
            }
        }
    }

    private var fallbackX = 0f
    private var fallbackY = 0f

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!want) return super.onGenericMotionEvent(event)
        // Pointer Capture já é tratado pelo listener; evita processar duas vezes o mesmo evento.
        if (hasPointerCapture()) return true
        if (event.source and android.view.InputDevice.SOURCE_MOUSE == android.view.InputDevice.SOURCE_MOUSE) {
            handleMotion(event, captured = false)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!want) return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0 && heldKeys.add(keyCode)) {
            Log.d(TAG, "Tecla $keyCode DOWN")
            safeKey(keyCode, true)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!want) return super.onKeyUp(keyCode, event)
        if (heldKeys.remove(keyCode)) {
            Log.d(TAG, "Tecla $keyCode UP")
            safeKey(keyCode, false)
        }
        return true
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        if (!want) return super.onKeyMultiple(keyCode, repeatCount, event)
        safeKey(keyCode, true)
        safeKey(keyCode, false)
        return true
    }
}
