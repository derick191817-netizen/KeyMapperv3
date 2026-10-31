package com.example.keymapper.mapping

import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.HudButton
import com.example.keymapper.data.Kind
import com.example.keymapper.data.Mode
import com.example.keymapper.data.Rot
import com.example.keymapper.diagnostics.InputDiagnostics
import com.example.keymapper.injection.TouchInjector
import com.example.keymapper.input.PointerSpeed
import com.example.keymapper.input.MouseInputProcessor
import com.example.keymapper.input.InputEventQueue
import com.example.keymapper.input.MouseSampleClock
import com.example.keymapper.input.MouseScrollAccumulator
import com.example.keymapper.input.VirtualCursorController
import com.example.keymapper.display.CoordinateTransformer
import com.example.keymapper.domain.SensitivityEngine
import com.example.keymapper.domain.InputBindingPolicy
import kotlin.math.hypot
import kotlin.math.min

/**
 * Converte teclas/mouse em toques (tudo roda na thread do TouchInjector).
 *
 *  - TIRO (FIRE): segurar contínuo (DOWN ao apertar, UP só ao soltar a tecla) ou toque único.
 *  - MIRA (AIM): segurar ou suspender (toggle).
 *  - CÂMERA:
 *      AUTO/CENTER : captura deltas relativos e os converte em arrastos touchscreen contínuos;
 *                    ao chegar à borda, troca o contato para o ponto de referência e preserva o excedente.
 *      INFINITE    : envia SOURCE_MOUSE_RELATIVE apenas para destinos que aceitam mouse nativo.
 *      OFF         : arrasto touchscreen limitado à borda.
 *    AUTO não infere suporte a mouse nativo pelo nome do pacote; prioriza jogos Android de toque.
 *
 * Posições são frações da ÁREA DO JOGO (RectF em pixels do monitor).
 */
