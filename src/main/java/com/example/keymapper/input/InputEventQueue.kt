package com.example.keymapper.input

/**
 * Fila circular pré-alocada para serializar eventos de entrada sem criar um Runnable por amostra.
 * Não é thread-safe: o proprietário deve proteger todas as chamadas com o mesmo lock.
 *
 * A fila mantém a ordem global entre mouse, teclas, rolagem e RELEASE. Uma reserva de capacidade
 * impede que uma rajada contínua de mouse ocupe todos os slots necessários às transições de tecla.
 * Em saturação, somente amostras de movimento podem ser descartadas para dar lugar a controle.
 */
class InputEventQueue(
    val capacity: Int = DEFAULT_CAPACITY,
    private val controlReserve: Int = minOf(64, maxOf(1, capacity / 8)),
    private val nanoTime: () -> Long = { System.nanoTime() }
) {
    companion object {
        const val TYPE_MOUSE = 1
        const val TYPE_KEY = 2
        const val TYPE_SCROLL = 3
        const val TYPE_RELEASE = 4
        const val DEFAULT_CAPACITY = 2048
        const val STALE_MOUSE_MAX_AGE_NANOS = 50_000_000L
    }

    data class DiscardedPointerEvents(val mouse: Int, val scroll: Int) {
        val total: Int get() = mouse + scroll
    }

    class Event {
        var type: Int = 0
            internal set
        var x: Float = 0f
            internal set
        var y: Float = 0f
            internal set
        var code: Int = 0
            internal set
        var down: Boolean = false
            internal set
        var eventTimeNanos: Long = 0L
            internal set
        var deviceId: Int = -1
            internal set
        /** Instante monotônico da entrada na fila; usado somente para medir espera local. */
        var queuedAtNanos: Long = 0L
            internal set
    }

    private val types = IntArray(capacity)
    private val xs = FloatArray(capacity)
    private val ys = FloatArray(capacity)
    private val codes = IntArray(capacity)
    private val downs = BooleanArray(capacity)
    private val times = LongArray(capacity)
    private val devices = IntArray(capacity)
    private val enqueuedAt = LongArray(capacity)

    private var head = 0
    private var count = 0
    var droppedMouse: Long = 0L
        private set
    var droppedScroll: Long = 0L
        private set
    var droppedControl: Long = 0L
        private set
    var highWatermark: Int = 0
        private set

    init {
        require(capacity >= 4) { "capacity precisa ser >= 4" }
        require(controlReserve in 1 until capacity) { "controlReserve deve estar entre 1 e capacity-1" }
    }

    fun offerMouse(dx: Float, dy: Float, eventTimeNanos: Long, deviceId: Int): Boolean {
        if (!dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) return false
        // Mantém slots de controle livres. Se a fila acumular atraso, descarta a amostra
        // de movimento mais antiga e conserva a mais recente para limitar latência percebida.
        if (count >= capacity - controlReserve && !removeOldestMouse()) {
            droppedMouse++
            return false
        }
        append(TYPE_MOUSE, dx, dy, 0, false, eventTimeNanos, deviceId)
        return true
    }

    fun offerKey(keyCode: Int, isDown: Boolean): Boolean {
        val accepted = offerControl(TYPE_KEY, 0f, 0f, keyCode, isDown, 0L, -1)
        if (accepted || isDown) return accepted
        // Em saturação composta somente por eventos de controle, perder KEY_UP poderia manter
        // uma tecla lógica presa. A alternativa segura é cancelar o backlog e soltar tudo.
        offerRelease()
        return true
    }

    fun offerScroll(horizontal: Float, vertical: Float, deviceId: Int = -1): Boolean {
        if (!horizontal.isFinite() || !vertical.isFinite() ||
            (horizontal == 0f && vertical == 0f)
        ) return false
        return offerControl(TYPE_SCROLL, horizontal, vertical, 0, false, 0L, deviceId)
    }

    /** RELEASE invalida todo o backlog anterior e sempre ganha um slot próprio. */
    fun offerRelease() {
        clear()
        append(TYPE_RELEASE, 0f, 0f, 0, false, 0L, -1)
    }

    private fun offerControl(
        type: Int,
        x: Float,
        y: Float,
        code: Int,
        down: Boolean,
        time: Long,
        deviceId: Int
    ): Boolean {
        if (count >= capacity && !removeOldestMouse()) {
            if (type == TYPE_SCROLL) droppedScroll++ else droppedControl++
            return false
        }
        append(type, x, y, code, down, time, deviceId)
        return true
    }

    fun pollInto(target: Event): Boolean {
        if (count == 0) return false
        val index = head
        target.type = types[index]
        target.x = xs[index]
        target.y = ys[index]
        target.code = codes[index]
        target.down = downs[index]
        target.eventTimeNanos = times[index]
        target.deviceId = devices[index]
        target.queuedAtNanos = enqueuedAt[index]
        head = (head + 1) % capacity
        count--
        return true
    }

    /**
     * Descarta somente amostras de mouse cuja permanência na fila excedeu o limite.
     * Teclas, botões, scroll e RELEASE permanecem na mesma ordem relativa. O compactador é
     * estável e escreve sempre em um offset <= ao offset de leitura, portanto funciona também
     * quando o ring buffer atravessa o fim do array, sem buffer auxiliar ou alocação por evento.
     *
     * [nowNanos] e [queuedAtNanos] precisam usar o mesmo relógio monotônico.
     */
    fun discardStaleMouse(nowNanos: Long, maxAgeNanos: Long): Int {
        require(maxAgeNanos >= 0L) { "maxAgeNanos não pode ser negativo" }
        if (count == 0) return 0

        // Os horários de enfileiramento são monotônicos e a fila é FIFO. Portanto, basta
        // inspecionar a primeira amostra de mouse: se ela ainda é recente, nenhuma amostra de
        // mouse posterior pode estar mais velha. Isso torna a verificação barata no caso normal.
        for (readOffset in 0 until count) {
            val index = (head + readOffset) % capacity
            if (types[index] == TYPE_MOUSE) {
                val age = nowNanos - enqueuedAt[index]
                if (age < 0L || age <= maxAgeNanos) return 0
                break
            }
        }

        val originalCount = count
        var retained = 0
        var dropped = 0
        for (readOffset in 0 until originalCount) {
            val from = (head + readOffset) % capacity
            val age = nowNanos - enqueuedAt[from]
            val stale = types[from] == TYPE_MOUSE && age >= 0L && age > maxAgeNanos
            if (stale) {
                dropped++
            } else {
                val to = (head + retained) % capacity
                if (to != from) copySlot(from, to)
                retained++
            }
        }
        if (dropped > 0) {
            count = retained
            droppedMouse += dropped.toLong()
        }
        return dropped
    }

    /**
     * Remove movimentos e scroll ainda pendentes de um dispositivo desconectado.
     * Eventos de teclado e RELEASE nunca são removidos por esta operação.
     */
    fun discardPointerEventsFromDevice(deviceId: Int): DiscardedPointerEvents {
        if (count == 0) return DiscardedPointerEvents(0, 0)
        val originalCount = count
        var retained = 0
        var droppedMouseCount = 0
        var droppedScrollCount = 0
        for (readOffset in 0 until originalCount) {
            val from = (head + readOffset) % capacity
            val type = types[from]
            val pointerEvent = type == TYPE_MOUSE || type == TYPE_SCROLL
            if (pointerEvent && devices[from] == deviceId) {
                if (type == TYPE_MOUSE) droppedMouseCount++ else droppedScrollCount++
            } else {
                val to = (head + retained) % capacity
                if (to != from) copySlot(from, to)
                retained++
            }
        }
        if (droppedMouseCount + droppedScrollCount > 0) count = retained
        droppedMouse += droppedMouseCount.toLong()
        droppedScroll += droppedScrollCount.toLong()
        return DiscardedPointerEvents(droppedMouseCount, droppedScrollCount)
    }

    fun clear() {
        head = 0
        count = 0
    }

    fun isEmpty(): Boolean = count == 0
    fun size(): Int = count

    private fun append(
        type: Int,
        x: Float,
        y: Float,
        code: Int,
        down: Boolean,
        time: Long,
        deviceId: Int
    ) {
        val index = (head + count) % capacity
        types[index] = type
        xs[index] = x
        ys[index] = y
        codes[index] = code
        downs[index] = down
        times[index] = time
        devices[index] = deviceId
        enqueuedAt[index] = nanoTime()
        count++
        if (count > highWatermark) highWatermark = count
    }

    /**
     * Remove a amostra de movimento mais antiga, preservando a ordem dos demais eventos.
     * Desloca o lado menor do buffer: caso comum (mouse no head) é O(1), evitando copiar
     * milhares de posições a cada amostra quando a fila está saturada por movimento.
     */
    private fun removeOldestMouse(): Boolean {
        var offset = 0
        while (offset < count && types[(head + offset) % capacity] != TYPE_MOUSE) offset++
        if (offset == count) return false

        val prefixCount = offset
        val suffixCount = count - offset - 1
        if (prefixCount <= suffixCount) {
            // Move o prefixo uma posição para trás e avança o head para remover o item.
            for (shift in offset downTo 1) {
                copySlot((head + shift - 1) % capacity, (head + shift) % capacity)
            }
            head = (head + 1) % capacity
        } else {
            // O sufixo é menor: fecha o buraco deslocando apenas os itens posteriores.
            for (shift in offset until count - 1) {
                copySlot((head + shift + 1) % capacity, (head + shift) % capacity)
            }
        }
        count--
        droppedMouse++
        return true
    }

    private fun copySlot(from: Int, to: Int) {
        types[to] = types[from]
        xs[to] = xs[from]
        ys[to] = ys[from]
        codes[to] = codes[from]
        downs[to] = downs[from]
        times[to] = times[from]
        devices[to] = devices[from]
        enqueuedAt[to] = enqueuedAt[from]
    }
}
