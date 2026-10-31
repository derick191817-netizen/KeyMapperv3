package com.example.keymapper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the pure-Kotlin sensitivity engine. */
class SensitivityEngineTest {

    @Test
    fun linearCurvePreservesIntegerDeltasAtUnitSensitivity() {
        val engine = SensitivityEngine()

        engine.process(
            dx = 12f, dy = -3f,
            sx = 1f, sy = 1f,
            curve = 0, exponent = 2f, knee = 10f, gainAbove = 1f
        )

        assertEquals(12, engine.outX)
        assertEquals(-3, engine.outY)
    }

    @Test
    fun xAndYSensitivityAreAppliedIndependently() {
        val engine = SensitivityEngine()

        engine.process(
            dx = 5f, dy = 4f,
            sx = 2f, sy = 0.5f,
            curve = 0, exponent = 2f, knee = 10f, gainAbove = 1f
        )

        assertEquals(10, engine.outX)
        assertEquals(2, engine.outY)
    }

    @Test
    fun fractionalDeltasAccumulateInsteadOfBeingLost() {
        val engine = SensitivityEngine()
        repeat(4) {
            engine.process(
                dx = 0.25f, dy = 0f,
                sx = 1f, sy = 1f,
                curve = 0, exponent = 2f, knee = 10f, gainAbove = 1f
            )
        }

        assertEquals(1, engine.outX)
        assertEquals(0, engine.outY)
    }

    @Test
    fun invalidSensitivityValuesFallBackToOne() {
        val engine = SensitivityEngine()

        engine.process(
            dx = 3f, dy = 2f,
            sx = Float.NaN, sy = Float.POSITIVE_INFINITY,
            curve = 0, exponent = 2f, knee = 10f, gainAbove = 1f
        )

        assertEquals(3, engine.outX)
        assertEquals(2, engine.outY)
    }

    @Test
    fun resetClearsOutputAndFractionalRemainder() {
        val engine = SensitivityEngine()
        engine.process(
            dx = 0.5f, dy = 0f,
            sx = 1f, sy = 1f,
            curve = 0, exponent = 2f, knee = 10f, gainAbove = 1f
        )
        assertEquals(0, engine.outX)

        engine.reset()
        assertEquals(0, engine.outX)
        assertEquals(0, engine.outY)

        engine.process(
            dx = 0.5f, dy = 0f,
            sx = 1f, sy = 1f,
            curve = 0, exponent = 2f, knee = 10f, gainAbove = 1f
        )
        assertEquals(0, engine.outX)
    }

    @Test
    fun nonFiniteMovementClearsFractionalStateInsteadOfPoisoningFutureFrames() {
        val engine = SensitivityEngine()
        engine.process(0.5f, 0f, 1f, 1f, 0, 2f, 10f, 1f)
        assertEquals(0, engine.outX)

        engine.process(Float.NaN, 0f, 1f, 1f, 0, 2f, 10f, 1f)
        assertEquals(0, engine.outX)
        assertEquals(0, engine.outY)

        engine.process(0.5f, 0f, 1f, 1f, 0, 2f, 10f, 1f)
        assertEquals(0, engine.outX)
        engine.process(0.5f, 0f, 1f, 1f, 0, 2f, 10f, 1f)
        assertEquals(1, engine.outX)
    }

    @Test
    fun pathologicalInputsAndCurveParametersStayFiniteAndBounded() {
        val engine = SensitivityEngine()
        engine.process(
            dx = Float.MAX_VALUE, dy = -Float.MAX_VALUE,
            sx = 1000f, sy = 1000f,
            curve = 1, exponent = 10f, knee = Float.NaN,
            gainAbove = Float.POSITIVE_INFINITY, timeScale = 100f
        )
        assertTrue(engine.outX in -1_000_000..1_000_000)
        assertTrue(engine.outY in -1_000_000..1_000_000)

        engine.process(2f, 2f, 1f, 1f, 1, Float.NaN, Float.NaN, Float.NaN)
        assertTrue(engine.outX in -1_000_000..1_000_000)
        assertTrue(engine.outY in -1_000_000..1_000_000)
    }

    @Test
    fun linearCurveIsInvariantAcrossEquivalentPollingRates() {
        val fast = SensitivityEngine()
        val slow = SensitivityEngine()
        var fastTotalX = 0
        var fastTotalY = 0

        slow.process(8f, -4f, 1f, 1f, 0, 1.5f, 8f, 1.5f, timeScale = 0.5f)
        val slowTotalX = slow.outX
        val slowTotalY = slow.outY
        repeat(8) {
            fast.process(1f, -0.5f, 1f, 1f, 0, 1.5f, 8f, 1.5f, timeScale = 4f)
            fastTotalX += fast.outX
            fastTotalY += fast.outY
        }

        assertEquals(slowTotalX, fastTotalX)
        assertEquals(slowTotalY, fastTotalY)
    }

    @Test
    fun nonlinearCurveIsStableAcrossEquivalentPollingRates() {
        val fast = SensitivityEngine()
        val slow = SensitivityEngine()
        var fastTotalX = 0
        var fastTotalY = 0

        // Mesma velocidade física: 1 unidade/ms em ambos os casos.
        // 125 Hz: 8 unidades em 8 ms. 1000 Hz: 1 unidade em cada 1 ms.
        slow.process(8f, 0f, 1f, 1f, 1, 1.5f, 8f, 1.5f, timeScale = 0.5f)
        val slowTotalX = slow.outX
        val slowTotalY = slow.outY
        repeat(8) {
            fast.process(1f, 0f, 1f, 1f, 1, 1.5f, 8f, 1.5f, timeScale = 4f)
            fastTotalX += fast.outX
            fastTotalY += fast.outY
        }

        // outX/outY são a saída da amostra atual; a distância total é a soma da sequência.
        assertEquals(slowTotalX, fastTotalX)
        assertEquals(slowTotalY, fastTotalY)
    }

}
