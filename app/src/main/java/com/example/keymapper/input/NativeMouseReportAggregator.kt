package com.example.keymapper.input

/**
 * Junta REL_X e REL_Y do mesmo relatório evdev em um único vetor de movimento.
 *
 * O kernel entrega cada eixo como um evento EV_REL separado, mas ambos pertencem ao mesmo
 * SYN_REPORT. Passar cada eixo isoladamente ao MouseSampleClock fazia a normalização temporal
 * depender da ordem X/Y e do intervalo entre duas chamadas do mesmo relatório. O agregador
 * mantém estado por deviceId e só publica uma amostra no SYN_REPORT (ou antes de um evento de
 * controle que precise preservar a ordem).
 *
 * A classe é deliberadamente independente do Android e só deve ser acessada pelo looper que
 * consome os lotes JNI. Um relatório dividido entre dois callbacks JNI continua corretamente
 * acumulado até que seu SYN_REPORT chegue.
 */
internal class NativeMouseReportAggregator(
    private val maxAxisDeltaPerReport: Double = MAX_AXIS_DELTA_PER_REPORT
) {
    data class Sample(val dx: Float, val dy: Float, val eventTimeNanos: Long)

    private data class Pending(
        var dx: Double = 0.0,
        var dy: Double = 0.0,
        var lastEventTimeNanos: Long = 0L
    )

    private val pendingByDevice = HashMap<Int, Pending>()

    init {
        require(maxAxisDeltaPerReport.isFinite() && maxAxisDeltaPerReport > 0.0)
    }

    /** Adiciona um evento REL_X/REL_Y; eventos de outros eixos são ignorados. */
    fun add(deviceId: Int, code: Int, value: Int, eventTimeNanos: Long): Boolean {
        if (value == 0 || (code != LinuxEvdevEvent.REL_X && code != LinuxEvdevEvent.REL_Y)) {
            return false
        }
        val pending = pendingByDevice.getOrPut(deviceId) { Pending() }
        if (code == LinuxEvdevEvent.REL_X) {
            pending.dx = (pending.dx + value.toDouble()).coerceIn(-maxAxisDeltaPerReport, maxAxisDeltaPerReport)
        } else {
            pending.dy = (pending.dy + value.toDouble()).coerceIn(-maxAxisDeltaPerReport, maxAxisDeltaPerReport)
        }
        if (eventTimeNanos > 0L) pending.lastEventTimeNanos = eventTimeNanos
        return true
    }

    /**
     * Fecha o relatório de [deviceId]. O timestamp de SYN_REPORT é preferido; caso o flush seja
     * provocado por um EV_KEY/scroll anterior ao marcador, usa-se o timestamp do último eixo.
     */
    fun flush(deviceId: Int, reportTimeNanos: Long = 0L): Sample? {
        val pending = pendingByDevice.remove(deviceId) ?: return null
        if (pending.dx == 0.0 && pending.dy == 0.0) return null
        val time = reportTimeNanos.takeIf { it > 0L } ?: pending.lastEventTimeNanos
        return Sample(pending.dx.toFloat(), pending.dy.toFloat(), time)
    }

    /** Perda de sincronização ou remoção de dispositivo descarta apenas seu relatório parcial. */
    fun drop(deviceId: Int) {
        pendingByDevice.remove(deviceId)
    }

    /** Pausa/reset global: nenhum delta incompleto pode reaparecer após retomar a captura. */
    fun clear() {
        pendingByDevice.clear()
    }

    fun pendingDeviceCount(): Int = pendingByDevice.size

    private companion object {
        const val MAX_AXIS_DELTA_PER_REPORT = 100_000.0
    }
}
