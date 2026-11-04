package com.example.keymapper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroStepPolicyTest {
    private val tap = HudButton(id = 1, key = 29, x = .2f, y = .3f, mode = Mode.TAP)
    private val hold = HudButton(id = 2, key = 30, x = .3f, y = .4f, mode = Mode.HOLD)
    private val fire = HudButton(id = 3, key = -1, x = .4f, y = .5f, kind = Kind.FIRE, mode = Mode.HOLD)
    private val swipe = HudButton(id = 4, key = 31, x = .5f, y = .6f, kind = Kind.SWIPE)
    private val rapid = HudButton(id = 5, key = 32, x = .6f, y = .7f, mode = Mode.RAPID)
    private val combo = HudButton(id = 6, key = 33, x = .7f, y = .8f, isCombo = true)
    private val macro = HudButton(id = 7, key = 34, x = .8f, y = .9f, kind = Kind.MACRO)

    @Test
    fun actionEligibilityIsTypeSpecificAndRejectsNesting() {
        assertTrue(MacroStepPolicy.isTargetEligible(MacroAction.TAP, tap))
        assertTrue(MacroStepPolicy.isTargetEligible(MacroAction.HOLD, hold))
        assertTrue(MacroStepPolicy.isTargetEligible(MacroAction.TAP, fire))
        assertTrue(MacroStepPolicy.isTargetEligible(MacroAction.SWIPE, swipe))
        assertFalse(MacroStepPolicy.isTargetEligible(MacroAction.SWIPE, tap))
        assertFalse(MacroStepPolicy.isTargetEligible(MacroAction.TAP, rapid))
        assertFalse(MacroStepPolicy.isTargetEligible(MacroAction.HOLD, combo))
        assertFalse(MacroStepPolicy.isTargetEligible(MacroAction.TAP, macro))
        assertFalse(MacroStepPolicy.isTargetEligible(MacroAction.WAIT, tap))
    }

    @Test
    fun normalizationDropsMissingSelfAndIncompatibleTargets() {
        val owner = HudButton(id = 20, key = 35, x = .5f, y = .5f, kind = Kind.MACRO)
        val steps = listOf(
            MacroStep(MacroAction.WAIT, 999, 250),
            MacroStep(MacroAction.TAP, tap.id, 25),
            MacroStep(MacroAction.HOLD, rapid.id, 500),
            MacroStep(MacroAction.SWIPE, swipe.id, 300),
            MacroStep(MacroAction.TAP, 20, 25),
            MacroStep(MacroAction.HOLD, 999, 250)
        )
        val result = MacroStepPolicy.normalizeSteps(steps, owner.id, listOf(owner, tap, rapid, swipe))
        assertEquals(
            listOf(
                MacroStep(MacroAction.WAIT, -1, 250),
                MacroStep(MacroAction.TAP, tap.id, 25),
                MacroStep(MacroAction.SWIPE, swipe.id, 300)
            ),
            result
        )
    }

    @Test
    fun stepAndRepeatLimitsAreBounded() {
        assertEquals(MacroStepPolicy.MAX_REPEATS, MacroStepPolicy.repeatCount(999))
        assertEquals(1, MacroStepPolicy.repeatCount(0))
        assertEquals(MacroStepPolicy.MIN_DURATION_MS, MacroStepPolicy.durationMs(-99))
        assertEquals(MacroStepPolicy.MAX_DURATION_MS, MacroStepPolicy.durationMs(99_999))
        val steps = (0 until 100).map { MacroStep(MacroAction.WAIT, -1, 100) }
        assertEquals(MacroStepPolicy.MAX_STEPS, MacroStepPolicy.normalizeSteps(steps, 8, listOf(macro)).size)
    }

    @Test
    fun swipeDurationUsesTheSameBoundsAsTheSharedSwipeInterpolator() {
        assertEquals(50, MacroStepPolicy.durationMs(MacroAction.SWIPE, 10))
        assertEquals(180, MacroStepPolicy.durationMs(MacroAction.SWIPE, 180))
        assertEquals(2_000, MacroStepPolicy.durationMs(MacroAction.SWIPE, 10_000))
        assertEquals(25, MacroStepPolicy.durationMs(MacroAction.TAP, 2_000))
        assertEquals(10_000, MacroStepPolicy.durationMs(MacroAction.HOLD, 10_000))
    }

    @Test
    fun macroAndComboRemainSeparateWhenProfileIsNormalized() {
        val owner = HudButton(
            id = 10, key = 36, x = .3f, y = .4f, kind = Kind.MACRO,
            isCombo = true, macro = listOf(1),
            sequence = listOf(MacroStep(MacroAction.TAP, tap.id, 25)),
            sequenceRepeatCount = 4
        )
        val normalized = MappingDataPolicy.normalizeButtons(listOf(owner, tap)).first()
        assertFalse(normalized.isCombo)
        assertTrue(normalized.macro.isEmpty())
        assertEquals(listOf(MacroStep(MacroAction.TAP, tap.id, 25)), normalized.sequence)
        assertEquals(4, normalized.sequenceRepeatCount)
    }

    @Test
    fun sequenceCursorRunsStepsInOrderAndRepeatsOnlyConfiguredCycles() {
        val steps = listOf(
            MacroStep(MacroAction.WAIT, -1, 100),
            MacroStep(MacroAction.TAP, tap.id, 25),
            MacroStep(MacroAction.SWIPE, swipe.id, 180)
        )
        val cursor = MacroSequenceCursor(steps, 2)
        assertEquals(steps + steps, List(6) { cursor.nextStep() })
        assertTrue(cursor.isFinished)
        assertNull(cursor.nextStep())
    }

    @Test
    fun sequenceCursorHandlesEmptyAndClampsRepeatCount() {
        val empty = MacroSequenceCursor(emptyList(), 5)
        assertNull(empty.nextStep())
        assertTrue(empty.isFinished)

        val once = MacroSequenceCursor(listOf(MacroStep(MacroAction.WAIT, -1, 10)), 0)
        assertEquals(MacroStep(MacroAction.WAIT, -1, 10), once.nextStep())
        assertTrue(once.isFinished)
        assertNull(once.nextStep())

        val mutableSteps = mutableListOf(MacroStep(MacroAction.WAIT, -1, 20))
        val snapshot = MacroSequenceCursor(mutableSteps, 1)
        mutableSteps.clear()
        assertEquals(MacroStep(MacroAction.WAIT, -1, 20), snapshot.nextStep())
        assertTrue(snapshot.isFinished)

        val capped = MacroSequenceCursor(List(100) { MacroStep(MacroAction.WAIT, -1, 10) }, 1)
        assertEquals(MacroStepPolicy.MAX_STEPS, generateSequence { capped.nextStep() }.count())
        assertTrue(capped.isFinished)
    }
}
