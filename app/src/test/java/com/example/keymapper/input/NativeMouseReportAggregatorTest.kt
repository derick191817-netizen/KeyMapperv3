package com.example.keymapper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMouseReportAggregatorTest {
    @Test fun combinesBothAxesIntoOneVectorAndUsesReportTimestamp() {
        val aggregator = NativeMouseReportAggregator()
        assertTrue(aggregator.add(4, LinuxEvdevEvent.REL_X, 7, 1_000L))
        assertTrue(aggregator.add(4, LinuxEvdevEvent.REL_Y, -3, 1_100L))

        val sample = aggregator.flush(4, 1_200L)
        assertEquals(7f, sample?.dx ?: 0f, 0f)
        assertEquals(-3f, sample?.dy ?: 0f, 0f)
        assertEquals(1_200L, sample?.eventTimeNanos)
        assertEquals(0, aggregator.pendingDeviceCount())
    }

    @Test fun sumsRepeatedAxisEventsWithinOneReport() {
        val aggregator = NativeMouseReportAggregator()
        aggregator.add(2, LinuxEvdevEvent.REL_X, 4, 100L)
        aggregator.add(2, LinuxEvdevEvent.REL_X, -1, 110L)
        aggregator.add(2, LinuxEvdevEvent.REL_Y, 3, 120L)
        val sample = aggregator.flush(2, 130L)
        assertEquals(3f, sample?.dx ?: 0f, 0f)
        assertEquals(3f, sample?.dy ?: 0f, 0f)
    }

    @Test fun reportMaySpanMultipleJniBatches() {
        val aggregator = NativeMouseReportAggregator()
        aggregator.add(8, LinuxEvdevEvent.REL_X, 5, 1_000L)
        assertNull(aggregator.flush(9, 1_100L))
        assertEquals(1, aggregator.pendingDeviceCount())

        // O segundo eixo chega no callback JNI seguinte, antes do SYN_REPORT.
        aggregator.add(8, LinuxEvdevEvent.REL_Y, -6, 1_200L)
        val sample = aggregator.flush(8, 1_300L)
        assertEquals(5f, sample?.dx ?: 0f, 0f)
        assertEquals(-6f, sample?.dy ?: 0f, 0f)
    }

    @Test fun devicesNeverSharePendingAxisState() {
        val aggregator = NativeMouseReportAggregator()
        aggregator.add(1, LinuxEvdevEvent.REL_X, 10, 100L)
        aggregator.add(2, LinuxEvdevEvent.REL_Y, 20, 200L)

        val second = aggregator.flush(2, 210L)
        val first = aggregator.flush(1, 110L)
        assertEquals(0f, second?.dx ?: 0f, 0f)
        assertEquals(20f, second?.dy ?: 0f, 0f)
        assertEquals(10f, first?.dx ?: 0f, 0f)
        assertEquals(0f, first?.dy ?: 0f, 0f)
    }

    @Test fun preControlFlushUsesLastAxisTimestamp() {
        val aggregator = NativeMouseReportAggregator()
        aggregator.add(3, LinuxEvdevEvent.REL_X, 2, 500L)
        val sample = aggregator.flush(3)
        assertEquals(500L, sample?.eventTimeNanos)
        assertEquals(2f, sample?.dx ?: 0f, 0f)
    }

    @Test fun droppedReportIsDiscardedWithoutAResidualSample() {
        val aggregator = NativeMouseReportAggregator()
        aggregator.add(5, LinuxEvdevEvent.REL_X, 9, 100L)
        aggregator.add(5, LinuxEvdevEvent.REL_Y, 2, 101L)
        aggregator.drop(5)
        assertNull(aggregator.flush(5, 102L))
        assertEquals(0, aggregator.pendingDeviceCount())
    }

    @Test fun clearRemovesEveryIncompleteDeviceReport() {
        val aggregator = NativeMouseReportAggregator()
        aggregator.add(1, LinuxEvdevEvent.REL_X, 1, 100L)
        aggregator.add(2, LinuxEvdevEvent.REL_Y, 1, 100L)
        aggregator.clear()
        assertFalse(aggregator.add(3, LinuxEvdevEvent.REL_WHEEL, 1, 101L))
        assertNull(aggregator.flush(1, 102L))
        assertNull(aggregator.flush(2, 102L))
        assertEquals(0, aggregator.pendingDeviceCount())
    }

    @Test fun pathologicalReportIsBoundedWithoutIntegerOverflow() {
        val aggregator = NativeMouseReportAggregator(maxAxisDeltaPerReport = 100.0)
        aggregator.add(7, LinuxEvdevEvent.REL_X, Int.MAX_VALUE, 100L)
        aggregator.add(7, LinuxEvdevEvent.REL_X, Int.MAX_VALUE, 101L)
        val sample = aggregator.flush(7, 102L)
        assertEquals(100f, sample?.dx ?: 0f, 0f)
    }
}
