package com.example.keymapper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MouseInputProcessorTest {
    @Test fun acceptsFiniteDeltasAndPreservesMetadata() {
        val processor = MouseInputProcessor()
        val delta = processor.process(12.5f, -4f, 8, 1234L)!!
        assertEquals(12.5f, delta.dx, 0f)
        assertEquals(-4f, delta.dy, 0f)
        assertEquals(8, delta.deviceId)
        assertEquals(1234L, delta.eventTimeNanos)
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
    @Test fun sampleClockNormalizesDifferentPollingIntervalsWithNanosecondTimestamps() {
        val fast = MouseSampleClock()
        val slow = MouseSampleClock()
        fast.timeScale(100_000_000L, 4)
        slow.timeScale(100_000_000L, 4)
        assertEquals(4f, fast.timeScale(101_000_000L, 4), 0f)
        assertEquals(0.5f, slow.timeScale(108_000_000L, 4), 0f)
    }

    @Test fun sampleClockResetsAfterDeviceChangeAndLongIdle() {
        val clock = MouseSampleClock()
        assertEquals(1f, clock.timeScale(100_000_000L, 1), 0f)
        assertEquals(1f, clock.timeScale(104_000_000L, 1), 0f)
        assertEquals(1f, clock.timeScale(108_000_000L, 2), 0f)
        assertEquals(1f, clock.timeScale(500_000_000L, 2), 0f)
    }


    @Test fun legacyMillisecondClockTreatsEqualTimestampsAsOneMillisecondApart() {
        val clock = MouseSampleClock(timestampResolutionNanos = 1_000_000L)
        assertEquals(1f, clock.timeScale(100_000_000L, 7), 0f)
        // Dois eventos diferentes podem cair no mesmo tick de uptimeMillis no Android 13.
        assertEquals(4f, clock.timeScale(100_000_000L, 7), 0f)
        assertEquals(4f, clock.timeScale(101_000_000L, 7), 0f)
        assertEquals(0.5f, clock.timeScale(109_000_000L, 7), 0f)
    }

    @Test fun nanosecondClockKeepsExactDuplicateTimestampNeutral() {
        val clock = MouseSampleClock(timestampResolutionNanos = 1L)
        assertEquals(1f, clock.timeScale(100_000_000L, 7), 0f)
        assertEquals(1f, clock.timeScale(100_000_000L, 7), 0f)
        assertEquals(4f, clock.timeScale(101_000_000L, 7), 0f)
    }

    @Test fun legacyClockDoesNotMoveBackwardsWhenTimestampIsStale() {
        val clock = MouseSampleClock(timestampResolutionNanos = 1_000_000L)
        clock.timeScale(100_000_000L, 7)
        assertEquals(1f, clock.timeScale(99_000_000L, 7), 0f)
        assertEquals(2f, clock.timeScale(102_000_000L, 7), 0f)
    }

    @Test fun sampleClockDoesNotMoveBackwardsOnOutOfOrderSample() {
        val clock = MouseSampleClock()
        clock.timeScale(100_000_000L, 1)
        assertEquals(1f, clock.timeScale(99_000_000L, 1), 0f)
        // It is measured from 100 ms, not from the stale 99 ms timestamp.
        assertEquals(2f, clock.timeScale(102_000_000L, 1), 0f)
    }

    @Test fun processIntoReusesCallerBufferAndKeepsValidationCounters() {
        val processor = MouseInputProcessor()
        val scratch = FloatArray(2)
        assertTrue(processor.processInto(3.5f, -7.25f, scratch))
        assertEquals(3.5f, scratch[0], 0f)
        assertEquals(-7.25f, scratch[1], 0f)
        assertFalse(processor.processInto(0f, 0f, scratch))
        assertEquals(1L, processor.acceptedCount())
        assertEquals(1L, processor.rejectedCount())
    }

    @Test fun firstSampleAfterLongIdleResetsWindowAndNextSampleNormalizes() {
        val clock = MouseSampleClock()
        assertEquals(1f, clock.timeScale(100_000_000L, 5), 0f)
        assertEquals(1f, clock.timeScale(200_000_000L, 5), 0f) // reinicia após pausa > 50 ms
        assertEquals(4f, clock.timeScale(201_000_000L, 5), 0f) // volta à normalização temporal
    }

    @Test fun sampleClockKeepsIndependentTimingForInterleavedMice() {
        val clock = MouseSampleClock()
        assertEquals(1f, clock.timeScale(100_000_000L, 10), 0f)
        assertEquals(1f, clock.timeScale(100_000_000L, 20), 0f)
        assertEquals(4f, clock.timeScale(101_000_000L, 10), 0f)
        assertEquals(0.5f, clock.timeScale(108_000_000L, 20), 0f)
    }

    @Test fun removingOneMouseResetsOnlyItsTemporalHistory() {
        val clock = MouseSampleClock()
        clock.timeScale(100_000_000L, 10)
        clock.timeScale(100_000_000L, 20)
        assertEquals(4f, clock.timeScale(101_000_000L, 10), 0f)
        clock.resetDevice(10)
        assertEquals(1f, clock.timeScale(102_000_000L, 10), 0f)
        assertEquals(0.5f, clock.timeScale(108_000_000L, 20), 0f)
    }

}
