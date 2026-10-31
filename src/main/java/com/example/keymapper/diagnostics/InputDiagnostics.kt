package com.example.keymapper.diagnostics

import android.os.Build
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/**
 * Métricas locais em memória. Não registra teclas individuais, texto digitado ou coordenadas.
 * As etapas são mantidas separadas: amostra recebida, delta transformado, lote encaminhado e
 * resultado booleano informado pelo mecanismo de injeção do Android.
 */
object InputDiagnostics {
    private const val SAMPLE_COUNT = 256
    private val lock = Any()
    private val mouseReceived = AtomicLong(0)
    private val mouseReceivedWithoutPointerCapture = AtomicLong(0)
    private val mouseProcessed = AtomicLong(0)
    private val mouseForwarded = AtomicLong(0)
    private val keyDowns = AtomicLong(0)
    private val keyUps = AtomicLong(0)
    private val buttonTransitions = AtomicLong(0)
    private val injectionSuccesses = AtomicLong(0)
    private val injectionFailures = AtomicLong(0)
    private val inputQueueMouseDrops = AtomicLong(0)
    private val inputQueueScrollDrops = AtomicLong(0)
    private val inputQueueStaleMouseDrops = AtomicLong(0)
    private val inputQueueControlDrops = AtomicLong(0)
    private val inputQueuePeakDepth = AtomicInteger(0)
    private val startedAt = SystemClock.elapsedRealtime()
    private val injectionDurations = LongArray(SAMPLE_COUNT)
    private val queueResidenceDurations = LongArray(SAMPLE_COUNT)
    private val drainDurations = LongArray(SAMPLE_COUNT)
    private var durationCount = 0
    private var durationIndex = 0
    private var queueResidenceCount = 0
    private var queueResidenceIndex = 0
    private var drainDurationCount = 0
    private var drainDurationIndex = 0
    private val inputDrainBatches = AtomicLong(0)
    private val inputDrainEvents = AtomicLong(0)
    private val inputDrainMaxBatch = AtomicInteger(0)
    private var lastDx = 0f
    private var lastDy = 0f
    private var lastProcessedX = 0f
    private var lastProcessedY = 0f
    private var lastForwardedX = 0f
    private var lastForwardedY = 0f
    private var lastDeviceId = -1
    private var lastEventTimeNanos = 0L
    private var captureRequested = false
    private var captureConfirmed = false
    private var windowFocused = false
    private var mode = "INACTIVE"
    private var backend = "Não selecionado"
    private var outputMode = "Ainda não utilizado"
    private var permissionStatus = "Não verificada"
    private var inputDevices = "Ainda não enumerados"
    private var backendProbe = "Ainda não executada"
    private var lastTransition = "Inicializado"

    fun recordMouse(
        dx: Float, dy: Float, deviceId: Int, eventTimeNanos: Long,
        pointerCaptureConfirmed: Boolean
    ) {
        mouseReceived.incrementAndGet()
        if (!pointerCaptureConfirmed) mouseReceivedWithoutPointerCapture.incrementAndGet()
        synchronized(lock) {
            lastDx = dx
            lastDy = dy
            lastDeviceId = deviceId
            lastEventTimeNanos = eventTimeNanos
            // A rota de entrada relativa e a confirmação de Pointer Capture são métricas distintas.
            mode = if (pointerCaptureConfirmed) "RELATIVE_ACTIVE" else "RELATIVE_FALLBACK_ACTIVE"
        }
    }

    fun recordMouseProcessed(dx: Float, dy: Float) {
        mouseProcessed.incrementAndGet()
        synchronized(lock) { lastProcessedX = dx; lastProcessedY = dy }
    }

    /** Lote passado ao injetor; não significa que InputManager o aceitou. */
    fun recordMouseForwarded(dx: Float, dy: Float) {
        mouseForwarded.incrementAndGet()
        synchronized(lock) { lastForwardedX = dx; lastForwardedY = dy }
    }

    fun recordKey(down: Boolean) {
        if (down) keyDowns.incrementAndGet() else keyUps.incrementAndGet()
    }

    fun recordButton() { buttonTransitions.incrementAndGet() }

    /** Registra saturação da fila pré-alocada; não guarda coordenadas nem teclas individuais. */
    fun recordInputQueueDrop(mouse: Boolean, count: Long = 1L) {
        if (count <= 0L) return
        if (mouse) inputQueueMouseDrops.addAndGet(count)
        else inputQueueControlDrops.addAndGet(count)
    }

    /** Scroll descartado por saturação da fila ou porque seu dispositivo de origem foi removido. */
    fun recordInputQueueScrollDrop(count: Long) {
        if (count > 0L) inputQueueScrollDrops.addAndGet(count)
    }

