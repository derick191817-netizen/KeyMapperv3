package com.example.keymapper.injection

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent

/**
 * Monta eventos multitoque e injeta (via Shizuku). Todos os métodos devem ser chamados na thread de
 * [handler] (prioridade URGENT_DISPLAY).
 *
 * Estabilidade:
 *  - THROTTLING: ACTION_MOVE sai no máximo a cada [MIN_MOVE_INTERVAL_MS] (8 ms = 120 Hz). Como as posições são
 *    absolutas, um MOVE adiado não perde movimento: o último é reenviado em seguida e SEMPRE antes de um UP/DOWN.
 *  - MEMÓRIA: todo MotionEvent.obtain() é seguido de recycle() em try/finally.
 *  - SHIZUKU: a injeção é protegida por try/catch (Throwable). Se falhar [MAX_FAILS] vezes seguidas, o injetor
 *    "quebra" (para de tentar, sem travar nada), avisa por [onFailure] e volta sozinho quando o Shizuku responder.
 *
 * Coordenadas VIRTUAIS: não são limitadas ao tamanho da tela (só NaN e ±1.000.000 px são barrados).
 */
class TouchInjector(
    private val injector: Injector,
    private val displayId: () -> Int,
    private val extraDelayMs: Long
) {
    companion object {
        const val MIN_MOVE_INTERVAL_MS = 8L
        private const val MAX_FAILS = 30
    }

    private val thread = HandlerThread("mapper-inject", Process.THREAD_PRIORITY_URGENT_DISPLAY)
        .also { it.start() }
    val handler = Handler(thread.looper)

    private val pts = LinkedHashMap<Int, FloatArray>()
    private var downTime = 0L

    private val setDisplay = try {
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    } catch (e: Throwable) {
        null
    }

    // throttling
    private var lastMoveAt = 0L
    private var moveDirty = false
    private val flushMove = Runnable { flushPendingMove() }

    // saúde do Shizuku
    @Volatile
    var broken = false
        private set
    var onFailure: (() -> Unit)? = null
    private var failStreak = 0
    private var lastProbe = 0L
    private var skipSleep = false

    fun isDown(id: Int) = pts.containsKey(id)

    private fun sane(v: Float): Float = if (v.isNaN()) 0f else v.coerceIn(-1_000_000f, 1_000_000f)

    fun down(id: Int, x: Float, y: Float) {
        if (pts.containsKey(id)) return
        flushPendingMove()
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
        val now = SystemClock.uptimeMillis()
        val since = now - lastMoveAt
        if (since >= MIN_MOVE_INTERVAL_MS) {
            moveDirty = false
            handler.removeCallbacks(flushMove)
            lastMoveAt = now
            send(MotionEvent.ACTION_MOVE, now)
        } else if (!moveDirty) {
            moveDirty = true
            handler.postDelayed(flushMove, MIN_MOVE_INTERVAL_MS - since) // envia a última posição logo depois
        }
    }

    private fun flushPendingMove() {
        if (!moveDirty) return
        moveDirty = false
        handler.removeCallbacks(flushMove)
        if (pts.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        lastMoveAt = now
        send(MotionEvent.ACTION_MOVE, now)
    }

    fun up(id: Int) {
        if (!pts.containsKey(id)) return
        flushPendingMove() // a posição final sempre chega antes do UP
        val now = SystemClock.uptimeMillis()
        val idx = pts.keys.indexOf(id)
        val action = if (pts.size == 1) MotionEvent.ACTION_UP
        else MotionEvent.ACTION_POINTER_UP or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        send(action, now)
        pts.remove(id)
    }

    /**
     * Troca de dedo SEM intervalo: leva o dedo antigo até (ex, ey), solta (ACTION_UP) e já toca (ACTION_DOWN)
     * o novo em (nx, ny), tudo em sequência na mesma chamada (bem abaixo de 8 ms; sem a folga de 5 ms
     * de aparelhos fracos entre eles).
     */
    fun swap(oldId: Int, newId: Int, ex: Float, ey: Float, nx: Float, ny: Float) {
        skipSleep = true
        try {
            move(oldId, ex, ey)
            up(oldId)
            down(newId, nx, ny)
        } finally {
            skipSleep = false
        }
    }

    fun releaseAll() {
        flushPendingMove()
        pts.keys.toList().forEach { up(it) }
    }

    /**
     * EXPERIMENTAL: injeta um movimento de mouse RELATIVO (AXIS_RELATIVE_X/Y, SOURCE_MOUSE_RELATIVE).
     * Só tem efeito em jogos que leem mouse; jogos controlados por toque ignoram esse evento.
     */
    fun relative(dx: Float, dy: Float) {
        if (broken && !probe(SystemClock.uptimeMillis())) return
        val now = SystemClock.uptimeMillis()
        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = dx
                y = dy
                setAxisValue(MotionEvent.AXIS_RELATIVE_X, dx)
                setAxisValue(MotionEvent.AXIS_RELATIVE_Y, dy)
            }
        )
        val ev = MotionEvent.obtain(
            now, now, MotionEvent.ACTION_MOVE, 1, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE_RELATIVE, 0
        )
        deliver(ev, now)
    }

    fun shutdown() {
        handler.post {
            releaseAll()
            thread.quitSafely()
        }
    }

    // ---------- envio ----------

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
        try {
            try {
                setDisplay?.invoke(ev, displayId())
            } catch (e: Throwable) {
            }
            ok = injector.inject(ev)
        } catch (t: Throwable) {
            ok = false
        } finally {
            ev.recycle()
        }
        if (ok) {
            failStreak = 0
        } else if (++failStreak >= MAX_FAILS && !broken) {
            broken = true            // para o loop suavemente: deixa de tentar até o Shizuku voltar
            lastProbe = now
            onFailure?.invoke()
        }
        if (extraDelayMs > 0 && !skipSleep) SystemClock.sleep(extraDelayMs)
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
                broken = false
                failStreak = 0
            }
        }
        return !broken
    }
}
