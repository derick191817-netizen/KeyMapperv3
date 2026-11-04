package com.example.keymapper.injection

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import com.example.keymapper.diagnostics.InputDiagnostics

/**
 * Monta eventos multitoque e injeta (via Shizuku). Todos os métodos devem ser chamados na thread de
 * [handler] (prioridade URGENT_DISPLAY).
 *
 * Estabilidade:
 *  - THROTTLING: movimentos relativos são agregados em janelas de 8,333 ms (alvo aproximado de 120 Hz),
 *    sem descartar a soma dos deltas. Eventos críticos de toque continuam preservados.
 *  - MEMÓRIA: todo MotionEvent.obtain() é seguido de recycle() em try/finally.
 *  - SHIZUKU: a injeção é protegida por try/catch (Throwable). Se falhar [MAX_FAILS] vezes seguidas, o injetor
 *    "quebra" (para de tentar, sem travar nada), avisa por [onFailure] e volta sozinho quando o Shizuku responder.
 *
 * Coordenadas VIRTUAIS: não são limitadas ao tamanho da tela (só NaN e ±1.000.000 px são barrados).
 */
class TouchInjector(
    private val injector: Injector,
    private val displayId: () -> Int,
    private val displaySize: () -> Pair<Int, Int> = { 0 to 0 },
    private val virtualTouchOutput: VirtualTouchOutput? = null
) {
    companion object {
        private const val MIN_MOVE_INTERVAL_NS = 8_333_333L
        private const val MIN_VIRTUAL_MOVE_INTERVAL_NS = 8_333_333L
        private const val RELEASE_RETRY_DELAY_MS = 250L
        private const val MAX_FAILS = 30
        private const val MAX_PENDING_VIRTUAL_DELTA = 1_000_000f
        private const val TAG = "KeyMapperInjector"
    }

    private val thread = HandlerThread("mapper-inject", Process.THREAD_PRIORITY_DISPLAY)
        .also { it.start() }
    val handler = Handler(thread.looper)
    private val shutdownRequested = java.util.concurrent.atomic.AtomicBoolean(false)

    private val pts = LinkedHashMap<Int, FloatArray>()
    // Um ACTION_UP recusado não pode apagar o estado local: o contato pode continuar ativo
    // no InputDispatcher. Mantemos esses IDs e tentamos encerrar novamente quando o Binder voltar.
    private val pendingReleaseIds = LinkedHashSet<Int>()
    private var releaseRetryScheduled = false
    private var virtualReleasePending = false
    private val retryPendingReleases = Runnable { retryPendingReleasesNow() }
    // Buffers reutilizados no caminho quente; construção e entrega são serializadas na HandlerThread.
    // Isso evita alocar arrays e objetos de coordenadas a cada amostra de movimento.
    private val pointerProperties = Array(TouchPointerPolicy.MAX_SIMULTANEOUS_POINTERS) {
        MotionEvent.PointerProperties()
    }
    private val pointerCoords = Array(TouchPointerPolicy.MAX_SIMULTANEOUS_POINTERS) {
        MotionEvent.PointerCoords()
    }
    private val relativePointerProperties = arrayOf(MotionEvent.PointerProperties())
    private val relativePointerCoords = arrayOf(MotionEvent.PointerCoords())
    private val virtualPointerIds = IntArray(TouchPointerPolicy.MAX_SIMULTANEOUS_POINTERS)
    private val virtualPointerXs = FloatArray(TouchPointerPolicy.MAX_SIMULTANEOUS_POINTERS)
    private val virtualPointerYs = FloatArray(TouchPointerPolicy.MAX_SIMULTANEOUS_POINTERS)
    // A route is pinned for the complete multi-touch contact sequence. Never switch transports
    // halfway through a gesture: doing so can duplicate DOWN or strand a contact in one backend.
    private var activeTouchRoute: TouchOutputTransport? = null
    private var downTime = 0L

    private val setDisplay = try {
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    } catch (e: Throwable) {
        null
    }

    // throttling para contatos touchscreen e para mouse relativo virtual
    private var lastMoveAtNs = 0L
    private var lastVirtualMoveAtNs = 0L
    private var pendingVirtualX = 0f
    private var pendingVirtualY = 0f
    private var virtualMoveScheduled = false
    private val flushVirtualMove = Runnable { flushVirtualMove() }
    private var moveDirty = false
    private val flushMove = Runnable { flushPendingMove() }

    // saúde do Shizuku
    @Volatile
    var broken = false
        private set
    var onFailure: (() -> Unit)? = null
    private var failStreak = 0
    private var lastProbe = 0L
    private var lastReadyCheck = 0L
    private var readinessKnown = false
    private var cachedReady = false
    private var failureReported = false
    private var displaySetterUnavailableLogged = false
    /** Display whose events could not be routed; avoids repeating reflection and failure callbacks per sample. */
    private var blockedDisplayId: Int? = null

    fun isDown(id: Int) = pts.containsKey(id)

    private fun sane(v: Float): Float = if (v.isNaN()) 0f else v.coerceIn(-1_000_000f, 1_000_000f)

    fun down(id: Int, x: Float, y: Float) {
        if (shutdownRequested.get() || pts.containsKey(id)) return
        // Não inicia outro contato enquanto o backend anterior ainda precisa confirmar a
        // liberação total do dispositivo virtual ou de algum ponteiro do InputManager.
        if (virtualReleasePending) {
            retryPendingReleasesNow()
            if (virtualReleasePending) return
        }
        if (pendingReleaseIds.isNotEmpty()) {
            retryPendingReleasesNow()
            if (pendingReleaseIds.isNotEmpty()) return
        }
        if (!TouchPointerPolicy.canStart(pts.size, id)) {
            Log.w(TAG, "Contato recusado: limite do Android de ${TouchPointerPolicy.MAX_SIMULTANEOUS_POINTERS} ponteiros simultâneos atingido")
            return
        }
        flushPendingMove(force = true)
        val now = SystemClock.uptimeMillis()
        val first = pts.isEmpty()
        if (first) downTime = now
        pts[id] = floatArrayOf(sane(x), sane(y))
        val idx = pts.keys.indexOf(id)
        val action = if (first) MotionEvent.ACTION_DOWN
        else MotionEvent.ACTION_POINTER_DOWN or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        if (!send(action, now)) {
            // Não mantenha um contato fantasma após uma rejeição do InputManager.
            pts.remove(id)
            if (pts.isEmpty()) downTime = 0L
        }
    }

    /** MOVE com throttling: atualiza a posição sempre; só injeta se passou o intervalo mínimo. */
    fun move(id: Int, x: Float, y: Float) {
        if (shutdownRequested.get() || id in pendingReleaseIds) return
        val p = pts[id] ?: return
        p[0] = sane(x)
        p[1] = sane(y)
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val sinceNs = nowNs - lastMoveAtNs
        if (sinceNs >= MIN_MOVE_INTERVAL_NS) {
            moveDirty = false
            handler.removeCallbacks(flushMove)
            lastMoveAtNs = nowNs
            send(MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis())
        } else if (!moveDirty) {
            moveDirty = true
            handler.postDelayed(flushMove, delayMsFor(MIN_MOVE_INTERVAL_NS - sinceNs))
        }
    }

    private fun delayMsFor(remainingNs: Long): Long =
        ((remainingNs + 999_999L) / 1_000_000L).coerceAtLeast(1L)

    private fun flushPendingMove(force: Boolean = false) {
        if (!moveDirty) return
        handler.removeCallbacks(flushMove)
        if (pts.isEmpty()) {
            moveDirty = false
            return
        }
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val remaining = MIN_MOVE_INTERVAL_NS - (nowNs - lastMoveAtNs)
        if (!force && lastMoveAtNs != 0L && remaining > 0L) {
            handler.postDelayed(flushMove, delayMsFor(remaining))
            return
        }
        moveDirty = false
        lastMoveAtNs = nowNs
        send(MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis())
    }

    fun up(id: Int) {
        if (virtualReleasePending || !pts.containsKey(id)) return
        flushPendingMove(force = true) // a posição final sempre chega antes do UP
        releasePointerOnce(id)
        if (pendingReleaseIds.isNotEmpty()) scheduleReleaseRetry()
    }

    /** Só remove o contato local quando o sistema aceitou o ACTION_UP correspondente. */
    private fun releasePointerOnce(id: Int) {
        if (!pts.containsKey(id)) {
            pendingReleaseIds.remove(id)
            return
        }
        val now = SystemClock.uptimeMillis()
        val idx = pts.keys.indexOf(id)
        val action = if (pts.size == 1) MotionEvent.ACTION_UP
        else MotionEvent.ACTION_POINTER_UP or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        if (send(action, now)) {
            pts.remove(id)
            pendingReleaseIds.remove(id)
            if (pts.isEmpty()) downTime = 0L
        } else if (pts.containsKey(id)) {
            pendingReleaseIds.add(id)
        } else {
            // The virtual device may have been destroyed after a kernel write failure. Its
            // removal cancels contacts at InputReader; do not retain an impossible local retry.
            pendingReleaseIds.remove(id)
        }
    }

    private fun scheduleReleaseRetry() {
        if (shutdownRequested.get() || releaseRetryScheduled ||
            (pendingReleaseIds.isEmpty() && !virtualReleasePending)) return
        releaseRetryScheduled = handler.postDelayed(retryPendingReleases, RELEASE_RETRY_DELAY_MS)
    }

    private fun retryPendingReleasesNow() {
        releaseRetryScheduled = false
        handler.removeCallbacks(retryPendingReleases)
        if (shutdownRequested.get()) return
        if (virtualReleasePending) {
            if (tryReleaseVirtualTouch()) {
                virtualReleasePending = false
                clearTouchContacts()
            } else {
                scheduleReleaseRetry()
            }
            return
        }
        if (pendingReleaseIds.isEmpty()) return
        if (activeTouchRoute == TouchOutputTransport.UINPUT && virtualTouchOutput?.isAvailable() != true) {
            // A closed uinput fd removes its virtual input device; the kernel cancels its active
            // contacts. Clear the matching app-side bookkeeping rather than retrying dead slots.
            pts.clear()
            pendingReleaseIds.clear()
            activeTouchRoute = null
            downTime = 0L
            return
        }
        for (id in pendingReleaseIds.toList()) releasePointerOnce(id)
        if (pendingReleaseIds.isNotEmpty()) scheduleReleaseRetry()
        else if (pts.isEmpty()) activeTouchRoute = null
    }

    /**
     * Transfere a visão para um novo ponto de referência sem mover o mouse físico.
     *
     * [nonstop] usa handover sobreposto: o novo contato recebe ACTION_POINTER_DOWN enquanto o
     * antigo ainda está ativo; depois o antigo recebe ACTION_POINTER_UP. Assim, nunca existe um
     * intervalo com zero contatos ativos. Jogos que tratam o segundo dedo como gesto multitouch
     * podem não aceitar este modo; por isso ele é opcional e o padrão continua usando ACTION_UP
     * seguido de ACTION_DOWN.
     *
     * Retorna true somente quando o novo contato está ativo e o antigo foi liberado. Uma falha não
     * pode ser interpretada pelo MappingEngine como troca concluída.
     */
    fun swap(
        oldId: Int,
        newId: Int,
        ex: Float,
        ey: Float,
        nx: Float,
        ny: Float,
        nonstop: Boolean = false
    ): Boolean {
        if (shutdownRequested.get() || oldId == newId || !pts.containsKey(oldId) ||
            oldId in pendingReleaseIds || newId in pts || virtualReleasePending
        ) return false

        move(oldId, ex, ey)
        // Se a lista já usa todos os slots aceitos, não existe espaço para o ponteiro
        // sobreposto. Degrada somente esse handover para UP/DOWN clássico em vez de abortar a
        // visão inteira; falhas reais de envio continuam sendo tratadas como falha.
        val canOverlap = TouchHandoffPolicy.useOverlappedHandoff(nonstop, pts.size, newId)
        if (canOverlap) {
            // O DOWN adicional mantém o gesto global ativo durante a troca de identificador.
            down(newId, nx, ny)
            if (!pts.containsKey(newId)) return false
            up(oldId)
            if (pts.containsKey(oldId)) {
                // Não deixe os dois IDs ativos se a liberação antiga falhar.
                up(newId)
                return false
            }
            return pts.containsKey(newId)
        }

        up(oldId)
        if (pts.containsKey(oldId)) return false
        down(newId, nx, ny)
        return !pts.containsKey(oldId) && pts.containsKey(newId)
    }

    fun releaseAll() {
        // Nunca reproduz movimento acumulado depois de pausa/perda de foco.
        handler.removeCallbacks(flushVirtualMove)
        pendingVirtualX = 0f
        pendingVirtualY = 0f
        virtualMoveScheduled = false
        lastVirtualMoveAtNs = 0L
        // Cancelamento/pausa não deve reproduzir o último MOVE pendente.
        handler.removeCallbacks(flushMove)
        moveDirty = false
        handler.removeCallbacks(retryPendingReleases)
        releaseRetryScheduled = false

        if (activeTouchRoute == TouchOutputTransport.UINPUT) {
            if (tryReleaseVirtualTouch()) {
                virtualReleasePending = false
                clearTouchContacts()
            } else {
                virtualReleasePending = true
                Log.w(TAG, "Aguardando confirmação de liberação total do touchscreen virtual")
                scheduleReleaseRetry()
            }
            return
        }

        pts.keys.toList().forEach { up(it) }
        if (pendingReleaseIds.isNotEmpty()) {
            Log.w(TAG, "${pendingReleaseIds.size} contato(s) aguardando confirmação de ACTION_UP")
            scheduleReleaseRetry()
        } else {
            activeTouchRoute = null
        }
    }

    /** Retira os contatos usando o cancelamento atômico do backend virtual. */
    private fun tryReleaseVirtualTouch(): Boolean {
        val output = virtualTouchOutput ?: return true
        if (!output.isAvailable()) return true // UI_DEV_DESTROY/fechamento remove o dispositivo e seus slots.
        val accepted = runCatching { output.releaseAll() }.getOrDefault(false)
        return accepted || !output.isAvailable()
    }

    private fun clearTouchContacts() {
        pts.clear()
        pendingReleaseIds.clear()
        downTime = 0L
        activeTouchRoute = null
        virtualReleasePending = false
        moveDirty = false
        handler.removeCallbacks(flushMove)
        handler.removeCallbacks(retryPendingReleases)
        releaseRetryScheduled = false
    }

    /** Aborta uma sequência uinput se o destino deixar de ser roteável ou o Binder falhar. */
    private fun abortActiveUinputGesture(reason: String) {
        if (activeTouchRoute != TouchOutputTransport.UINPUT && !virtualReleasePending) return
        if (tryReleaseVirtualTouch()) {
            clearTouchContacts()
        } else {
            virtualReleasePending = true
            Log.w(TAG, "$reason; aguardando liberação uinput")
            scheduleReleaseRetry()
        }
    }

    /**
     * Acumula deltas relativos. O limitador de frequência agrupa movimentos em vez de descartá-los,
     * preservando a distância total mesmo quando o mouse entrega eventos acima de 120 Hz.
     * Chamado na thread de [handler].
     */
    fun injectVirtualMove(x: Float, y: Float): Boolean {
        if (shutdownRequested.get() || !x.isFinite() || !y.isFinite() || (x == 0f && y == 0f)) return false
        val nowMs = SystemClock.uptimeMillis()
        if (!ensureInputManagerReady(nowMs)) {
            pendingVirtualX = 0f
            pendingVirtualY = 0f
            virtualMoveScheduled = false
            handler.removeCallbacks(flushVirtualMove)
            return false
        }
        pendingVirtualX = boundedVirtualSum(pendingVirtualX, x)
        pendingVirtualY = boundedVirtualSum(pendingVirtualY, y)
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val elapsed = nowNs - lastVirtualMoveAtNs
        if (lastVirtualMoveAtNs == 0L || elapsed >= MIN_VIRTUAL_MOVE_INTERVAL_NS) {
            flushVirtualMove()
        } else if (!virtualMoveScheduled) {
            virtualMoveScheduled = true
            handler.postDelayed(
                flushVirtualMove,
                delayMsFor(MIN_VIRTUAL_MOVE_INTERVAL_NS - elapsed)
            )
        }
        return true // aceito na fila local; não significa entrega ao aplicativo de destino
    }

    /** Deltas relativos são acumulados no handler. Saturar só protege contra entradas corrompidas; eventos normais preservam a soma. */
    private fun boundedVirtualSum(current: Float, delta: Float): Float {
        val sum = current.toDouble() + delta.toDouble()
        return sum.coerceIn(-MAX_PENDING_VIRTUAL_DELTA.toDouble(), MAX_PENDING_VIRTUAL_DELTA.toDouble()).toFloat()
    }

    private fun flushVirtualMove() {
        handler.removeCallbacks(flushVirtualMove)
        virtualMoveScheduled = false
        if (pendingVirtualX == 0f && pendingVirtualY == 0f) return
        val nowMs = SystemClock.uptimeMillis()
        if (broken && !probe(nowMs)) {
            pendingVirtualX = 0f
            pendingVirtualY = 0f
            return
        }
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val elapsed = nowNs - lastVirtualMoveAtNs
        if (lastVirtualMoveAtNs != 0L && elapsed < MIN_VIRTUAL_MOVE_INTERVAL_NS) {
            virtualMoveScheduled = true
            handler.postDelayed(
                flushVirtualMove,
                delayMsFor(MIN_VIRTUAL_MOVE_INTERVAL_NS - elapsed)
            )
            return
        }
        val dx = pendingVirtualX
        val dy = pendingVirtualY
        pendingVirtualX = 0f
        pendingVirtualY = 0f
        lastVirtualMoveAtNs = nowNs
        sendVirtualMove(dx, dy, nowMs)
    }

    private fun sendVirtualMove(x: Float, y: Float, now: Long) {
        if (x == 0f && y == 0f) return
        relativePointerProperties[0].apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        relativePointerCoords[0].apply {
            // Espelha o formato gerado pelo InputFlinger em Pointer Capture:
            // X/Y e AXIS_RELATIVE_X/Y carregam o mesmo deslocamento relativo.
            this.x = x
            this.y = y
            setAxisValue(MotionEvent.AXIS_RELATIVE_X, x)
            setAxisValue(MotionEvent.AXIS_RELATIVE_Y, y)
        }
        val ev = MotionEvent.obtain(
            now, now, MotionEvent.ACTION_MOVE, 1, relativePointerProperties, relativePointerCoords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE_RELATIVE, 0
        )
        deliver(ev, now)
    }

    /** Compatibilidade com chamadas antigas. */
    fun relative(dx: Float, dy: Float): Boolean = injectVirtualMove(dx, dy)

    fun shutdown() {
        if (!shutdownRequested.compareAndSet(false, true)) return
        val accepted = handler.post {
            try {
                releaseAll()
            } catch (t: Throwable) {
                Log.e(TAG, "Falha liberando contatos durante o encerramento do injetor", t)
            } finally {
                handler.removeCallbacks(retryPendingReleases)
                if (pendingReleaseIds.isNotEmpty()) {
                    Log.e(TAG, "Encerrando com ${pendingReleaseIds.size} contato(s) sem confirmação de ACTION_UP; Shizuku/InputManager indisponível")
                }
                thread.quitSafely()
            }
        }
        if (!accepted) {
            // Se o Looper já não aceita trabalho, não deixe o HandlerThread vivo indefinidamente.
            Log.w(TAG, "Looper do injetor já encerrado; finalizando HandlerThread diretamente")
            thread.quitSafely()
        }
    }

    // ---------- envio ----------

    /**
     * Use uinput apenas no display principal. O kernel InputReader associa um dispositivo
     * touchscreen virtual a um viewport físico; esse mecanismo não recebe displayId do app.
     * Para DeX/monitor externo, o caminho InputManager continua sendo o único roteável.
     */
    private fun deliverTouchscreen(event: MotionEvent, targetDisplayId: Int): Boolean {
        val action = event.actionMasked
        val route = activeTouchRoute ?: run {
            val selected = if (action == MotionEvent.ACTION_DOWN) {
                TouchOutputRoutingPolicy.select(targetDisplayId, virtualTouchOutput?.isAvailable() == true)
            } else {
                TouchOutputTransport.INPUT_MANAGER
            }
            if (action == MotionEvent.ACTION_DOWN) activeTouchRoute = selected
            selected
        }

        if (!TouchOutputRoutingPolicy.canContinue(route, targetDisplayId)) {
            // Display mudou durante um gesto iniciado no touchscreen virtual: liberar e abortar,
            // nunca encaminhar o restante do gesto para uma tela diferente.
            abortActiveUinputGesture("O display mudou durante um gesto uinput")
            reportFailureOnce("O display mudou durante um gesto uinput; gesto cancelado")
            return false
        }

        val accepted = if (route == TouchOutputTransport.UINPUT) {
            sendVirtualTouchFrame(event)
        } else {
            injector.inject(event)
        }

        if (!accepted && route == TouchOutputTransport.UINPUT) {
            // Qualquer rejeição pode indicar divergência entre os slots do kernel e os do app.
            // Cancela a sequência inteira; nunca tenta continuar com um estado multitoque parcial.
            abortActiveUinputGesture("O backend uinput rejeitou um quadro touchscreen")
        } else if (!accepted && route == TouchOutputTransport.INPUT_MANAGER && action == MotionEvent.ACTION_DOWN) {
            activeTouchRoute = null
        } else if (accepted && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
            activeTouchRoute = null
        }
        return accepted
    }

    /** Envia o quadro inteiro: IDs permanecem como slots estáveis do protocolo multitouch B. */
    private fun sendVirtualTouchFrame(event: MotionEvent): Boolean {
        val output = virtualTouchOutput ?: return false
        val count = event.pointerCount
        if (count !in 1..TouchPointerPolicy.MAX_SIMULTANEOUS_POINTERS) return false
        val geometry = runCatching { displaySize() }.getOrNull() ?: return false
        val (width, height) = geometry
        if (!TouchOutputGeometryPolicy.isUsable(width, height)) return false
        for (index in 0 until count) {
            val point = TouchOutputGeometryPolicy.normalize(
                event.getX(index), event.getY(index), width, height
            ) ?: return false
            virtualPointerIds[index] = event.getPointerId(index)
            virtualPointerXs[index] = point.x
            virtualPointerYs[index] = point.y
        }
        return output.sendFrame(
            event.actionMasked,
            event.actionIndex,
            count,
            virtualPointerIds,
            virtualPointerXs,
            virtualPointerYs
        )
    }

    private fun reportFailureOnce(message: String) {
        if (failureReported) return
        failureReported = true
        Log.e(TAG, message)
        try {
            onFailure?.invoke()
        } catch (t: Throwable) {
            Log.e(TAG, "Callback de falha do injetor também falhou", t)
        }
    }

    private fun send(action: Int, now: Long): Boolean {
        // A prontidão é decidida no deliver(): uinput touchscreen não deve depender de
        // INJECT_EVENTS/InputManager; SOURCE_MOUSE_RELATIVE e o fallback ainda dependem.
        val n = pts.size
        if (n == 0 || n > pointerProperties.size) return false
        var i = 0
        for ((id, xy) in pts) {
            val props = pointerProperties[i]
            props.id = id
            props.toolType = MotionEvent.TOOL_TYPE_FINGER
            val coords = pointerCoords[i]
            coords.x = xy[0]
            coords.y = xy[1]
            coords.pressure = 1f
            coords.size = 1f
            i++
        }
        val ev = MotionEvent.obtain(
            downTime, now, action, n, pointerProperties, pointerCoords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        return deliver(ev, now)
    }

    /** Injeta e SEMPRE recicla o evento (try/finally); falhas do transporte não derrubam o serviço. */
    private fun deliver(ev: MotionEvent, now: Long): Boolean {
        var ok = false
        var usesInputManager = true
        val startedNs = SystemClock.elapsedRealtimeNanos()
        try {
            val targetDisplayId = try {
                displayId()
            } catch (t: Throwable) {
                abortActiveUinputGesture("Falha consultando o display durante o gesto")
                reportFailureOnce("Não foi possível identificar o display de destino; evento não encaminhado")
                InputDiagnostics.recordInjection(false, SystemClock.elapsedRealtimeNanos() - startedNs)
                return false
            }
            if (targetDisplayId < 0) {
                abortActiveUinputGesture("Display de destino inválido ($targetDisplayId)")
                reportFailureOnce("Display de destino inválido ($targetDisplayId); evento não encaminhado")
                InputDiagnostics.recordInjection(false, SystemClock.elapsedRealtimeNanos() - startedNs)
                return false
            }

            val useVirtualTouch = isVirtualTouchCandidate(ev, targetDisplayId)
            usesInputManager = !useVirtualTouch
            if (useVirtualTouch) {
                // Não consultar INJECT_EVENTS nem preparar IInputManager neste caminho. O
                // UserService controla /dev/uinput diretamente e constitui um transporte distinto.
                if (virtualTouchOutput?.isAvailable() != true) {
                    abortActiveUinputGesture("Dispositivo touchscreen uinput deixou de estar disponível")
                    reportFailureOnce("uinput indisponível; o gesto atual foi cancelado")
                    InputDiagnostics.recordInjection(false, SystemClock.elapsedRealtimeNanos() - startedNs)
                    return false
                }
            } else {
                // Mouse relativo e saída de gestos via InputManager exigem INJECT_EVENTS.
                if (!ensureInputManagerReady(now)) {
                    clearUnstartedInputManagerRoute(ev)
                    InputDiagnostics.recordInjection(false, SystemClock.elapsedRealtimeNanos() - startedNs)
                    return false
                }

                if (blockedDisplayId == targetDisplayId) {
                    clearUnstartedInputManagerRoute(ev)
                    InputDiagnostics.recordInjection(false, SystemClock.elapsedRealtimeNanos() - startedNs)
                    return false
                }
                if (targetDisplayId != 0) {
                    val setter = setDisplay
                    if (setter == null) {
                        blockedDisplayId = targetDisplayId
                        clearUnstartedInputManagerRoute(ev)
                        reportFailureOnce("Este Android não expõe setDisplayId; recusada injeção no display externo para evitar envio à tela errada")
                        InputDiagnostics.recordInjection(false, SystemClock.elapsedRealtimeNanos() - startedNs)
                        return false
                    }
                    try {
                        setter.invoke(ev, targetDisplayId)
                    } catch (t: Throwable) {
                        if (!displaySetterUnavailableLogged) {
                            displaySetterUnavailableLogged = true
                            Log.w(TAG, "Não foi possível direcionar o evento ao display $targetDisplayId; envio cancelado", t)
                        }
                        blockedDisplayId = targetDisplayId
                        clearUnstartedInputManagerRoute(ev)
                        reportFailureOnce("Falha ao selecionar display $targetDisplayId; evento não encaminhado")
                        InputDiagnostics.recordInjection(false, SystemClock.elapsedRealtimeNanos() - startedNs)
                        return false
                    }
                }
                blockedDisplayId = null
            }

            ok = if (ev is MotionEvent && ev.source == InputDevice.SOURCE_TOUCHSCREEN) {
                deliverTouchscreen(ev, targetDisplayId)
            } else {
                injector.inject(ev)
            }
        } catch (t: Throwable) {
            abortActiveUinputGesture("Exceção durante o transporte touchscreen")
            clearUnstartedInputManagerRoute(ev)
            ok = false
            Log.w(TAG, "Falha ao injetar evento de entrada", t)
        } finally {
            ev.recycle()
        }
        InputDiagnostics.recordInjection(ok, SystemClock.elapsedRealtimeNanos() - startedNs)
        if (usesInputManager) {
            if (ok) {
                failStreak = 0
            } else if (++failStreak >= MAX_FAILS && !broken) {
                broken = true
                lastProbe = now
                reportFailureOnce("Transporte InputManager interrompido após $failStreak falhas consecutivas")
            }
        }
        return ok
    }

    /** Decide o transporte antes da sondagem de permissões, para manter uinput independente. */
    private fun isVirtualTouchCandidate(event: InputEvent, targetDisplayId: Int): Boolean {
        if (event !is MotionEvent || event.source != InputDevice.SOURCE_TOUCHSCREEN) return false
        // Decide and pin the transport exactly once, before the permission preflight. A gesture
        // never changes backend halfway through if uinput availability changes between frames.
        if (activeTouchRoute == null && event.actionMasked == MotionEvent.ACTION_DOWN) {
            val geometryAvailable = runCatching { displaySize() }
                .getOrNull()
                ?.let { (width, height) -> TouchOutputGeometryPolicy.isUsable(width, height) }
                ?: false
            activeTouchRoute = TouchOutputRoutingPolicy.select(
                targetDisplayId,
                uinputAvailable = virtualTouchOutput?.isAvailable() == true && geometryAvailable
            )
        }
        return activeTouchRoute == TouchOutputTransport.UINPUT
    }

    private fun clearUnstartedInputManagerRoute(event: InputEvent) {
        if (event is MotionEvent && event.source == InputDevice.SOURCE_TOUCHSCREEN &&
            event.actionMasked == MotionEvent.ACTION_DOWN &&
            activeTouchRoute == TouchOutputTransport.INPUT_MANAGER) {
            activeTouchRoute = null
        }
    }

    /** Sondagem compartilhada apenas pelos transportes que realmente usam InputManager. */
    private fun ensureInputManagerReady(now: Long): Boolean {
        if (broken && !probe(now)) return false
        if (!readinessKnown || now - lastReadyCheck >= 500L) {
            lastReadyCheck = now
            readinessKnown = true
            cachedReady = try {
                injector.isReady()
            } catch (t: Throwable) {
                Log.w(TAG, "Falha verificando Shizuku/InputManager antes da injeção", t)
                false
            }
        }
        if (cachedReady) return true
        broken = true
        lastProbe = now
        reportFailureOnce("Shizuku sem autorização efetiva para INJECT_EVENTS ou InputManager indisponível")
        return false
    }

    /** A cada 1 s verifica se o Shizuku voltou; se sim, retoma. */
    private fun probe(now: Long): Boolean {
        if (now - lastProbe >= 1000L) {
            lastProbe = now
            val ready = try {
                injector.isReady()
            } catch (t: Throwable) {
                false
            }
            if (ready) {
                val wasBroken = broken
                broken = false
                failStreak = 0
                readinessKnown = true
                cachedReady = true
                failureReported = false
                if (wasBroken) Log.i(TAG, "Shizuku disponível novamente; injeção reativada")
            } else {
                readinessKnown = true
                cachedReady = false
            }
        }
        return !broken
    }
}
