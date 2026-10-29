import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
package com.example.keymapper.diagnostics

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/** Métricas locais em memória; não grava teclas individuais nem dados pessoais. */
object InputDiagnostics {
    private const val SAMPLE_COUNT = 128
    private val lock = Any()
    private val mouseMoves = AtomicLong(0)
    private val keyDowns = AtomicLong(0)
    private val keyUps = AtomicLong(0)
    private val buttonTransitions = AtomicLong(0)
    private val injectionSuccesses = AtomicLong(0)
    private val injectionFailures = AtomicLong(0)
    private val startedAt = SystemClock.elapsedRealtime()
    private val injectionDurations = LongArray(SAMPLE_COUNT)
    private var durationCount = 0
    private var durationIndex = 0
    private var lastDx = 0f
    private var lastDy = 0f
    private var captured = false
    private var windowFocused = false
    private var mode = "INACTIVE"
    private var lastTransition = "Inicializado"

    fun recordMouse(dx: Float, dy: Float) {
        mouseMoves.incrementAndGet()
        synchronized(lock) { lastDx = dx; lastDy = dy }
    }

    fun recordKey(down: Boolean) {
        if (down) keyDowns.incrementAndGet() else keyUps.incrementAndGet()
    }

    fun recordButton() { buttonTransitions.incrementAndGet() }

    fun setCapture(isCaptured: Boolean, hasFocus: Boolean, reason: String) {
        synchronized(lock) {
            captured = isCaptured
            windowFocused = hasFocus
            mode = when {
                !hasFocus -> "SUSPENDED"
                isCaptured -> "POINTER_CAPTURED"
                else -> "POINTER_RELEASED"
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

    fun recordInjection(success: Boolean, durationNs: Long) {
        if (success) injectionSuccesses.incrementAndGet() else injectionFailures.incrementAndGet()
        synchronized(lock) {
            injectionDurations[durationIndex] = durationNs.coerceAtLeast(0L)
            durationIndex = (durationIndex + 1) % SAMPLE_COUNT
            durationCount = (durationCount + 1).coerceAtMost(SAMPLE_COUNT)
        }
    }

    fun summary(): String {
        val snap = snapshot()
        return "${snap.mode}; foco=${snap.windowFocused}; captura=${snap.captured}; " +
            "movimentos=${snap.mouseMoves}; injeções=${snap.injectionSuccesses}/${snap.injectionFailures}; " +
            "último Δ=(${snap.lastDx}, ${snap.lastDy})"
    }

    fun report(): String {
        val s = snapshot()
        return buildString {
            appendLine("DIAGNÓSTICO DE ENTRADA — KeyMapper")
            appendLine("Tempo de processo: ${s.uptimeSeconds} s")
            appendLine("Estado: ${s.mode}; janela focada=${s.windowFocused}; captura=${s.captured}")
            appendLine("Última transição: ${s.lastTransition}")
            appendLine("Movimentos de mouse recebidos: ${s.mouseMoves}")
            appendLine("Teclas: DOWN=${s.keyDowns}, UP=${s.keyUps}")
            appendLine("Transições de botões do mouse: ${s.buttonTransitions}")
            appendLine("Injeções: ${s.injectionSuccesses} sucesso(s), ${s.injectionFailures} falha(s)")
            appendLine("Último delta: X=${s.lastDx}, Y=${s.lastDy}")
            appendLine("Duração interna da chamada de injeção: média=${s.averageInjectionMs} ms, p95=${s.p95InjectionMs} ms")
            appendLine("Nota: esta duração mede a chamada interna de injeção; não mede a latência completa até o jogo.")
        }
    }

    private fun snapshot(): Snapshot = synchronized(lock) {
        val durations = (0 until durationCount).map { i ->
            injectionDurations[(durationIndex - durationCount + i + SAMPLE_COUNT) % SAMPLE_COUNT]
        }.sorted()
        val average = if (durations.isEmpty()) 0.0 else durations.average() / 1_000_000.0
        val p95 = if (durations.isEmpty()) 0.0 else durations[((durations.size - 1) * 95 / 100)] / 1_000_000.0
        Snapshot(
            mouseMoves.get(), keyDowns.get(), keyUps.get(), buttonTransitions.get(),
            injectionSuccesses.get(), injectionFailures.get(), lastDx, lastDy,
            captured, windowFocused, mode, lastTransition,
            ((SystemClock.elapsedRealtime() - startedAt) / 1000L), average, p95
        )
    }

    private data class Snapshot(
        val mouseMoves: Long, val keyDowns: Long, val keyUps: Long, val buttonTransitions: Long,
        val injectionSuccesses: Long, val injectionFailures: Long, val lastDx: Float, val lastDy: Float,
        val captured: Boolean, val windowFocused: Boolean, val mode: String, val lastTransition: String,
        val uptimeSeconds: Long, val averageInjectionMs: Double, val p95InjectionMs: Double
    )
}