    /** Movimento removido por idade durante recuperação de backlog; também conta como drop de mouse. */
    fun recordStaleMouseDrops(count: Long) {
        if (count <= 0L) return
        inputQueueMouseDrops.addAndGet(count)
        inputQueueStaleMouseDrops.addAndGet(count)
    }

    fun recordInputQueueDepth(depth: Int) {
        var current = inputQueuePeakDepth.get()
        while (depth > current) {
            if (inputQueuePeakDepth.compareAndSet(current, depth)) return
            current = inputQueuePeakDepth.get()
        }
    }

    /** Tempo monotônico entre entrada na fila local e retirada pelo consumidor. */
    fun recordInputQueueResidence(durationNs: Long) {
        synchronized(lock) {
            queueResidenceDurations[queueResidenceIndex] = durationNs.coerceAtLeast(0L)
            queueResidenceIndex = (queueResidenceIndex + 1) % SAMPLE_COUNT
            queueResidenceCount = (queueResidenceCount + 1).coerceAtMost(SAMPLE_COUNT)
        }
    }

    /** Métrica do consumidor; não inclui o tempo posterior gasto pelo jogo para processar o evento. */
    fun recordInputDrainBatch(eventCount: Int, durationNs: Long) {
        if (eventCount <= 0) return
        inputDrainBatches.incrementAndGet()
        inputDrainEvents.addAndGet(eventCount.toLong())
        var maxBatch = inputDrainMaxBatch.get()
        while (eventCount > maxBatch) {
            if (inputDrainMaxBatch.compareAndSet(maxBatch, eventCount)) break
            maxBatch = inputDrainMaxBatch.get()
        }
        synchronized(lock) {
            drainDurations[drainDurationIndex] = durationNs.coerceAtLeast(0L)
            drainDurationIndex = (drainDurationIndex + 1) % SAMPLE_COUNT
            drainDurationCount = (drainDurationCount + 1).coerceAtMost(SAMPLE_COUNT)
        }
    }

    fun setCapture(
        isCaptured: Boolean,
        hasFocus: Boolean,
        reason: String,
        requested: Boolean = isCaptured
    ) {
        synchronized(lock) {
            captureRequested = requested
            windowFocused = hasFocus
            // Android só mantém Pointer Capture válido enquanto a janela solicitante está focada.
            captureConfirmed = isCaptured && hasFocus
            mode = when {
                !hasFocus && requested -> "SUSPENDED"
                captureConfirmed -> "GRABBED"
                requested -> "GRAB_REQUESTED"
                else -> "RELEASED"
            }
            lastTransition = "$reason @ ${SystemClock.elapsedRealtime()} ms"
        }
    }

    fun setMode(value: String, reason: String) {
        synchronized(lock) {
            mode = value
            lastTransition = "$reason @ ${SystemClock.elapsedRealtime()} ms"
        }
    }

    fun setBackend(value: String) {
        synchronized(lock) { backend = value }
    }

    fun setOutputMode(value: String) {
        synchronized(lock) { outputMode = value }
    }

    fun setPermissionStatus(value: String) {
        synchronized(lock) { permissionStatus = value }
    }

    fun setInputDevices(value: String) {
        synchronized(lock) { inputDevices = value }
    }

    fun setBackendProbe(value: String) {
        synchronized(lock) { backendProbe = value }
    }

    fun recordInjection(success: Boolean, durationNs: Long) {
        if (success) injectionSuccesses.incrementAndGet() else injectionFailures.incrementAndGet()
        synchronized(lock) {
            injectionDurations[durationIndex] = durationNs.coerceAtLeast(0L)
            durationIndex = (durationIndex + 1) % SAMPLE_COUNT
            durationCount = (durationCount + 1).coerceAtMost(SAMPLE_COUNT)
        }
    }

    fun summary(): String {
        val s = snapshot()
        return "${s.mode}; foco=${s.windowFocused}; captura=${s.captureConfirmed}; " +
            "entrada=${s.mouseReceived}; processada=${s.mouseProcessed}; encaminhada=${s.mouseForwarded}; " +
            "InputManager=${s.injectionSuccesses}/${s.injectionFailures}; " +
            "filaPico=${inputQueuePeakDepth.get()}/${com.example.keymapper.input.InputEventQueue.DEFAULT_CAPACITY}; " +
            "filaDrops=mouse:${inputQueueMouseDrops.get()}/scroll:${inputQueueScrollDrops.get()}/controle:${inputQueueControlDrops.get()} " +
            "(mouseAtrasado=${inputQueueStaleMouseDrops.get()}); " +
            "filaP95=${s.queueWaitP95Ms}ms; backend=${s.backend}"
    }

