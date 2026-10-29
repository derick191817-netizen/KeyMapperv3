package com.example.keymapper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
}
