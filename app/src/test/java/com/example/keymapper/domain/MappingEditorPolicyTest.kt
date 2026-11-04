package com.example.keymapper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.example.keymapper.data.HudButton
import com.example.keymapper.data.Kind
import com.example.keymapper.data.Mode
import org.junit.Test

class MappingEditorPolicyTest {
    @Test
    fun duplicateKeyBindingIsDetectedForAnotherControlOnly() {
        val bindings = listOf(
            MappingEditorPolicy.KeyBinding(1, "Pulo", 62),
            MappingEditorPolicy.KeyBinding(2, "Agachar", 31)
        )

        assertEquals("Pulo", MappingEditorPolicy.findConflict(62, 3, bindings)?.label)
        assertNull(MappingEditorPolicy.findConflict(62, 1, bindings))
        assertNull(MappingEditorPolicy.findConflict(44, 3, bindings))
        assertNull(MappingEditorPolicy.findConflict(0, 3, bindings)) // controle ainda sem associação
    }

    @Test
    fun globalShortcutsAreReservedForTheService() {
        assertTrue(MappingEditorPolicy.conflictsWithGlobalShortcut(131, 131, 132))
        assertTrue(MappingEditorPolicy.conflictsWithGlobalShortcut(132, 131, 132))
        assertFalse(MappingEditorPolicy.conflictsWithGlobalShortcut(44, 131, 132))
        assertFalse(MappingEditorPolicy.conflictsWithGlobalShortcut(0, 131, 132))
    }

    @Test
    fun globalShortcutConflictIsFoundAcrossProvidedProfilesAndIgnoresUnassignedControls() {
        val bindings = listOf(
            MappingEditorPolicy.KeyBinding(1, "Sem tecla", 0),
            MappingEditorPolicy.KeyBinding(2, "Pulo", 131),
            MappingEditorPolicy.KeyBinding(3, "Agachar", 31)
        )

        assertEquals("Pulo", MappingEditorPolicy.findGlobalShortcutConflict(131, 132, bindings)?.label)
        assertNull(MappingEditorPolicy.findGlobalShortcutConflict(133, 134, bindings))
        assertNull(MappingEditorPolicy.findGlobalShortcutConflict(0, 134, bindings))
    }

    @Test
    fun nextIdFillsGapsAndRejectsFullProfiles() {
        assertEquals(1, MappingEditorPolicy.nextAvailableId(listOf(0, 2, 3), 3))
        assertEquals(0, MappingEditorPolicy.nextAvailableId(listOf(9, 10), 2))
        assertNull(MappingEditorPolicy.nextAvailableId((0..127).toList(), 128))
        assertNull(MappingEditorPolicy.nextAvailableId(emptyList(), 0, capacity = 0))
    }

    @Test
    fun canvasGestureOnlyCountsAsTapWithinTouchSlop() {
        assertTrue(MappingEditorPolicy.isTap(10f, 10f, 12f, 12f, 4f))
        assertFalse(MappingEditorPolicy.isTap(10f, 10f, 20f, 10f, 4f))
        assertFalse(MappingEditorPolicy.isTap(Float.NaN, 0f, 0f, 0f, 4f))
    }

    @Test
    fun normalizedPositionClampsAndRecoversInvalidValues() {
        assertEquals(0f, MappingEditorPolicy.normalizedPosition(-0.4f), 0f)
        assertEquals(1f, MappingEditorPolicy.normalizedPosition(1.4f), 0f)
        assertEquals(0.5f, MappingEditorPolicy.normalizedPosition(Float.NaN), 0f)
        assertEquals(0.1f, MappingEditorPolicy.boundedCenter(0f, 0.1f), 0f)
        assertEquals(0.9f, MappingEditorPolicy.boundedCenter(1f, 0.1f), 0f)
        assertEquals(0.5f, MappingEditorPolicy.boundedCenter(0f, 2f), 0f)
    }
    @Test
    fun everyPersistedControlKindHasAUserFacingLabel() {
        assertEquals("Controle", MappingEditorPolicy.controlKindLabel(0))
        assertEquals("Fire", MappingEditorPolicy.controlKindLabel(1))
        assertEquals("Aim / Sight", MappingEditorPolicy.controlKindLabel(2))
        assertEquals("Swipe", MappingEditorPolicy.controlKindLabel(3))
        assertEquals("Macro", MappingEditorPolicy.controlKindLabel(4))
        assertEquals("Controle", MappingEditorPolicy.controlKindLabel(999))
    }

    @Test
    fun libraryKeepsTapHoldFireAndSightAsDistinctUserFacingTools() {
        assertEquals("Tap", MappingEditorPolicy.controlDisplayLabel(0, 1))
        assertEquals("Hold", MappingEditorPolicy.controlDisplayLabel(0, 0))
        assertEquals("Rapid Tap", MappingEditorPolicy.controlDisplayLabel(0, 4))
        assertEquals("Fire", MappingEditorPolicy.controlDisplayLabel(1, 0))
        assertEquals("Aim / Sight", MappingEditorPolicy.controlDisplayLabel(2, 0))
        assertEquals("Swipe", MappingEditorPolicy.controlDisplayLabel(3, 0))
        assertEquals("Macro", MappingEditorPolicy.controlDisplayLabel(4, 0))
        assertEquals("Combo", MappingEditorPolicy.controlDisplayLabel(0, 0, isCombo = true))
    }

    @Test
    fun comboRejectsSelfNestedRapidAndSwipeTargets() {
        val hold = HudButton(id = 2, key = 44, kind = Kind.STANDARD, mode = Mode.HOLD)
        val fire = HudButton(id = 3, key = -1, kind = Kind.FIRE, mode = Mode.HOLD)
        val rapid = HudButton(id = 4, key = 45, kind = Kind.STANDARD, mode = Mode.RAPID)
        val swipe = HudButton(id = 5, key = 46, kind = Kind.SWIPE)
        val nestedCombo = HudButton(id = 6, key = 47, isCombo = true)
        val nestedMacro = HudButton(id = 7, key = 48, kind = Kind.MACRO)
        assertTrue(MappingEditorPolicy.isComboTargetEligible(1, hold))
        assertTrue(MappingEditorPolicy.isComboTargetEligible(1, fire))
        assertFalse(MappingEditorPolicy.isComboTargetEligible(2, hold))
        assertFalse(MappingEditorPolicy.isComboTargetEligible(1, rapid))
        assertFalse(MappingEditorPolicy.isComboTargetEligible(1, swipe))
        assertFalse(MappingEditorPolicy.isComboTargetEligible(1, nestedCombo))
        assertFalse(MappingEditorPolicy.isComboTargetEligible(1, nestedMacro))
    }

    @Test
    fun fireDefaultsToLeftMouseOnlyWhenNoBindingOrGlobalShortcutConflicts() {
        assertEquals(-1, MappingEditorPolicy.defaultFireBinding(131, 132, emptyList()))
        assertEquals(0, MappingEditorPolicy.defaultFireBinding(131, 132, listOf(
            MappingEditorPolicy.KeyBinding(4, "Disparo anterior", -1)
        )))
        assertEquals(0, MappingEditorPolicy.defaultFireBinding(-1, 132, emptyList()))
        assertEquals(0, MappingEditorPolicy.defaultFireBinding(131, -1, emptyList()))
    }

}