    fun report(): String {
        val s = snapshot()
        return buildString {
            appendLine("DIAGNÓSTICO DE ENTRADA — KeyMapper")
            appendLine("Tempo de processo: ${s.uptimeSeconds} s")
            appendLine("Estado observado: ${s.mode}")
            appendLine("Backend de entrada/saída: ${s.backend}")
            appendLine("Modo de saída da visão: ${s.outputMode}")
            appendLine("Escopo de captura: Pointer Capture pertence à janela focada que o solicitou; não é captura global do mouse.")
            appendLine("Acesso global a /dev/input e criação de uinput: não implementados nem validados nesta versão.")
            appendLine("Permissão/serviço: ${s.permissionStatus}")
            appendLine("Dispositivos Android enumerados: ${s.inputDevices}")
            appendLine()
            appendLine(s.backendProbe)
            appendLine("Janela focada: ${s.windowFocused}")
            appendLine("Captura solicitada: ${s.captureRequested}")
            appendLine("Pointer Capture confirmado: ${s.captureConfirmed}")
            val backendDecision = com.example.keymapper.input.CaptureBackendPolicy.evaluate(
                com.example.keymapper.input.CaptureBackendEnvironment(
                    sdkInt = Build.VERSION.SDK_INT,
                    windowFocused = s.windowFocused
                )
            )
            appendLine("Backend selecionado pela política: ${backendDecision.kind}")
            appendLine("Prontidão do backend: ${backendDecision.readiness}")
            appendLine("Captura global confirmada pela política: ${backendDecision.globalCapture}")
            appendLine("Justificativa: ${backendDecision.explanation}")
            appendLine("Última transição: ${s.lastTransition}")
            appendLine("Amostras físicas recebidas: ${s.mouseReceived}")
            appendLine("Amostras relativas recebidas sem Pointer Capture: ${s.mouseReceivedWithoutPointerCapture}")
            appendLine("Amostras processadas após sensibilidade: ${s.mouseProcessed}")
            appendLine("Lotes encaminhados ao injetor: ${s.mouseForwarded}")
            appendLine("Fila de entrada: pico=${inputQueuePeakDepth.get()}/" +
                "${com.example.keymapper.input.InputEventQueue.DEFAULT_CAPACITY}; " +
                "descartes de movimento=${inputQueueMouseDrops.get()} " +
                "(atrasados=${inputQueueStaleMouseDrops.get()}), " +
                "descartes de scroll=${inputQueueScrollDrops.get()}, " +
                "descartes de controle=${inputQueueControlDrops.get()}")
            appendLine("Política de recuperação: em cada lote, amostras de mouse com mais de " +
                "${com.example.keymapper.input.InputEventQueue.STALE_MOUSE_MAX_AGE_NANOS / 1_000_000L} ms são descartadas, inclusive em filas pequenas; " +
                "teclas/scroll/RELEASE preservam ordem. Movimento pode ser perdido após bloqueio prolongado.")
            appendLine("Espera na fila (janela=${s.queueWaitSampleCount}/$SAMPLE_COUNT amostras): " +
                "média=${s.queueWaitAverageMs} ms, p50=${s.queueWaitP50Ms} ms, " +
                "p95=${s.queueWaitP95Ms} ms, p99=${s.queueWaitP99Ms} ms, " +
                "máximo=${s.queueWaitMaxMs} ms")
            appendLine("Consumidor: lotes=${s.inputDrainBatches}, eventos=${s.inputDrainEvents}, " +
                "maiorLote=${s.inputDrainMaxBatch}; duração do lote p95=${s.drainDurationP95Ms} ms " +
                "(janela=${s.drainDurationSampleCount}/$SAMPLE_COUNT)")
            appendLine("Medições de injeção: ${s.injectionDurationSampleCount}/$SAMPLE_COUNT amostras na janela")
            appendLine("Nota estatística: percentis usam somente as amostras disponíveis na janela; com poucas observações, p95/p99 são estimativas instáveis.")
            appendLine("Último dispositivo ID: ${s.lastDeviceId}; eventTime=${s.lastEventTimeNanos} ns (${s.lastEventTimeNanos / 1_000_000.0} ms)")
            appendLine(
                "Resolução temporal da API MotionEvent: " +
                    if (Build.VERSION.SDK_INT >= 34) "nanossegundos (API 34+)" else "milissegundos (API 26–33; conversão para ns não aumenta a precisão)"
            )
            appendLine("Último delta recebido: X=${s.lastDx}, Y=${s.lastDy}")
            appendLine("Última saída calibrada: X=${s.lastProcessedX}, Y=${s.lastProcessedY}")
            appendLine("Último lote encaminhado: X=${s.lastForwardedX}, Y=${s.lastForwardedY}")
            appendLine("Teclas: DOWN=${s.keyDowns}, UP=${s.keyUps}")
            appendLine("Transições de botões do mouse: ${s.buttonTransitions}")
            appendLine("InputManager.injectInputEvent: aceitos=${s.injectionSuccesses}, recusados/falhos=${s.injectionFailures}")
            appendLine("Duração da chamada de injeção: média=${s.averageInjectionMs} ms, " +
                "p50=${s.p50InjectionMs} ms, p95=${s.p95InjectionMs} ms, p99=${s.p99InjectionMs} ms")
            appendLine("Limite: retorno positivo do injetor não confirma que o jogo processou o evento.")
            appendLine()
            appendLine(ReferenceBackendEvidence.report())
        }
    }

