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
    private val displayId: () -> Int
) {
    companion object {
        private const val MIN_MOVE_INTERVAL_NS = 8_333_333L
        private const val MIN_VIRTUAL_MOVE_INTERVAL_NS = 8_333_333L
        private const val MAX_FAILS = 30
        private const val MAX_PENDING_VIRTUAL_DELTA = 1_000_000f
        private const val TAG = "KeyMapperInjector"
    }

    private val thread = HandlerThread("mapper-inject", Process.THREAD_PRIORITY_DISPLAY)
        .also { it.start() }
    val handler = Handler(thread.looper)

    private val pts = LinkedHashMap<Int, FloatArray>()
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
        if (pts.containsKey(id)) return
        flushPendingMove(force = true)
        val now = SystemClock.uptimeMillis()
        val first = pts.isEmpty()
        if (first) downTime = now
        pts[id] = floatArrayOf(sane(x), sane(y))
        val idx = pts.keys.indexOf(id)
        val action = if (first) MotionEvent.ACTION_DOWN
        else MotionEvent.ACTION_POINTER_DOWN or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        send(action, now)
    }

    /** MOVE com throttling: atualiza a posição sempre; só injeta se passou o intervalo mínimo. */
    fun move(id: Int, x: Float, y: Float) {
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
        if (!pts.containsKey(id)) return
        flushPendingMove(force = true) // a posição final sempre chega antes do UP
        val now = SystemClock.uptimeMillis()
        val idx = pts.keys.indexOf(id)
        val action = if (pts.size == 1) MotionEvent.ACTION_UP
        else MotionEvent.ACTION_POINTER_UP or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        send(action, now)
        pts.remove(id)
    }

    /**
     * Reancoragem touchscreen: leva o contato antigo até (ex, ey), envia ACTION_UP e em seguida
     * ACTION_DOWN no novo ponto (nx, ny), na mesma thread. Preserva a ordem e não espera um frame
     * entre os eventos, mas ainda é uma troca de contato — não equivale a um fluxo de mouse relativo
     * nativo e alguns jogos podem perceber a pequena interrupção. UP/DOWN não são descartados pelo
     * limitador de frequência dos movimentos.
     */
    fun swap(oldId: Int, newId: Int, ex: Float, ey: Float, nx: Float, ny: Float) {
        move(oldId, ex, ey)
        up(oldId)
        down(newId, nx, ny)
    }

    fun releaseAll() {
        // Nunca reproduz movimento acumulado depois de pausa/perda de foco.
        handler.removeCallbacks(flushVirtualMove)
        pendingVirtualX = 0f
        pendingVirtualY = 0f
        virtualMoveScheduled = false
        lastVirtualMoveAtNs = 0L
        // Cancelamento/pausa não deve reproduzir o último MOVE pendente. Apenas encerra
        // os contatos na posição já conhecida pelo injetor.
        handler.removeCallbacks(flushMove)
        moveDirty = false
        pts.keys.toList().forEach { up(it) }
    }

    /**
     * Acumula deltas relativos. O limitador de frequência agrupa movimentos em vez de descartá-los,
     * preservando a distância total mesmo quando o mouse entrega eventos acima de 120 Hz.
     * Chamado na thread de [handler].
     */
    fun injectVirtualMove(x: Float, y: Float): Boolean {
        if (!x.isFinite() || !y.isFinite() || (x == 0f && y == 0f)) return false
        val nowMs = SystemClock.uptimeMillis()
        if (broken && !probe(nowMs)) {
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
        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                // Espelha o formato gerado pelo InputFlinger em Pointer Capture:
                // X/Y e AXIS_RELATIVE_X/Y carregam o mesmo deslocamento relativo.
                this.x = x
                this.y = y
                setAxisValue(MotionEvent.AXIS_RELATIVE_X, x)
                setAxisValue(MotionEvent.AXIS_RELATIVE_Y, y)
            }
        )
        val ev = MotionEvent.obtain(
            now, now, MotionEvent.ACTION_MOVE, 1, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE_RELATIVE, 0
        )
        deliver(ev, now)
    }

    /** Compatibilidade com chamadas antigas. */
    fun relative(dx: Float, dy: Float): Boolean = injectVirtualMove(dx, dy)

    fun shutdown() {
        handler.post {
            releaseAll()
            thread.quitSafely()
        }
    }

    // ---------- envio ----------

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

    private fun send(action: Int, now: Long) {
        if (broken && !probe(now)) return
        val n = pts.size
        if (n == 0) return
        val props = Array(n) { MotionEvent.PointerProperties() }
        val coords = Array(n) { MotionEvent.PointerCoords() }
        var i = 0
        for ((id, xy) in pts) {
            props[i].id = id
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER
            coords[i].x = xy[0]
            coords[i].y = xy[1]
            coords[i].pressure = 1f
            coords[i].size = 1f
            i++
        }
        val ev = MotionEvent.obtain(
            downTime, now, action, n, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        deliver(ev, now)
    }

    /** Injeta e SEMPRE recicla o evento (try/finally); falhas do Shizuku não derrubam nada. */
    private fun deliver(ev: MotionEvent, now: Long) {
        var ok = false
        val startedNs = SystemClock.elapsedRealtimeNanos()
        try {
            // Verifica conexão/permissão antes da injeção, sem chamar pingBinder em cada evento.
            if (!readinessKnown || now - lastReadyCheck >= 500L) {
                lastReadyCheck = now
                readinessKnown = true
                cachedReady = try { injector.isReady() } catch (t: Throwable) {
                    Log.w(TAG, "Falha verificando Shizuku antes da injeção", t)
                    false
                }
            }
            if (!cachedReady) {
                broken = true
                lastProbe = now
                reportFailureOnce("Shizuku desconectado ou sem permissão; evento descartado")
                InputDiagnostics.recordInjection(false, SystemClock.elapsedRealtimeNanos() - startedNs)
                return
            }
            val targetDisplayId = try {
                displayId()
            } catch (t: Throwable) {
                reportFailureOnce("Não foi possível identificar o display de destino; evento não encaminhado")
                InputDiagnostics.recordInjection(false, SystemClock.elapsedRealtimeNanos() - startedNs)
                return
            }
            if (targetDisplayId < 0) {
                reportFailureOnce("Display de destino inválido ($targetDisplayId); evento não encaminhado")
                InputDiagnostics.recordInjection(false, SystemClock.elapsedRealtimeNanos() - startedNs)
                return
            }
            if (blockedDisplayId == targetDisplayId) {
                InputDiagnostics.recordInjection(false, SystemClock.elapsedRealtimeNanos() - startedNs)
                return
            }
            if (targetDisplayId != 0) {
                val setter = setDisplay
                if (setter == null) {
                    blockedDisplayId = targetDisplayId
                    reportFailureOnce("Este Android não expõe setDisplayId; recusada injeção no display externo para evitar envio à tela errada")
                    InputDiagnostics.recordInjection(false, SystemClock.elapsedRealtimeNanos() - startedNs)
                    return
                }
                try {
                    setter.invoke(ev, targetDisplayId)
                } catch (t: Throwable) {
                    if (!displaySetterUnavailableLogged) {
                        displaySetterUnavailableLogged = true
                        Log.w(TAG, "Não foi possível direcionar o evento ao display $targetDisplayId; envio cancelado", t)
                    }
                    blockedDisplayId = targetDisplayId
                    reportFailureOnce("Falha ao selecionar display $targetDisplayId; evento não encaminhado")
                    InputDiagnostics.recordInjection(false, SystemClock.elapsedRealtimeNanos() - startedNs)
                    return
                }
            }
            blockedDisplayId = null
            ok = injector.inject(ev)
        } catch (t: Throwable) {
            ok = false
            Log.w(TAG, "Falha ao injetar evento de entrada", t)
        } finally {
            ev.recycle()
        }
        InputDiagnostics.recordInjection(ok, SystemClock.elapsedRealtimeNanos() - startedNs)
        if (ok) {
            failStreak = 0
        } else if (++failStreak >= MAX_FAILS && !broken) {
            broken = true            // para o loop suavemente: deixa de tentar até o Shizuku voltar
            lastProbe = now
            reportFailureOnce("Injeção interrompida após $failStreak falhas consecutivas")
        }
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
