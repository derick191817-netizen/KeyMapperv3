package com.example.keymapper.input

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.example.keymapper.diagnostics.InputDiagnostics

/** Captura entrada física enquanto esta view possui foco e Pointer Capture. */
class CaptureView(
    context: Context,
    private val onDelta: (Float, Float, Long, Int) -> Unit,
    private val onButton: (Int, Boolean) -> Unit,
    private val onKey: (Int, Boolean) -> Unit,
    private val onCaptureStateChanged: (Boolean) -> Unit = {},
    private val onScroll: (Float, Float, Int) -> Unit = { _, _, _ -> }
) : View(context) {

    companion object {
        private const val TAG = "KeyMapper"
        private const val CAPTURE_TIMEOUT_MS = 1_500L
    }

    @Volatile private var want = false
    /** Captura teclado sem capturar o ponteiro enquanto o editor está aberto, evitando ações no jogo. */
    @Volatile private var blockKeysInEditor = false
    @Volatile private var lastRelativeFallbackUptimeMs = 0L
    private var lastPublishedCaptureState = false
    private val physicalInputState = PhysicalInputState()
    // Reusable scratch buffer: evita criar um objeto Delta por amostra a 500–1000 Hz.
    private val deltaScratch = FloatArray(2)
    private val captureTimeout = Runnable {
        if (InputStatePolicy.shouldReportCaptureTimeout(
                captureRequested = want,
                windowFocused = hasWindowFocus(),
                pointerCaptureConfirmed = hasPointerCapture(),
                relativeFallbackActive = lastRelativeFallbackUptimeMs > 0L &&
                    SystemClock.uptimeMillis() - lastRelativeFallbackUptimeMs <= CAPTURE_TIMEOUT_MS * 2L
            )) {
            val reason = "Pointer Capture não confirmado após ${CAPTURE_TIMEOUT_MS} ms; a janela pode não estar apta a capturar"
            InputStateMachine.transition(InputState.ERROR, reason)
            InputDiagnostics.setCapture(false, hasWindowFocus(), reason, requested = true)
            InputDiagnostics.setMode(InputState.ERROR.name, reason)
            publishCaptureState()
        }
    }

    private fun requestCaptureIfFocused() {
        if (!want) return
        requestFocus()
        if (hasWindowFocus() && !hasPointerCapture()) {
            requestPointerCapture()
            removeCallbacks(captureTimeout)
            postDelayed(captureTimeout, CAPTURE_TIMEOUT_MS)
        }
    }

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
        blockKeysInEditor = false
        lastRelativeFallbackUptimeMs = 0L
        want = true
        InputStateMachine.transition(InputState.GRAB_REQUESTED, "solicitação de Pointer Capture")
        InputDiagnostics.setCapture(hasPointerCapture(), hasWindowFocus(), "CaptureView.start", requested = true)
        publishCaptureState()
        isFocusable = true
        isFocusableInTouchMode = true
        post { requestCaptureIfFocused() }
    }

    fun stop() {
        want = false
        lastRelativeFallbackUptimeMs = 0L
        removeCallbacks(captureTimeout)
        InputStateMachine.transition(InputState.RELEASE_REQUESTED, "liberação solicitada")
        val wasCaptured = hasPointerCapture()
        InputDiagnostics.setCapture(wasCaptured, hasWindowFocus(), "CaptureView.stop", requested = false)
        if (wasCaptured) releasePointerCapture()
        else InputStateMachine.transition(InputState.RELEASED, "sem captura ativa para liberar")
        releaseHeldInputs()
        publishCaptureState()
    }

    /**
     * Keeps the tiny capture window focusable to consume physical keyboard events during HUD editing,
     * but leaves [want] false so no Pointer Capture or mouse-delta pipeline is started.
     */
    fun setEditorKeyBlock(enabled: Boolean) {
        blockKeysInEditor = enabled
        if (enabled) {
            stop()
            isFocusable = true
            isFocusableInTouchMode = true
            post { if (blockKeysInEditor) requestFocus() }
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        InputDiagnostics.setCapture(hasPointerCapture(), hasWindowFocus, "windowFocusChanged", requested = want)
        publishCaptureState()
        if (!hasWindowFocus && want) {
            lastRelativeFallbackUptimeMs = 0L
            removeCallbacks(captureTimeout)
            InputStateMachine.transition(InputState.SUSPENDED, "janela perdeu foco")
        } else if (want && hasPointerCapture()) {
            InputStateMachine.transition(InputState.GRABBED, "janela focada com captura confirmada")
        } else if (want) {
            InputStateMachine.transition(InputState.GRAB_REQUESTED, "janela focada, aguardando captura")
        }
        if (hasWindowFocus && blockKeysInEditor) {
            post { if (blockKeysInEditor) requestFocus() }
        } else if (hasWindowFocus && want) {
            post { requestCaptureIfFocused() }
        } else if (!hasWindowFocus) {
            if (hasPointerCapture()) releasePointerCapture()
            releaseHeldInputs()
        }
    }

    override fun onPointerCaptureChange(hasCapture: Boolean) {
        super.onPointerCaptureChange(hasCapture)
        Log.d(TAG, "Pointer Capture ${if (hasCapture) "ativado" else "desativado"}; want=$want, focus=${hasWindowFocus()}")
        InputDiagnostics.setCapture(hasCapture, hasWindowFocus(), "pointerCaptureChange", requested = want)
        if (hasCapture) {
            lastRelativeFallbackUptimeMs = 0L
            removeCallbacks(captureTimeout)
        }
        // Se a captura foi perdida, nenhum botão/tecla física pode permanecer marcado como pressionado.
        if (!hasCapture) releaseHeldInputs()
        publishCaptureState()
        if (hasCapture && want && hasWindowFocus()) {
            InputStateMachine.transition(InputState.GRABBED, "Pointer Capture concedido")
        } else if (!hasCapture) {
            val next = when {
                want && !hasWindowFocus() -> InputState.SUSPENDED
                want -> InputState.GRAB_REQUESTED
                else -> InputState.RELEASED
            }
            InputStateMachine.transition(next, "Pointer Capture liberado")
            if (want && hasWindowFocus()) {
                removeCallbacks(captureTimeout)
                postDelayed(captureTimeout, CAPTURE_TIMEOUT_MS)
            }
        }
        if (hasCapture && !want) releasePointerCapture()
    }

    private fun publishCaptureState() {
        // O callback representa apenas transições reais de captura, não a solicitação inicial.
        // Isso permite que o serviço libere contatos quando uma captura antes ativa se perde.
        val active = want && hasWindowFocus() && hasPointerCapture()
        if (active == lastPublishedCaptureState) return
        lastPublishedCaptureState = active
        try { onCaptureStateChanged(active) } catch (t: Throwable) {
            Log.w(TAG, "Falha atualizando o estado visual da captura", t)
        }
    }

    /** Limpa teclas/botões físicos retidos quando um dispositivo é removido do sistema. */
    fun releasePhysicalInputs(deviceId: Int) {
        releaseHeldInputs(deviceId)
    }

    /** Liberação global usada somente quando o próprio capturador perde o foco ou é encerrado. */
    fun releasePhysicalInputs() {
        releaseHeldInputs(null)
    }

    private fun releaseHeldInputs(deviceId: Int? = null) {
        val bits = intArrayOf(
            MotionEvent.BUTTON_PRIMARY,
            MotionEvent.BUTTON_SECONDARY,
            MotionEvent.BUTTON_TERTIARY,
            MotionEvent.BUTTON_BACK,
            MotionEvent.BUTTON_FORWARD
        )
        val released = if (deviceId == null) physicalInputState.clear() else physicalInputState.clearDevice(deviceId)
        for (bit in bits) if ((released.buttonMask and bit) != 0) {
            InputDiagnostics.recordButton()
            safeButton(bit, false)
        }
        for (keyCode in released.keyCodes) {
            InputDiagnostics.recordKey(false)
            safeKey(keyCode, false)
        }
    }

    private fun safeButton(bit: Int, down: Boolean) {
        try { onButton(bit, down) } catch (t: Throwable) { Log.e(TAG, "Erro encaminhando botão do mouse", t) }
    }

    private fun safeKey(code: Int, down: Boolean) {
        try { onKey(code, down) } catch (t: Throwable) { Log.e(TAG, "Erro encaminhando tecla $code", t) }
    }

    private fun handleMotion(event: MotionEvent, captured: Boolean) {
        if (!want) return
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            // CANCEL pertence ao deviceId do evento; não deve liberar outro mouse ainda pressionado.
            // Limpa primeiro: um CANCEL nunca deve iniciar uma nova pressão de botão.
            if (event.deviceId >= 0) releaseHeldInputs(event.deviceId) else releaseHeldInputs()
            return
        }
        val state = event.buttonState
        val changed = physicalInputState.updateButtons(event.deviceId, state)
        if (changed != 0) {
            val bits = intArrayOf(
                MotionEvent.BUTTON_PRIMARY,
                MotionEvent.BUTTON_SECONDARY,
                MotionEvent.BUTTON_TERTIARY,
                MotionEvent.BUTTON_BACK,
                MotionEvent.BUTTON_FORWARD
            )
            for (bit in bits) if ((changed and bit) != 0) {
                InputDiagnostics.recordButton()
                safeButton(bit, (state and bit) != 0)
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            if (vertical.isFinite() && horizontal.isFinite() && (vertical != 0f || horizontal != 0f)) {
                try { onScroll(horizontal, vertical, event.deviceId) } catch (t: Throwable) {
                    Log.e(TAG, "Erro encaminhando rolagem do mouse", t)
                }
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            // Não colapsa o histórico: os eixos relativos não são acumulados pelo Android.
            // Em Pointer Capture, getX/getY já representam deltas relativos (não coordenadas de tela).
            // Fora da captura, X/Y são posições absolutas; só AXIS_RELATIVE_X/Y são aceitos.
            for (i in 0 until event.historySize) {
                if (!MouseDeltaPolicy.selectInto(
                    captured = captured,
                    pointerX = event.getHistoricalX(0, i),
                    pointerY = event.getHistoricalY(0, i),
                    relativeAxisX = event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, 0, i),
                    relativeAxisY = event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, 0, i),
                    out = deltaScratch
                )) continue
                dispatchDelta(
                    deltaScratch[0], deltaScratch[1], historicalEventTimeNanos(event, i), event.deviceId, captured
                )
            }

            if (MouseDeltaPolicy.selectInto(
                captured = captured,
                pointerX = event.x,
                pointerY = event.y,
                relativeAxisX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X),
                relativeAxisY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y),
                out = deltaScratch
            )) {
                dispatchDelta(deltaScratch[0], deltaScratch[1], eventTimeNanos(event), event.deviceId, captured)
            }
        }

    }

    private fun historicalEventTimeNanos(event: MotionEvent, historyPosition: Int): Long =
        if (Build.VERSION.SDK_INT >= 34) event.getHistoricalEventTimeNanos(historyPosition)
        else event.getHistoricalEventTime(historyPosition) * 1_000_000L

    private fun eventTimeNanos(event: MotionEvent): Long =
        if (Build.VERSION.SDK_INT >= 34) event.eventTimeNanos
        else event.eventTime * 1_000_000L

    private fun dispatchDelta(
        dx: Float,
        dy: Float,
        eventTimeNanos: Long,
        deviceId: Int,
        captured: Boolean
    ) {
        if (!dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) return
        // Em movimento comum, somente os eixos explicitamente relativos são válidos.
        // X/Y de hover sem Pointer Capture são coordenadas absolutas e nunca viram deltas.
        InputDiagnostics.recordMouse(
            dx, dy, deviceId, eventTimeNanos,
            pointerCaptureConfirmed = captured && hasPointerCapture() && hasWindowFocus()
        )
        if (hasWindowFocus()) {
            if (captured && hasPointerCapture()) {
                lastRelativeFallbackUptimeMs = 0L
                InputStateMachine.transition(InputState.RELATIVE_ACTIVE, "amostra relativa recebida com Pointer Capture")
            } else if (!captured) {
                // SOURCE_MOUSE_RELATIVE/AXIS_RELATIVE_X/Y pode chegar por generic motion sem
                // Pointer Capture. Registra entrada relativa, mas não afirma captura confirmada.
                lastRelativeFallbackUptimeMs = SystemClock.uptimeMillis()
                InputStateMachine.transition(
                    InputState.RELATIVE_FALLBACK_ACTIVE,
                    "amostra relativa recebida por MotionEvent sem Pointer Capture"
                )
            }
        }
        try {
            onDelta(dx, dy, eventTimeNanos, deviceId)
        } catch (t: Throwable) {
            Log.e(TAG, "Erro encaminhando amostra relativa do mouse", t)
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!want) return super.onGenericMotionEvent(event)
        // Pointer Capture já é tratado pelo listener; evita processar duas vezes o mesmo evento.
        if (hasPointerCapture()) return true
        if (InputDeviceManager.isMouse(event)) {
            handleMotion(event, captured = false)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (blockKeysInEditor) return true
        if (!want) return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0 && physicalInputState.pressKey(event.deviceId, keyCode)) {
            InputDiagnostics.recordKey(true)
            safeKey(keyCode, true)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (blockKeysInEditor) return true
        if (!want) return super.onKeyUp(keyCode, event)
        if (physicalInputState.releaseKey(event.deviceId, keyCode)) {
            InputDiagnostics.recordKey(false)
            safeKey(keyCode, false)
        }
        return true
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        if (blockKeysInEditor) return true
        if (!want) return super.onKeyMultiple(keyCode, repeatCount, event)
        if (physicalInputState.canDispatchTransientKey(event.deviceId, keyCode)) {
            InputDiagnostics.recordKey(true)
            safeKey(keyCode, true)
            InputDiagnostics.recordKey(false)
            safeKey(keyCode, false)
        }
        return true
    }

}
