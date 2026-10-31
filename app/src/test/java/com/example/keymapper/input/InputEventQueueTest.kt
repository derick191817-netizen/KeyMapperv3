package com.example.keymapper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InputEventQueueTest {
    @Test fun preservesGlobalOrderAcrossMouseKeysAndScroll() {
        val queue = InputEventQueue(capacity = 16, controlReserve = 2)
        val out = InputEventQueue.Event()
        assertTrue(queue.offerMouse(1f, 2f, 100L, 7))
        assertTrue(queue.offerKey(30, true))
        assertTrue(queue.offerScroll(0f, -1f))
        assertTrue(queue.offerMouse(3f, 4f, 200L, 7))

        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_MOUSE, out.type)
        assertEquals(1f, out.x, 0f)
        assertEquals(100L, out.eventTimeNanos)
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_KEY, out.type)
        assertEquals(30, out.code)
        assertTrue(out.down)
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_SCROLL, out.type)
        assertEquals(-1f, out.y, 0f)
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_MOUSE, out.type)
        assertEquals(3f, out.x, 0f)
        assertFalse(queue.pollInto(out))
    }

    @Test fun reservesSlotsForControlDuringMouseBurst() {
        val queue = InputEventQueue(capacity = 8, controlReserve = 2)
        repeat(6) { assertTrue(queue.offerMouse(1f, 0f, it.toLong(), 1)) }
        assertTrue(queue.offerMouse(1f, 0f, 7L, 1))
        assertEquals(1L, queue.droppedMouse)
        assertTrue(queue.offerKey(42, true))
        assertEquals(7, queue.size())
    }

    @Test fun criticalControlEvictsOldestMouseWithoutReorderingRemainingEvents() {
        val queue = InputEventQueue(capacity = 4, controlReserve = 1)
        val out = InputEventQueue.Event()
        assertTrue(queue.offerMouse(1f, 0f, 1L, 1))
        assertTrue(queue.offerKey(10, true))
        assertTrue(queue.offerMouse(2f, 0f, 2L, 1))
        assertTrue(queue.offerKey(10, false))
        // Full: a new control transition evicts the oldest motion sample, not a key transition.
        assertTrue(queue.offerKey(11, true))
        assertEquals(1L, queue.droppedMouse)
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_KEY, out.type)
        assertEquals(10, out.code)
        assertTrue(out.down)
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_MOUSE, out.type)
        assertEquals(2f, out.x, 0f)
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_KEY, out.type)
        assertFalse(out.down)
        assertTrue(queue.pollInto(out))
        assertEquals(11, out.code)
        assertFalse(queue.pollInto(out))
    }

    @Test fun releaseClearsStaleBacklogAndBecomesNextEvent() {
        val queue = InputEventQueue(capacity = 8, controlReserve = 1)
        val out = InputEventQueue.Event()
        repeat(5) { queue.offerMouse(1f, 1f, it.toLong(), 2) }
        assertTrue(queue.offerKey(22, true))
        queue.offerRelease()
        assertEquals(1, queue.size())
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_RELEASE, out.type)
        assertFalse(queue.pollInto(out))
    }

    @Test fun circularIndicesRemainCorrectAfterWraparound() {
        val queue = InputEventQueue(capacity = 8, controlReserve = 1)
        val out = InputEventQueue.Event()
        repeat(5) { assertTrue(queue.offerKey(it, true)) }
        repeat(4) {
            assertTrue(queue.pollInto(out))
            assertEquals(it, out.code)
        }
        repeat(6) { assertTrue(queue.offerKey(10 + it, true)) }
        val expected = listOf(4, 10, 11, 12, 13, 14, 15)
        expected.forEach { code ->
            assertTrue(queue.pollInto(out))
            assertEquals(code, out.code)
        }
        assertFalse(queue.pollInto(out))
    }

    @Test fun evictionMovesTheSmallerPrefixWhenMouseIsNearHead() {
        val queue = InputEventQueue(capacity = 8, controlReserve = 1)
        val out = InputEventQueue.Event()
        assertTrue(queue.offerKey(1, true))
        assertTrue(queue.offerMouse(1f, 0f, 10L, 1))
        repeat(6) { assertTrue(queue.offerKey(2 + it, true)) }
        assertTrue(queue.offerKey(99, true)) // full: remove mouse at offset 1 by shifting prefix
        val expected = listOf(1, 2, 3, 4, 5, 6, 7, 99)
        expected.forEach { code ->
            assertTrue(queue.pollInto(out))
            assertEquals(InputEventQueue.TYPE_KEY, out.type)
            assertEquals(code, out.code)
        }
        assertFalse(queue.pollInto(out))
    }

    @Test fun evictionMovesTheSmallerSuffixWhenMouseIsNearTail() {
        val queue = InputEventQueue(capacity = 8, controlReserve = 1)
        val out = InputEventQueue.Event()
        repeat(5) { assertTrue(queue.offerKey(10 + it, true)) }
        assertTrue(queue.offerMouse(1f, 0f, 10L, 1))
        assertTrue(queue.offerKey(20, true))
        assertTrue(queue.offerKey(21, true))
        assertTrue(queue.offerKey(99, true)) // full: remove mouse at offset 5 by shifting suffix
        val expected = listOf(10, 11, 12, 13, 14, 20, 21, 99)
        expected.forEach { code ->
            assertTrue(queue.pollInto(out))
            assertEquals(InputEventQueue.TYPE_KEY, out.type)
            assertEquals(code, out.code)
        }
        assertFalse(queue.pollInto(out))
    }


    @Test fun randomizedMixedEventsMatchReferenceFifoAcrossWrapAndEviction() {
        data class Expected(
            val type: Int,
            val x: Float = 0f,
            val y: Float = 0f,
            val code: Int = 0,
            val down: Boolean = false,
            val time: Long = 0L
        )
        val capacity = 16
        val reserve = 4
        val queue = InputEventQueue(capacity, reserve)
        val model = ArrayList<Expected>()
        val out = InputEventQueue.Event()
        val random = Random(0x4B4D)
        var modelDroppedMouse = 0L
        var modelDroppedScroll = 0L
        var modelDroppedControl = 0L

        fun removeModelMouse(): Boolean {
            val index = model.indexOfFirst { it.type == InputEventQueue.TYPE_MOUSE }
            if (index < 0) return false
            model.removeAt(index)
            modelDroppedMouse++
            return true
        }
        fun offerModelControl(event: Expected): Boolean {
            if (model.size >= capacity && !removeModelMouse()) {
                if (event.type == InputEventQueue.TYPE_SCROLL) modelDroppedScroll++ else modelDroppedControl++
                return false
            }
            model.add(event)
            return true
        }
        fun compareNext() {
            val expected = model.removeAt(0)
            assertTrue(queue.pollInto(out))
            assertEquals(expected.type, out.type)
            assertEquals(expected.x, out.x, 0f)
            assertEquals(expected.y, out.y, 0f)
            assertEquals(expected.code, out.code)
            assertEquals(expected.down, out.down)
            assertEquals(expected.time, out.eventTimeNanos)
        }

        repeat(5_000) { index ->
            when (random.nextInt(4)) {
                0 -> {
                    val event = Expected(InputEventQueue.TYPE_MOUSE, index.toFloat(), -1f, time = index.toLong())
                    var accepted = true
                    if (model.size >= capacity - reserve && !removeModelMouse()) {
                        modelDroppedMouse++
                        accepted = false
                    }
                    if (accepted) model.add(event)
                    assertEquals(accepted, queue.offerMouse(event.x, event.y, event.time, 3))
                }
                1 -> {
                    val code = random.nextInt(10, 80)
                    val down = random.nextBoolean()
                    var accepted = offerModelControl(Expected(InputEventQueue.TYPE_KEY, code = code, down = down))
                    if (!accepted && !down) {
                        model.clear()
                        model.add(Expected(InputEventQueue.TYPE_RELEASE))
                        accepted = true
                    }
                    assertEquals(accepted, queue.offerKey(code, down))
                }
                2 -> {
                    val x = if (random.nextBoolean()) 1f else -1f
                    val y = if (random.nextBoolean()) 0.5f else -0.5f
                    val accepted = offerModelControl(Expected(InputEventQueue.TYPE_SCROLL, x = x, y = y))
                    assertEquals(accepted, queue.offerScroll(x, y))
                }
                else -> {
                    if (model.isNotEmpty() && random.nextBoolean()) compareNext()
                }
            }
            assertEquals(model.size, queue.size())
        }
        while (model.isNotEmpty()) compareNext()
        assertFalse(queue.pollInto(out))
        assertEquals(modelDroppedMouse, queue.droppedMouse)
        assertEquals(modelDroppedScroll, queue.droppedScroll)
        assertEquals(modelDroppedControl, queue.droppedControl)
    }


    @Test fun enqueueTimestampSurvivesPrefixCompaction() {
        var now = 10L
        val queue = InputEventQueue(capacity = 8, controlReserve = 1, nanoTime = { now })
        val out = InputEventQueue.Event()
        assertTrue(queue.offerKey(1, true))
        now = 20L
        assertTrue(queue.offerMouse(1f, 0f, 20L, 1))
        for (i in 2..7) {
            now += 10L
            assertTrue(queue.offerKey(i, true))
        }
        now = 90L
        assertTrue(queue.offerKey(8, true)) // remove mouse at offset 1; prefix is shifted

        val expectedCodes = listOf(1, 2, 3, 4, 5, 6, 7, 8)
        val expectedQueuedAt = listOf(10L, 30L, 40L, 50L, 60L, 70L, 80L, 90L)
        for (i in expectedCodes.indices) {
            assertTrue(queue.pollInto(out))
            assertEquals(InputEventQueue.TYPE_KEY, out.type)
            assertEquals(expectedCodes[i], out.code)
            assertEquals(expectedQueuedAt[i], out.queuedAtNanos)
        }
        assertFalse(queue.pollInto(out))
    }

    @Test fun enqueueTimestampSurvivesSuffixCompaction() {
        var now = 100L
        val queue = InputEventQueue(capacity = 8, controlReserve = 1, nanoTime = { now })
        val out = InputEventQueue.Event()
        for (i in 10..14) {
            assertTrue(queue.offerKey(i, true))
            now += 10L
        }
        assertTrue(queue.offerMouse(1f, 0f, 150L, 2))
        now += 10L
        assertTrue(queue.offerKey(15, true))
        now += 10L
        assertTrue(queue.offerKey(16, true))
        now = 190L
        assertTrue(queue.offerKey(17, true)) // remove mouse near tail; suffix is shifted

        val expectedCodes = listOf(10, 11, 12, 13, 14, 15, 16, 17)
        val expectedQueuedAt = listOf(100L, 110L, 120L, 130L, 140L, 160L, 170L, 190L)
        for (i in expectedCodes.indices) {
            assertTrue(queue.pollInto(out))
            assertEquals(InputEventQueue.TYPE_KEY, out.type)
            assertEquals(expectedCodes[i], out.code)
            assertEquals(expectedQueuedAt[i], out.queuedAtNanos)
        }
        assertFalse(queue.pollInto(out))
    }

    @Test fun controlOverflowIsCountedWhenNoMouseCanBeEvicted() {
        val queue = InputEventQueue(capacity = 4, controlReserve = 1)
        repeat(4) { assertTrue(queue.offerKey(100 + it, true)) }
        assertFalse(queue.offerKey(999, true))
        assertEquals(1L, queue.droppedControl)
        assertEquals(4, queue.size())
    }

    @Test fun scrollOverflowHasItsOwnDropCounter() {
        val queue = InputEventQueue(capacity = 4, controlReserve = 1)
        repeat(4) { assertTrue(queue.offerKey(100 + it, true)) }
        assertFalse(queue.offerScroll(0f, 1f, 8))
        assertEquals(1L, queue.droppedScroll)
        assertEquals(0L, queue.droppedControl)
        assertEquals(4, queue.size())
    }

    @Test fun saturatedQueueConvertsUnqueueableKeyUpIntoReleaseAll() {
        val queue = InputEventQueue(capacity = 4, controlReserve = 1)
        val out = InputEventQueue.Event()
        repeat(4) { assertTrue(queue.offerKey(200 + it, true)) }
        assertTrue(queue.offerKey(999, false))
        assertEquals(1L, queue.droppedControl)
        assertEquals(1, queue.size())
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_RELEASE, out.type)
    }

    @Test fun staleMouseSamplesAreRemovedButControlAndFreshEventsKeepOrder() {
        var now = 0L
        val queue = InputEventQueue(capacity = 12, controlReserve = 2, nanoTime = { now })
        val out = InputEventQueue.Event()
        assertTrue(queue.offerMouse(1f, 0f, 1L, 1))
        now = 10L
        assertTrue(queue.offerKey(40, true))
        now = 40L
        assertTrue(queue.offerMouse(2f, 0f, 2L, 1))
        now = 50L
        assertTrue(queue.offerScroll(0f, -1f))
        now = 75L
        assertTrue(queue.offerMouse(3f, 0f, 3L, 1))

        // Max age is 50: samples queued at 0, 40, 75; only the first is stale at now=80.
        assertEquals(1, queue.discardStaleMouse(nowNanos = 80L, maxAgeNanos = 50L))
        assertEquals(1L, queue.droppedMouse)
        assertEquals(4, queue.size())
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_KEY, out.type)
        assertEquals(40, out.code)
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_MOUSE, out.type)
        assertEquals(2f, out.x, 0f)
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_SCROLL, out.type)
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_MOUSE, out.type)
        assertEquals(3f, out.x, 0f)
        assertFalse(queue.pollInto(out))
    }

    @Test fun staleMouseCompactionWorksAcrossRingWraparound() {
        var now = 0L
        val queue = InputEventQueue(capacity = 8, controlReserve = 1, nanoTime = { now })
        val out = InputEventQueue.Event()
        repeat(6) { i ->
            now += 10L
            assertTrue(queue.offerKey(10 + i, true))
        }
        repeat(5) { assertTrue(queue.pollInto(out)) }
        now = 100L
        assertTrue(queue.offerMouse(1f, 0f, 1L, 1)) // old, stored after wrap
        now = 110L
        assertTrue(queue.offerKey(99, true))
        now = 180L
        assertTrue(queue.offerMouse(2f, 0f, 2L, 1)) // fresh enough at the purge time
        assertEquals(1, queue.discardStaleMouse(nowNanos = 200L, maxAgeNanos = 30L))
        val expected = listOf(15, 99)
        expected.forEach { code ->
            assertTrue(queue.pollInto(out))
            assertEquals(InputEventQueue.TYPE_KEY, out.type)
            assertEquals(code, out.code)
        }
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_MOUSE, out.type)
        assertEquals(2f, out.x, 0f)
        assertFalse(queue.pollInto(out))
    }

    @Test fun sampleAtExactMaximumAgeIsRetained() {
        val queue = InputEventQueue(capacity = 8, controlReserve = 1, nanoTime = { 100L })
        val out = InputEventQueue.Event()
        assertTrue(queue.offerMouse(1f, 0f, 1L, 1))
        // O descarte usa idade estritamente maior que o limite; exatamente 50 ms permanece.
        assertEquals(0, queue.discardStaleMouse(nowNanos = 150L, maxAgeNanos = 50L))
        assertEquals(0L, queue.droppedMouse)
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_MOUSE, out.type)
        assertEquals(1f, out.x, 0f)
    }

    @Test fun futureQueuedTimestampIsNotTreatedAsStale() {
        val queue = InputEventQueue(capacity = 8, controlReserve = 1, nanoTime = { 200L })
        val out = InputEventQueue.Event()
        assertTrue(queue.offerMouse(1f, 0f, 1L, 1))
        assertEquals(0, queue.discardStaleMouse(nowNanos = 100L, maxAgeNanos = 1L))
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_MOUSE, out.type)
    }

    @Test fun removingDeviceDropsOnlyItsPendingMouseAndScrollEvents() {
        val queue = InputEventQueue(capacity = 16, controlReserve = 2)
        val out = InputEventQueue.Event()
        assertTrue(queue.offerMouse(1f, 0f, 1L, 10))
        assertTrue(queue.offerKey(40, true))
        assertTrue(queue.offerScroll(0f, -1f, 20))
        assertTrue(queue.offerMouse(2f, 0f, 2L, 20))
        assertTrue(queue.offerKey(41, true))
        assertTrue(queue.offerScroll(1f, 0f, 10))

        assertEquals(InputEventQueue.DiscardedPointerEvents(mouse = 1, scroll = 1),
            queue.discardPointerEventsFromDevice(10))
        assertEquals(4, queue.size())
        assertEquals(1L, queue.droppedMouse)
        assertEquals(1L, queue.droppedScroll)

        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_KEY, out.type)
        assertEquals(40, out.code)
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_SCROLL, out.type)
        assertEquals(20, out.deviceId)
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_MOUSE, out.type)
        assertEquals(20, out.deviceId)
        assertEquals(2f, out.x, 0f)
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_KEY, out.type)
        assertEquals(41, out.code)
        assertFalse(queue.pollInto(out))
    }

    @Test fun removedDeviceSeparatesMouseAndScrollDropCounters() {
        val queue = InputEventQueue(capacity = 12, controlReserve = 2)
        assertTrue(queue.offerMouse(1f, 0f, 1L, 4))
        assertTrue(queue.offerScroll(0f, 1f, 4))
        assertTrue(queue.offerScroll(0f, -1f, 9))

        val removed = queue.discardPointerEventsFromDevice(4)
        assertEquals(InputEventQueue.DiscardedPointerEvents(mouse = 1, scroll = 1), removed)
        assertEquals(1L, queue.droppedMouse)
        assertEquals(1L, queue.droppedScroll)
        assertEquals(1, queue.size())
    }

    @Test fun removingDevicePreservesOtherDevicesAcrossRingWraparound() {
        val queue = InputEventQueue(capacity = 8, controlReserve = 1)
        val out = InputEventQueue.Event()
        repeat(5) { assertTrue(queue.offerKey(100 + it, true)) }
        repeat(4) { assertTrue(queue.pollInto(out)) }
        assertTrue(queue.offerMouse(1f, 0f, 1L, 10))
        assertTrue(queue.offerScroll(0f, 1f, 20))
        assertTrue(queue.offerMouse(2f, 0f, 2L, 20))
        assertEquals(InputEventQueue.DiscardedPointerEvents(mouse = 1, scroll = 0),
            queue.discardPointerEventsFromDevice(10))
        assertEquals(1L, queue.droppedMouse)
        assertEquals(0L, queue.droppedScroll)
        assertEquals(3, queue.size())
        val types = mutableListOf<Int>()
        val ids = mutableListOf<Int>()
        while (queue.pollInto(out)) {
            types += out.type
            ids += out.deviceId
        }
        assertEquals(listOf(InputEventQueue.TYPE_KEY, InputEventQueue.TYPE_SCROLL, InputEventQueue.TYPE_MOUSE), types)
        assertEquals(listOf(-1, 20, 20), ids)
    }

    @Test fun singleStaleMouseIsDiscardedEvenWhenQueueIsSmallerThanDrainBatch() {
        var now = 1_000L
        val queue = InputEventQueue(capacity = 32, controlReserve = 4, nanoTime = { now })
        val out = InputEventQueue.Event()
        assertTrue(queue.offerMouse(4f, -2f, 1L, 7))
        now = 2_000L
        assertTrue(queue.offerKey(30, true))
        assertTrue(queue.offerScroll(0f, -1f, 8))

        // Apenas três eventos pendentes: a política não pode depender de backlog > 256.
        assertEquals(1, queue.discardStaleMouse(nowNanos = 60_000_001L, maxAgeNanos = 50_000_000L))
        assertEquals(2, queue.size())
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_KEY, out.type)
        assertTrue(queue.pollInto(out))
        assertEquals(InputEventQueue.TYPE_SCROLL, out.type)
        assertEquals(8, out.deviceId)
        assertFalse(queue.pollInto(out))
    }

}
