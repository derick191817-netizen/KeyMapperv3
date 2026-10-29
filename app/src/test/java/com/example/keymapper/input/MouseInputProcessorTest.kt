package com.example.keymapper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MouseInputProcessorTest {
    @Test fun acceptsFiniteDeltasAndPreservesMetadata() {
        val processor = MouseInputProcessor()
        val delta = processor.process(12.5f, -4f, 8, 1234L)!!
        assertEquals(12.5f, delta.dx, 0f)
        assertEquals(-4f, delta.dy, 0f)
        assertEquals(8, delta.deviceId)
        assertEquals(1234L, delta.eventTime)
        assertEquals(1L, processor.acceptedCount())
        assertEquals(0L, processor.rejectedCount())
    }

    @Test fun rejectsZeroAndNonFiniteSamples() {
        val processor = MouseInputProcessor()
        assertNull(processor.process(0f, 0f, 1, 1L))
        assertNull(processor.process(Float.NaN, 1f, 1, 2L))
        assertNull(processor.process(1f, Float.NEGATIVE_INFINITY, 1, 3L))
        assertEquals(0L, processor.acceptedCount())
        assertEquals(3L, processor.rejectedCount())
    }

    @Test fun extremeFiniteSampleIsSaturatedBeforeEnteringPipeline() {
        val processor = MouseInputProcessor()
        val delta = processor.process(Float.MAX_VALUE, -Float.MAX_VALUE, 1, 5L)!!
        assertEquals(100_000f, delta.dx, 0f)
        assertEquals(-100_000f, delta.dy, 0f)
    }

    @Test fun countersReset() {
        val processor = MouseInputProcessor()
        processor.process(1f, 0f, 1, 0L)
        processor.process(Float.NaN, 0f, 1, 0L)
        processor.reset()
        assertEquals(0L, processor.acceptedCount())
        assertEquals(0L, processor.rejectedCount())
    }
}
