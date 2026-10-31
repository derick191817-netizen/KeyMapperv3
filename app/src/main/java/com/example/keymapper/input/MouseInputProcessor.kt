package com.example.keymapper.input

import java.util.concurrent.atomic.AtomicLong

/** Valida e contabiliza deltas recebidos; sensibilidade e curvas são aplicadas pelo motor de mira. */
class MouseInputProcessor {
    data class Delta(val dx: Float, val dy: Float, val deviceId: Int, val eventTimeNanos: Long)

    private val accepted = AtomicLong(0)
    private val rejected = AtomicLong(0)

    /** Caminho sem alocação para o loop de alta frequência; [out] precisa ter ao menos 2 posições. */
    fun processInto(dx: Float, dy: Float, out: FloatArray): Boolean {
        if (out.size < 2) return false
        if (!dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) {
            rejected.incrementAndGet()
            return false
        }
        // Um driver defeituoso não deve conseguir entregar deltas quase infinitos ao pipeline.
        // O limite é muito acima de um evento físico normal e só atua como proteção de integridade.
        accepted.incrementAndGet()
        out[0] = dx.coerceIn(-MAX_DELTA_PER_EVENT, MAX_DELTA_PER_EVENT)
        out[1] = dy.coerceIn(-MAX_DELTA_PER_EVENT, MAX_DELTA_PER_EVENT)
        return true
    }

    /** API compatível para diagnósticos/testes; o pipeline principal usa processInto reutilizando buffer. */
    fun process(dx: Float, dy: Float, deviceId: Int, eventTimeNanos: Long): Delta? {
        val out = FloatArray(2)
        if (!processInto(dx, dy, out)) return null
        return Delta(out[0], out[1], deviceId, eventTimeNanos)
    }

    fun acceptedCount(): Long = accepted.get()
    fun rejectedCount(): Long = rejected.get()
    fun reset() { accepted.set(0); rejected.set(0) }

    private companion object {
        const val MAX_DELTA_PER_EVENT = 100_000f
    }
}

/**
 * Normaliza diferenças de polling usando timestamps monotônicos em nanossegundos.
 * A referência padrão é 4 ms (250 amostras/s); gaps maiores que 50 ms iniciam um novo trecho
 * sem acelerar ou desacelerar artificialmente a primeira amostra após a pausa.
 *
 * O relógio não retrocede quando recebe um timestamp fora de ordem. Isso evita que uma amostra
 * atrasada contamine a escala temporal das amostras seguintes.
 */
class MouseSampleClock(
    private val referenceIntervalNanos: Long = 4_000_000L,
    private val maxIdleGapNanos: Long = 50_000_000L,
    /**
     * Resolução mínima representável pelo relógio da origem. Até API 33, MotionEvent só expõe
     * eventTime em milissegundos; converter para nanossegundos não cria precisão adicional.
     * Nesse caso, amostras com o mesmo timestamp são tratadas como separadas por 1 ms.
     * Em API 34+, passe 1 ns para usar getEventTimeNanos sem essa aproximação.
     */
    private val timestampResolutionNanos: Long = 1L
) {
    private data class DeviceClock(var lastTimeNanos: Long, var initialized: Boolean)
    // Um relógio por deviceId evita que eventos intercalados de dois mouses reiniciem a escala
    // temporal um do outro. Entradas são criadas somente na primeira amostra de cada dispositivo.
    private val clocks = HashMap<Int, DeviceClock>()

    init {
        require(referenceIntervalNanos > 0L) { "referenceIntervalNanos deve ser positivo" }
        require(maxIdleGapNanos > 0L) { "maxIdleGapNanos deve ser positivo" }
        require(timestampResolutionNanos > 0L) { "timestampResolutionNanos deve ser positivo" }
    }

    fun timeScale(eventTimeNanos: Long, deviceId: Int): Float {
        val clock = clocks.getOrPut(deviceId) { DeviceClock(eventTimeNanos, false) }
        if (!clock.initialized) {
            clock.lastTimeNanos = eventTimeNanos
            clock.initialized = true
            return 1f
        }

        val previous = clock.lastTimeNanos
        // Timestamp realmente atrasado: não retrocede o relógio nem infere velocidade.
        if (eventTimeNanos < previous) return 1f

        val elapsed = eventTimeNanos - previous
        if (elapsed == 0L) {
            // Em relógios de precisão de milissegundos, eventos diferentes podem compartilhar
            // o mesmo timestamp. Tratar isso como 1 ms evita alternar acidentalmente para ganho
            // neutro em mouses de polling alto. Com timestamps de ns, repetição exata permanece
            // indeterminada e recebe escala neutra.
            if (timestampResolutionNanos <= 1L) return 1f
            return (referenceIntervalNanos.toDouble() / timestampResolutionNanos.toDouble())
                .toFloat()
                .coerceIn(0.08f, 4f)
        }

        clock.lastTimeNanos = eventTimeNanos
        // A primeira amostra após uma pausa longa inicia uma nova janela; atualizar o relógio
        // aqui é essencial para que as amostras seguintes voltem a normalizar normalmente.
        if (elapsed > maxIdleGapNanos) return 1f
        val measurableElapsed = elapsed.coerceAtLeast(timestampResolutionNanos)
        return (referenceIntervalNanos.toDouble() / measurableElapsed.toDouble())
            .toFloat()
            .coerceIn(0.08f, 4f)
    }

    /** Remove apenas a referência temporal do periférico desconectado. */
    fun resetDevice(deviceId: Int) {
        clocks.remove(deviceId)
    }

    fun reset() {
        clocks.clear()
    }
}