    private fun snapshot(): Snapshot = synchronized(lock) {
        fun samples(values: LongArray, count: Int, index: Int): List<Long> =
            (0 until count).map { i -> values[(index - count + i + SAMPLE_COUNT) % SAMPLE_COUNT] }.sorted()
        fun percentile(values: List<Long>, percent: Int): Double {
            if (values.isEmpty()) return 0.0
            val sampleIndex = (((values.size - 1) * percent) / 100).coerceIn(0, values.lastIndex)
            return values[sampleIndex] / 1_000_000.0
        }
        fun average(values: List<Long>): Double =
            if (values.isEmpty()) 0.0 else values.average() / 1_000_000.0
        val durations = samples(injectionDurations, durationCount, durationIndex)
        val queueWaits = samples(queueResidenceDurations, queueResidenceCount, queueResidenceIndex)
        val drains = samples(drainDurations, drainDurationCount, drainDurationIndex)
        Snapshot(
            mouseReceived.get(), mouseReceivedWithoutPointerCapture.get(), mouseProcessed.get(),
            mouseForwarded.get(), keyDowns.get(), keyUps.get(),
            buttonTransitions.get(), injectionSuccesses.get(), injectionFailures.get(),
            lastDx, lastDy, lastProcessedX, lastProcessedY, lastForwardedX, lastForwardedY,
            lastDeviceId, lastEventTimeNanos, captureRequested, captureConfirmed, windowFocused,
            mode, backend, outputMode, permissionStatus, inputDevices, backendProbe, lastTransition,
            ((SystemClock.elapsedRealtime() - startedAt) / 1000L), average(durations),
            percentile(durations, 50), percentile(durations, 95), percentile(durations, 99),
            durationCount, average(queueWaits), percentile(queueWaits, 50),
            percentile(queueWaits, 95), percentile(queueWaits, 99), percentile(queueWaits, 100),
            queueResidenceCount, inputDrainBatches.get(), inputDrainEvents.get(),
            inputDrainMaxBatch.get(), percentile(drains, 95), drainDurationCount
        )
    }

    private data class Snapshot(
        val mouseReceived: Long, val mouseReceivedWithoutPointerCapture: Long,
        val mouseProcessed: Long, val mouseForwarded: Long,
        val keyDowns: Long, val keyUps: Long, val buttonTransitions: Long,
        val injectionSuccesses: Long, val injectionFailures: Long,
        val lastDx: Float, val lastDy: Float, val lastProcessedX: Float, val lastProcessedY: Float,
        val lastForwardedX: Float, val lastForwardedY: Float,
        val lastDeviceId: Int, val lastEventTimeNanos: Long,
        val captureRequested: Boolean, val captureConfirmed: Boolean, val windowFocused: Boolean,
        val mode: String, val backend: String, val outputMode: String,
        val permissionStatus: String, val inputDevices: String, val backendProbe: String,
        val lastTransition: String,
        val uptimeSeconds: Long, val averageInjectionMs: Double,
        val p50InjectionMs: Double, val p95InjectionMs: Double, val p99InjectionMs: Double,
        val injectionDurationSampleCount: Int,
        val queueWaitAverageMs: Double, val queueWaitP50Ms: Double, val queueWaitP95Ms: Double,
        val queueWaitP99Ms: Double, val queueWaitMaxMs: Double, val queueWaitSampleCount: Int,
        val inputDrainBatches: Long, val inputDrainEvents: Long, val inputDrainMaxBatch: Int,
        val drainDurationP95Ms: Double, val drainDurationSampleCount: Int
    )
}