class MappingEngine(
    private val touch: TouchInjector,
    private val area: () -> RectF,
    private val cfg: () -> AppSettings
) {
    private companion object {
        const val JOY = 0
        const val LOOK_A = 1
        const val LOOK_B = 30   // ids 2..29 são dos botões; 30 alterna com 1 ao trocar de dedo
        const val EDGE_MARGIN = 10f
        const val MAX_PENDING_MOUSE_DELTA = 1_000_000f
        const val MAX_WHEEL_STEPS_PER_EVENT = 16
        const val MAX_INPUT_EVENTS_PER_DRAIN = 256
        // Não reproduzir movimentos antigos após um bloqueio do consumidor; 50 ms também é
        // o limite de trecho usado pelo MouseSampleClock para reiniciar a normalização temporal.
        const val MAX_MOUSE_QUEUE_AGE_NS = InputEventQueue.STALE_MOUSE_MAX_AGE_NANOS
    }

    private val sens = SensitivityEngine()
    private val mouseProcessor = MouseInputProcessor()
    private val mouseSampleScratch = FloatArray(2)
    private val lookOutputScratch = FloatArray(2)
    // Android 13/API 33 e anteriores entregam MotionEvent.eventTime em ms. API 34+ permite
    // usar a variante em ns; não fingimos precisão de nanossegundos em dispositivos legados.
    private val sampleClock = MouseSampleClock(
        timestampResolutionNanos = if (Build.VERSION.SDK_INT >= 34) 1L else 1_000_000L
    )
    private val virtualCursor = VirtualCursorController()
    private val keys = HashSet<Int>()
    private val held = HashSet<Int>()      // todas as teclas/botões atualmente pressionados
    private val toggled = HashSet<Int>()
    // Ações atrasadas (toques, duplo toque e delay configurado) são rastreadas para
    // que a desativação/ perda de foco não execute eventos obsoletos depois de releaseAll().
    private val pendingActions = LinkedHashSet<Runnable>()

    // Uma fila primitiva preserva a ordem global entre mouse/teclas/scroll sem um Handler.post
    // por amostra. O consumidor e todos os estados do motor continuam na thread do TouchInjector.
    private val inputQueueLock = Any()
    private val inputQueue = InputEventQueue()
    private val inputEventScratch = InputEventQueue.Event()
    private var inputDrainScheduled = false
    private val drainInputEvents = Runnable { drainInputEventBatch() }
    // IDs de ponteiro Android são limitados a 0..31. IDs de botão são alocados sem colisão
    // e liberados junto com o contato; isso evita colisões após criar/apagar muitos controles.
    private val buttonPointerIds = HashMap<Int, Int>()
    private var joyActive = false
    private var lookActive = false
    private var lookId = LOOK_A
    // Canvas virtual independente da tela física. Estes valores NUNCA são usados como
    // coordenadas absolutas de touchscreen: o Android/jogo pode limitar coordenadas fora da
    // superfície. A injeção contínua usa deltas relativos via TouchInjector.
    // Posição física válida do contato de câmera.
    private var lx = 0f
    private var ly = 0f

    @Volatile
    var paused = false

    /** Chamado quando a mira ativa muda (o HUD redesenha a cruz de mira). */
    var onAimChanged: (() -> Unit)? = null

    private val releaseLook = Runnable { endLook() }

    // Câmera em lotes: os deltas do mouse (até 1000 Hz) são somados e enviados em intervalos FIXOS.
    private val lookLock = Any()
    private var pendX = 0f
    private var pendY = 0f
    private var flushScheduled = false
    private val scrollAccumulator = MouseScrollAccumulator(
        maxStepsPerAxisPerEvent = MAX_WHEEL_STEPS_PER_EVENT
    )
    private val scrollStepsScratch = IntArray(2)

    @Volatile
    private var lastFlush = 0L
    private val flushLook = Runnable { flushLookNow() }

    /** Botão de mira ativo (-1 = nenhum). Ao mudar, o arrasto da câmera recomeça da posição da mira. */
    @Volatile
    var aimId = -1
        private set(value) {
            if (field != value) {
                field = value
                endLook()
                onAimChanged?.invoke()
            }
        }

    private fun pointerId(b: HudButton): Int? {
        buttonPointerIds[b.id]?.let { return it }
        val used = buttonPointerIds.values.toHashSet()
        val candidate = (2..29).firstOrNull { it !in used } ?: if (31 !in used) 31 else null
        if (candidate != null) buttonPointerIds[b.id] = candidate
        return candidate
    }

    fun onKey(code: Int, down: Boolean) {
        if (paused) return
        enqueueKey(code, down)
    }

    private fun enqueueKey(code: Int, down: Boolean) {
        var schedule = false
        var accepted = false
        var active = false
        var droppedMotion = false
        var droppedControl = false
        synchronized(inputQueueLock) {
            active = !paused
            if (active) {
                val dropsBefore = inputQueue.droppedMouse
                val controlDropsBefore = inputQueue.droppedControl
                accepted = inputQueue.offerKey(code, down)
                droppedMotion = inputQueue.droppedMouse != dropsBefore
                droppedControl = inputQueue.droppedControl != controlDropsBefore
                if (accepted) InputDiagnostics.recordInputQueueDepth(inputQueue.size())
                if (accepted && !inputDrainScheduled) {
                    inputDrainScheduled = true
                    schedule = true
                }
            }
        }
        if (droppedMotion) InputDiagnostics.recordInputQueueDrop(mouse = true)
        if (droppedControl) InputDiagnostics.recordInputQueueDrop(mouse = false)
        else if (active && !accepted) InputDiagnostics.recordInputQueueDrop(mouse = false)
        postInputDrain(schedule)
    }

    private fun enqueueMouse(dx: Float, dy: Float, eventTimeNanos: Long, deviceId: Int) {
        var schedule = false
        var accepted = false
        var active = false
        var droppedOldSample = false
        synchronized(inputQueueLock) {
            active = !paused
            if (active) {
                val dropsBefore = inputQueue.droppedMouse
                accepted = inputQueue.offerMouse(dx, dy, eventTimeNanos, deviceId)
                droppedOldSample = inputQueue.droppedMouse != dropsBefore
                if (accepted) InputDiagnostics.recordInputQueueDepth(inputQueue.size())
                if (accepted && !inputDrainScheduled) {
                    inputDrainScheduled = true
                    schedule = true
                }
            }
        }
        if (droppedOldSample) InputDiagnostics.recordInputQueueDrop(mouse = true)
        postInputDrain(schedule)
    }

    private fun enqueueScroll(horizontal: Float, vertical: Float, deviceId: Int) {
        var schedule = false
        var accepted = false
        var active = false
        var droppedMotion = false
        var droppedScroll = false
        synchronized(inputQueueLock) {
            active = !paused
            if (active) {
                val dropsBefore = inputQueue.droppedMouse
                val scrollDropsBefore = inputQueue.droppedScroll
                accepted = inputQueue.offerScroll(horizontal, vertical, deviceId)
                droppedMotion = inputQueue.droppedMouse != dropsBefore
                droppedScroll = inputQueue.droppedScroll != scrollDropsBefore
                if (accepted) InputDiagnostics.recordInputQueueDepth(inputQueue.size())
                if (accepted && !inputDrainScheduled) {
                    inputDrainScheduled = true
                    schedule = true
                }
            }
        }
        if (droppedMotion) InputDiagnostics.recordInputQueueDrop(mouse = true)
        if (droppedScroll) InputDiagnostics.recordInputQueueScrollDrop(1L)
        else if (active && !accepted) InputDiagnostics.recordInputQueueScrollDrop(1L)
        postInputDrain(schedule)
    }

    private fun postInputDrain(schedule: Boolean) {
        if (!schedule) return
        if (!touch.handler.post(drainInputEvents)) {
            synchronized(inputQueueLock) {
                inputQueue.clear()
                inputDrainScheduled = false
            }
            InputDiagnostics.recordInputQueueDrop(mouse = false)
        }
    }

    /** Processa no máximo um lote por iteração para não monopolizar a thread de injeção. */
    private fun drainInputEventBatch() {
        val drainStartedNs = System.nanoTime()
        // Um backlog antigo de mouse produz uma câmera atrasada mesmo que o consumidor já tenha
        // voltado a executar. Remove esses deltas em uma compactação estável, sem tocar nos eventos
        // de controle e sem alterar a ordem dos movimentos ainda recentes.
        val staleMouseDrops = synchronized(inputQueueLock) {
            // Verifica também filas pequenas: um bloqueio longo pode envelhecer poucos eventos.
            // InputEventQueue faz fast-path sem compactação se a primeira amostra ainda é recente.
            inputQueue.discardStaleMouse(System.nanoTime(), MAX_MOUSE_QUEUE_AGE_NS)
        }
        if (staleMouseDrops > 0) {
            InputDiagnostics.recordStaleMouseDrops(staleMouseDrops.toLong())
        }
        var processed = 0
        while (processed < MAX_INPUT_EVENTS_PER_DRAIN) {
            val hasEvent = synchronized(inputQueueLock) {
                if (inputQueue.pollInto(inputEventScratch)) true
                else {
                    inputDrainScheduled = false
                    false
                }
            }
            if (!hasEvent) {
                if (processed > 0) InputDiagnostics.recordInputDrainBatch(
                    processed, System.nanoTime() - drainStartedNs
                )
                return
            }

            val event = inputEventScratch
            InputDiagnostics.recordInputQueueResidence(
                System.nanoTime() - event.queuedAtNanos
            )
            if (!paused || event.type == InputEventQueue.TYPE_RELEASE) {
                when (event.type) {
                    InputEventQueue.TYPE_MOUSE -> {
                        if (!touch.broken) processMouseSample(
                            event.x, event.y, event.eventTimeNanos, event.deviceId
                        )
                    }
                    InputEventQueue.TYPE_KEY -> handleKey(event.code, event.down)
                    InputEventQueue.TYPE_SCROLL -> processMouseScroll(event.x, event.y, event.deviceId)
                    InputEventQueue.TYPE_RELEASE -> releaseAllOnHandler()
                }
            }
            processed++
        }

        InputDiagnostics.recordInputDrainBatch(processed, System.nanoTime() - drainStartedNs)
        val hasMore = synchronized(inputQueueLock) {
            if (inputQueue.isEmpty()) {
                inputDrainScheduled = false
                false
            } else true
        }
        if (hasMore && !touch.handler.post(drainInputEvents)) {
            synchronized(inputQueueLock) {
                inputQueue.clear()
                inputDrainScheduled = false
            }
            InputDiagnostics.recordInputQueueDrop(mouse = false)
        }
    }

    /** Freezes new input immediately; cleanup and subsequent resume are ordered on the engine thread. */
    fun pauseAndReleaseAll() {
        paused = true
        releaseAll()
    }

    fun resumeAfterConfigurationChange() {
        touch.handler.post { paused = false }
    }

    fun onMouseButton(bit: Int, down: Boolean) {
        // Não classifica botões desconhecidos como botão do meio.
        val code = InputBindingPolicy.mouseButtonCode(bit) ?: return
        onKey(code, down)
    }

    /** A roda gera pulsos de tecla mapeáveis (Page Up/Down/Left/Right). Frações são acumuladas. */
    fun onMouseScroll(horizontal: Float, vertical: Float, deviceId: Int = -1) {
        if (paused || !horizontal.isFinite() || !vertical.isFinite() ||
            (horizontal == 0f && vertical == 0f)
        ) return
        enqueueScroll(horizontal, vertical, deviceId)
    }

    /** Descarta apenas movimento/scroll pendente da origem removida; outros periféricos seguem ativos. */
    fun onInputDeviceRemoved(deviceId: Int) {
        val removed = synchronized(inputQueueLock) {
            inputQueue.discardPointerEventsFromDevice(deviceId)
        }
        if (removed.mouse > 0) InputDiagnostics.recordInputQueueDrop(mouse = true, count = removed.mouse.toLong())
        if (removed.scroll > 0) InputDiagnostics.recordInputQueueScrollDrop(removed.scroll.toLong())
        // O relógio é propriedade da thread de injeção; serializa a limpeza depois do lote atual.
        touch.handler.post {
            sampleClock.resetDevice(deviceId)
            scrollAccumulator.resetDevice(deviceId)
        }
    }

    private fun processMouseScroll(horizontal: Float, vertical: Float, deviceId: Int) {
        if (!scrollAccumulator.consumeInto(deviceId, horizontal, vertical, scrollStepsScratch)) return
        val verticalSteps = scrollStepsScratch[0]
        val horizontalSteps = scrollStepsScratch[1]
        repeat(kotlin.math.abs(verticalSteps)) {
            dispatchWheelKey(if (verticalSteps > 0) KeyEvent.KEYCODE_PAGE_UP else KeyEvent.KEYCODE_PAGE_DOWN)
        }
        repeat(kotlin.math.abs(horizontalSteps)) {
            dispatchWheelKey(if (horizontalSteps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
        }
    }

    private fun dispatchWheelKey(code: Int) {
        handleKey(code, true)
        handleKey(code, false)
    }

    /** Compatibilidade para fontes antigas que não fornecem metadados do evento. */
    fun onDelta(dx: Float, dy: Float) = onDelta(dx, dy, SystemClock.uptimeMillis() * 1_000_000L, -1)

    /** Cada amostra é transformada na ordem temporal ANTES de qualquer agregação. */
    fun onDelta(dx: Float, dy: Float, eventTimeNanos: Long, deviceId: Int) {
        if (paused) return
        enqueueMouse(dx, dy, eventTimeNanos, deviceId)
    }

    /** Transforma cada amostra individual na ordem global da fila, antes de somar a saída. */
    private fun processMouseSample(dx: Float, dy: Float, eventTimeNanos: Long, deviceId: Int) {
        if (!mouseProcessor.processInto(dx, dy, mouseSampleScratch)) return
        val sampleX = mouseSampleScratch[0]
        val sampleY = mouseSampleScratch[1]
        val timeScale = sampleClock.timeScale(eventTimeNanos, deviceId)
        if (!processLookSample(sampleX, sampleY, timeScale, lookOutputScratch)) return
        val outputX = lookOutputScratch[0]
        val outputY = lookOutputScratch[1]
        InputDiagnostics.recordMouseProcessed(outputX, outputY)
        if (outputX == 0f && outputY == 0f) return
        var schedule = false
        synchronized(lookLock) {
            pendX = boundedDeltaSum(pendX, outputX)
            pendY = boundedDeltaSum(pendY, outputY)
            val threshold = currentLookDeadzone()
            if (!flushScheduled && hypot(pendX, pendY) >= threshold) {
                flushScheduled = true
                schedule = true
            }
        }
        if (schedule) {
            // lookIntervalMs agrupa somente a saída já transformada; não altera aceleração.
            val interval = cfg().lookIntervalMs.coerceIn(1, 20).toLong()
            val wait = (lastFlush + interval - SystemClock.uptimeMillis()).coerceAtLeast(0L)
            touch.handler.postDelayed(flushLook, wait)
        }
    }


    private fun currentLookDeadzone(): Float {
        val s = cfg()
        val button = if (aimId >= 0) s.buttons.firstOrNull { it.id == aimId } else null
        return (button?.aimDeadzone ?: s.deadzone).coerceAtLeast(0f)
    }


    /** Soma acumulada saturada: uma rajada/driver defeituoso nunca pode envenenar o próximo frame com Infinity. */
    private fun boundedDeltaSum(current: Float, delta: Float): Float {
        val sum = current.toDouble() + delta.toDouble()
        return sum.coerceIn(-MAX_PENDING_MOUSE_DELTA.toDouble(), MAX_PENDING_MOUSE_DELTA.toDouble()).toFloat()
    }

    private fun flushLookNow() {
        var dx = 0f
        var dy = 0f
        synchronized(lookLock) {
            dx = pendX
            dy = pendY
            pendX = 0f
            pendY = 0f
            flushScheduled = false
        }
        lastFlush = SystemClock.uptimeMillis()
        if (paused || touch.broken || (dx == 0f && dy == 0f)) return // Shizuku caiu: não gasta CPU à toa
        if (applyLookDelta(dx, dy)) InputDiagnostics.recordMouseForwarded(dx, dy)
    }

    /** Solta tudo (troca de monitor, mouse removido, pausa, modo edição, queda do Shizuku). */
    fun releaseAll() {
        var schedule = false
        synchronized(inputQueueLock) {
            inputQueue.offerRelease()
            InputDiagnostics.recordInputQueueDepth(inputQueue.size())
            if (!inputDrainScheduled) {
                inputDrainScheduled = true
                schedule = true
            }
        }
        postInputDrain(schedule)
    }

    /** Executado exclusivamente na thread serializada do motor. */
    private fun releaseAllOnHandler() {
        touch.handler.removeCallbacks(releaseLook)
        touch.handler.removeCallbacks(flushLook)
        pendingActions.toList().forEach { touch.handler.removeCallbacks(it) }
        pendingActions.clear()
        synchronized(lookLock) {
            pendX = 0f
            pendY = 0f
            flushScheduled = false
        }
        keys.clear()
        held.clear()
        toggled.clear()
        buttonPointerIds.clear()
        scrollAccumulator.reset()
        joyActive = false
        lookActive = false
        lookId = LOOK_A
        sens.reset()
        mouseProcessor.reset()
        sampleClock.reset()
        virtualCursor.reset()
        aimId = -1
        touch.releaseAll()
    }

    // ---- teclas (botões e joystick: lógica inalterada) ----

    private fun handleKey(code: Int, down: Boolean) {
        // Código 0 representa controle ainda sem tecla associada no editor; nunca deve acionar o motor.
        if (code == 0) return
        // Also reject input tasks that were queued before a pause/profile transition.
        if (paused) return
        val s = cfg()
        // Teclados físicos podem emitir vários ACTION_DOWN para a mesma tecla enquanto ela
        // permanece pressionada. Tratar esses repeats como novos cliques quebra TOGGLE,
        // duplica toques e pode deixar ações em estado inconsistente.
        val duplicateTransition = if (down) !held.add(code) else !held.remove(code)
        if (duplicateTransition) return
        if (code == KeyEvent.KEYCODE_W || code == KeyEvent.KEYCODE_A ||
            code == KeyEvent.KEYCODE_S || code == KeyEvent.KEYCODE_D
        ) {
            if (down) keys.add(code) else keys.remove(code)
            updateJoy(s)
            return
        }
        for (b in s.buttons) {
            if (b.key != code) continue
            when (b.kind) {
                Kind.AIM -> aimKey(b, s, down)
                Kind.FIRE -> {
                    fireKey(b, down)
                    linkAim(b, s, down)
                }
                else -> standardKey(b, s, down)
            }
        }
    }

    /** TIRO: HOLD = DOWN ao apertar e UP só ao soltar a tecla; TAP = toque único. */
    private fun fireKey(b: HudButton, down: Boolean) {
        if (b.mode == Mode.TAP) {
            if (down) tap(b)
        } else {
            if (down) press(b) else release(b)
        }
    }

    /** PADRÃO: simula qualquer tecla. Com macro, aciona os botões escolhidos (e não o próprio). */
    private fun standardKey(b: HudButton, s: AppSettings, down: Boolean) {
        if (b.macro.isNotEmpty()) {
            for (id in b.macro) {
                val t = s.buttons.firstOrNull { it.id == id } ?: continue
                if (down) press(t) else if (!toggled.contains(t.id)) release(t)
            }
            return
        }
        val act = Runnable {
            when (b.mode) {
                Mode.TAP -> if (down) tap(b)
                Mode.DOUBLE -> if (down) doubleTap(b)
                Mode.TOGGLE -> if (down) {
                    if (toggled.remove(b.id)) release(b) else {
                        toggled.add(b.id)
                        press(b)
                    }
                }
                else -> if (down) press(b) else release(b)
            }
        }
        // "Tempo de resposta": atraso fixo (a ordem dos eventos é preservada, pois o atraso é igual para todos)
        val delay = b.delayMs.toLong().coerceIn(0L, 500L)
        if (delay > 0L) scheduleAction(delay, act) else act.run()
    }

    /** Agenda uma ação cancelável e a remove do registro quando termina. Chamado no handler. */
    private fun scheduleAction(delayMs: Long, action: Runnable) {
        lateinit var tracked: Runnable
        tracked = Runnable {
            pendingActions.remove(tracked)
            action.run()
        }
        pendingActions.add(tracked)
        touch.handler.postDelayed(tracked, delayMs.coerceAtLeast(0L))
    }

    /** Duplo toque: um aperto = dois toques curtos (intervalo fixo). */
    private fun doubleTap(b: HudButton) {
        tap(b)
        scheduleAction(90) { tap(b) }
    }

    private fun press(b: HudButton) {
        val p = pointerId(b) ?: return
        if (touch.isDown(p)) return
        val a = area()
        val point = CoordinateTransformer.normalizedToPixels(a, b.x, b.y)
        touch.down(p, point.x, point.y)
    }

    private fun release(b: HudButton) {
        val p = buttonPointerIds.remove(b.id) ?: return
        touch.up(p)
    }

    /** Toque curto: desce e solta sozinho. */
    private fun tap(b: HudButton) {
        val p = pointerId(b) ?: return
        if (touch.isDown(p)) return
        val a = area()
        val point = CoordinateTransformer.normalizedToPixels(a, b.x, b.y)
        touch.down(p, point.x, point.y)
        scheduleAction(25) {
            touch.up(p)
            if (buttonPointerIds[b.id] == p) buttonPointerIds.remove(b.id)
        }
    }

    /** MIRA: segurar, ou suspender (toggle): um toque ativa e mantém, o próximo desativa. */
    private fun aimKey(b: HudButton, s: AppSettings, down: Boolean) {
        if (b.aimToggle) {
            if (!down) return                  // soltar a tecla não desativa
            tap(b)
            val on = aimId != b.id
            aimId = if (on) b.id else -1
            fireWithAim(b, s, on)
        } else {
            if (down) {
                press(b)
                aimId = b.id
            } else {
                release(b)
                if (aimId == b.id) aimId = -1
            }
            fireWithAim(b, s, down)
        }
    }

    /** "Atirar com a tecla de mira": a mira também aciona os botões ligados à tecla de tiro. */
    private fun fireWithAim(ab: HudButton, s: AppSettings, down: Boolean) {
        if (!ab.fireWithAim) return
        for (f in s.buttons) {
            if (f.id == ab.id || f.kind == Kind.AIM || f.key != ab.shootKey) continue
            if (f.mode == Mode.TAP) {
                if (down) tap(f)
            } else if (down) {
                press(f)
            } else {
                release(f)
            }
        }
    }

    /** "Mirar ao atirar": o tiro também segura o botão de mira escolhido. */
    private fun linkAim(b: HudButton, s: AppSettings, down: Boolean) {
        if (b.linkAimId < 0) return
        val a = s.buttons.firstOrNull { it.id == b.linkAimId && it.kind == Kind.AIM } ?: return
        if (down) {
            press(a)
            aimId = a.id
        } else {
            release(a)
            if (aimId == a.id) aimId = -1
        }
    }

    // ---- joystick (WASD) ----

    private fun updateJoy(s: AppSettings) {
        val vx = (if (keys.contains(KeyEvent.KEYCODE_D)) 1 else 0) - (if (keys.contains(KeyEvent.KEYCODE_A)) 1 else 0)
        val vy = (if (keys.contains(KeyEvent.KEYCODE_S)) 1 else 0) - (if (keys.contains(KeyEvent.KEYCODE_W)) 1 else 0)
        if (vx == 0 && vy == 0) {
            if (joyActive) {
                touch.up(JOY)
                joyActive = false
            }
            return
        }
        val a = area()
        val len = hypot(vx.toFloat(), vy.toFloat())
        val cx = a.left + s.joyX * a.width()
        val cy = a.top + s.joyY * a.height()
        val r = s.joyR * min(a.width(), a.height())
        if (!joyActive) {
            touch.down(JOY, cx, cy)
            joyActive = true
        }
        touch.move(JOY, cx + vx / len * r, cy + vy / len * r)
    }

    // ---- câmera (mouse) ----

    /**
     * AUTO prioriza compatibilidade com jogos de toque: arrasto touchscreen com reancoragem.
     * Mouse relativo nativo só é escolhido explicitamente porque muitos shooters Android não
     * interpretam SOURCE_MOUSE_RELATIVE como entrada de câmera. Não inferimos suporte pelo nome
     * do pacote do jogo.
     */
    private fun resolveLookMode(s: AppSettings, ab: HudButton?): Int =
        LookModePolicy.resolve(s.lookMode, ab?.rotMode ?: -1, s.relativeMouse)

    /** Processa uma amostra individual; só o resultado transformado é agregado. */
    /** Preenche buffer reutilizável para evitar Pair alocado a cada amostra física. */
    private fun processLookSample(dx: Float, dy: Float, timeScale: Float, out: FloatArray): Boolean {
        val s = cfg()
        val ab = if (aimId >= 0) s.buttons.firstOrNull { it.id == aimId } else null
        if (ab != null && !ab.mouseAim) return false
        val invert = s.invertY != (ab != null && ab.aimInvert)
        val ady = if (invert) -dy else dy

        // Valores de referência BlueStacks: 69 e 16240 representam fator 1.0.
        val bsMouseScale = (s.bsMouseSensitivity / 69f).coerceIn(0f, 2.9f)
        val bsFineTuneScale = (s.bsFineTune / 16240f).coerceIn(0.25f, 4f)
        val speed = PointerSpeed.mult(s.pointerSpeed) * s.globalSens * bsMouseScale * bsFineTuneScale
        val relationY = s.bsSensitivityRatio.coerceIn(0.01f, 10f)
        if (ab != null) {
            val shooting = held.contains(ab.shootKey)
            val mouse = (ab.aimMouseSens / 69f).coerceIn(0f, 2.9f)
            val zoom = ab.aimZoom.coerceAtLeast(1f)
            val general = (ab.aimGeneral / 16240f).coerceIn(0.0001f, 4f) * speed / zoom
            val sx = ab.aimSensX * (if (shooting) ab.shootSensX else 1f) * general
            val sy = ab.aimSensY * (if (shooting) ab.shootSensY else 1f) * general
            val level = ab.aimAccelLevel.coerceIn(0f, 10f)
            val curve = if (level > 0f) 1 else if (s.epp) 4 else 0
            sens.process(dx * mouse, ady * mouse, sx, sy, curve, 1f + level * 0.1f, s.knee, s.gain, timeScale)
        } else {
            val curve = if (s.epp && s.curve == 0) 4 else s.curve
            sens.process(dx, ady, s.sensX * speed, s.sensY * speed * relationY,
                curve, s.exponent, s.knee, s.gain, timeScale)
        }
        out[0] = sens.outX.toFloat()
        out[1] = sens.outY.toFloat()
        return true
    }

    /** Aplica um lote de deltas já calibrados ao backend de visão escolhido. */
    private fun applyLookDelta(dx: Float, dy: Float): Boolean {
        val s = cfg()
        val ab = if (aimId >= 0) s.buttons.firstOrNull { it.id == aimId } else null
        if (ab != null && !ab.mouseAim) return false
        // O mouse virtual mantém um referencial lógico independente da tela. Sua posição é
        // telemetria, não coordenada de saída: nunca derive o delta enviado pela diferença entre
        // posições acumuladas. Só avance esse referencial quando o lote for aceito pelo backend.
        val moveX = dx
        val moveY = dy
        if (moveX == 0f && moveY == 0f) return false

        val effectiveLookMode = resolveLookMode(s, ab)
        InputDiagnostics.setOutputMode(
            when (effectiveLookMode) {
                Rot.INFINITE -> "SOURCE_MOUSE_RELATIVE (compatibilidade do destino não confirmada)"
                Rot.OFF -> "Touchscreen absoluto limitado"
                else -> "Touchscreen com reancoragem UP/DOWN"
            }
        )
        if (effectiveLookMode == Rot.INFINITE) {
            // Backend nativo relativo: somente para destinos que realmente aceitam mouse relativo.
            val accepted = injectInfiniteDelta(moveX, moveY)
            if (accepted) virtualCursor.advance(moveX, moveY)
            return accepted
        }

        val a = area()
        if (!a.left.isFinite() || !a.top.isFinite() || !a.right.isFinite() || !a.bottom.isFinite() ||
            a.width() <= 0f || a.height() <= 0f
        ) {
            Log.w("KeyMapperLook", "Área de jogo inválida; lote relativo não encaminhado")
            return false
        }
        val marginX = min(EDGE_MARGIN, (a.width() * 0.1f).coerceAtLeast(1f))
        val marginY = min(EDGE_MARGIN, (a.height() * 0.1f).coerceAtLeast(1f))
        val minX = a.left + marginX
        val maxX = a.right - marginX
        val minY = a.top + marginY
        val maxY = a.bottom - marginY
        // origem do arrasto = posição da mira (ou área OLHAR); fica sempre dentro dos limites reais da tela
        val fx = if (ab != null) (ab.aimPosX / 1000f).coerceIn(0f, 1f) else s.lookX
        val fy = if (ab != null) (ab.aimPosY / 1000f).coerceIn(0f, 1f) else s.lookY
        // Para a mira global, a posição X usa escala lógica 0..1000, mapeada para a largura da área.
        // Valores salvos no formato anterior (0..100) são migrados no AppSettings.fromJson().
        // O ponto de reancoragem deve permanecer dentro da área, não exatamente na borda.
        // Caso contrário, mover novamente para fora no mesmo sentido não teria espaço para progredir.
        val insetX = min(32f, (maxX - minX).coerceAtLeast(0f) * 0.1f)
        val insetY = min(32f, (maxY - minY).coerceAtLeast(0f) * 0.1f)
        val anchorMinX = minX + insetX
        val anchorMaxX = maxX - insetX
        val anchorMinY = minY + insetY
        val anchorMaxY = maxY - insetY
        val cx = if (ab != null) {
            (a.left + fx * a.width()).coerceIn(anchorMinX, anchorMaxX)
        } else {
            (a.left + (s.bsLookPositionX.coerceIn(0, 1000) / 1000f) * a.width()).coerceIn(anchorMinX, anchorMaxX)
        }
        val cy = (a.top + fy * a.height()).coerceIn(anchorMinY, anchorMaxY)

        val accepted = when (effectiveLookMode) {
            Rot.OFF -> lookClamped(s, cx, cy, moveX, moveY, minX, maxX, minY, maxY)
            else -> lookCenter(s, cx, cy, moveX, moveY, minX, maxX, minY, maxY)
        }
        if (accepted) virtualCursor.advance(moveX, moveY)
        return accepted
    }

    /** Mantém referência lógica ilimitada e encaminha deltas relativos ao backend nativo explícito. */
    private fun injectInfiniteDelta(dx: Float, dy: Float): Boolean {
        if (!dx.isFinite() || !dy.isFinite()) return false
        // Retorna apenas se o lote entrou na fila local do injetor, não se o destino o consumiu.
        return touch.injectVirtualMove(dx, dy)
    }

    private fun beginLook(cx: Float, cy: Float) {
        if (!lookActive) {
            lx = cx
            ly = cy
            // A referência lógica não é redefinida pela coordenada absoluta do toque; o contato
            // touchscreen é apenas a origem física necessária ao fallback de arrasto.
            touch.down(lookId, lx, ly)
            lookActive = true
        }
    }

    private fun armRelease(delayMs: Long) {
        touch.handler.removeCallbacks(releaseLook)
        if (delayMs > 0L) touch.handler.postDelayed(releaseLook, delayMs)
    }

    private fun idleMs(s: AppSettings): Long {
        // 0 significa manter o contato até a mira ser liberada/pausada. TouchInjector.up()
        // força a entrega do último MOVE pendente antes de liberar o contato.
        return s.lookIdleMs.coerceIn(0, 60_000).toLong()
    }

    /**
     * Modo touchscreen com reancoragem: a entrada física continua relativa, mas a saída é um arrasto
     * absoluto. Ao alcançar uma margem, o contato é levado até ela, liberado e recriado no ponto
     * de ancoragem sem reposicionar o mouse físico. O excedente é preservado. A sequência UP/DOWN
     * não é mouse relativo nativo e pode produzir uma pequena interrupção perceptível no jogo.
     */
    private fun lookCenter(
        s: AppSettings, cx: Float, cy: Float, ox: Float, oy: Float,
        minX: Float, maxX: Float, minY: Float, maxY: Float
    ): Boolean {
        val startX = if (lookActive) lx else cx
        val startY = if (lookActive) ly else cy
        val plan = LookReanchorPlanner.plan(
            startX = startX,
            startY = startY,
            deltaX = ox,
            deltaY = oy,
            anchorX = cx,
            anchorY = cy,
            bounds = LookReanchorPlanner.Bounds(minX, maxX, minY, maxY)
        ) ?: return false
        beginLook(cx, cy)
        for (point in plan.reanchors) {
            val next = if (lookId == LOOK_A) LOOK_B else LOOK_A
            touch.swap(lookId, next, point.edgeX, point.edgeY, cx, cy)
            lookId = next
        }
        if (plan.capped) {
            Log.w("KeyMapperLook", "Delta exigiu mais reancoragens que o limite de segurança; excedente foi limitado")
        }
        lx = plan.finalX
        ly = plan.finalY
        touch.move(lookId, lx, ly)
        armRelease(idleMs(s))
        return true
    }

    /** Modo limitado: a mira para na margem da área e não reancora o contato. */
    private fun lookClamped(
        s: AppSettings, cx: Float, cy: Float, ox: Float, oy: Float,
        minX: Float, maxX: Float, minY: Float, maxY: Float
    ): Boolean {
        beginLook(cx, cy)
        lx = (lx + ox).coerceIn(minX, maxX)
        ly = (ly + oy).coerceIn(minY, maxY)
        touch.move(lookId, lx, ly)
        armRelease(idleMs(s))
        return true
    }

    private fun endLook() {
        if (lookActive) {
            touch.up(lookId)
            lookActive = false
            lookId = LOOK_A
        }
        touch.handler.removeCallbacks(flushLook)
        synchronized(lookLock) {
            pendX = 0f
            pendY = 0f
            flushScheduled = false
        }
        sampleClock.reset()
        // O referencial lógico do mouse persiste durante a sessão; só releaseAll() reinicia.
        sens.reset()
    }
}
