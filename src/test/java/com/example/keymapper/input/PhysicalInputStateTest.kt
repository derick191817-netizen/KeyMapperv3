package com.example.keymapper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PhysicalInputStateTest {
    @Test fun repeatedKeyDownDoesNotCreateDuplicateHeldStateOnSameDevice() {
        val state = PhysicalInputState()
        assertTrue(state.pressKey(10, 29))
        assertFalse(state.pressKey(10, 29))
        assertTrue(state.releaseKey(10, 29))
        assertFalse(state.releaseKey(10, 29))
    }

    @Test fun sameKeyOnTwoKeyboardsIsReleasedOnlyAfterLastKeyboardReleases() {
        val state = PhysicalInputState()
        assertTrue(state.pressKey(10, 29))
        assertFalse(state.pressKey(20, 29)) // global DOWN already sent
        assertFalse(state.releaseKey(10, 29)) // keyboard 20 still holds it
        assertTrue(state.isKeyHeldAnywhere(29))
        assertTrue(state.releaseKey(20, 29)) // only now send global UP
        assertFalse(state.isKeyHeldAnywhere(29))
    }

    @Test fun removingOneKeyboardReleasesOnlyKeysNotHeldByAnotherKeyboard() {
        val state = PhysicalInputState()
        assertTrue(state.pressKey(10, 29))
        assertTrue(state.pressKey(10, 30))
        assertFalse(state.pressKey(20, 29))

        val removed = state.clearDevice(10)
        assertEquals(listOf(30), removed.keyCodes.toList())
        assertEquals(0, removed.buttonMask)
        assertTrue(state.isKeyHeldAnywhere(29))
        assertTrue(state.releaseKey(20, 29))
    }

    @Test fun sameMouseButtonAcrossDevicesDoesNotReleaseUntilLastDeviceIsRemoved() {
        val state = PhysicalInputState()
        val primary = 1
        assertEquals(primary, state.updateButtons(10, primary))
        assertEquals(0, state.updateButtons(20, primary))
        assertEquals(0, state.clearDevice(10).buttonMask)
        assertEquals(primary, state.clearDevice(20).buttonMask)
    }

    @Test fun buttonTransitionsAreComputedFromCombinedDeviceState() {
        val state = PhysicalInputState()
        assertEquals(3, state.updateButtons(10, 3))
        assertEquals(0, state.updateButtons(20, 1))
        assertEquals(2, state.updateButtons(10, 1)) // bit 2 is released globally
        assertEquals(0, state.updateButtons(20, 0)) // bit 1 remains held by device 10
        assertEquals(1, state.updateButtons(10, 0))
    }

    @Test fun deviceRemovalReleasesOnlyItsUniqueButtons() {
        val state = PhysicalInputState()
        assertEquals(3, state.updateButtons(10, 3))
        assertEquals(0, state.updateButtons(20, 1)) // bit 1 já estava globalmente pressionado
        assertEquals(2, state.clearDevice(10).buttonMask)
        assertEquals(1, state.clearDevice(20).buttonMask)
    }

    @Test fun clearResetsAllDevicesAndReturnsGlobalState() {
        val state = PhysicalInputState()
        assertTrue(state.pressKey(10, 29))
        assertFalse(state.pressKey(20, 29))
        assertEquals(1, state.updateButtons(10, 1))
        assertEquals(0, state.updateButtons(20, 1))
        val released = state.clear()
        assertEquals(1, released.buttonMask)
        assertEquals(listOf(29), released.keyCodes.toList())
        assertFalse(state.isKeyHeldAnywhere(29))
        assertTrue(state.canDispatchTransientKey(30, 29))
    }

    @Test fun transientKeyPulseIsSuppressedWhileAnyDeviceHoldsSameKey() {
        val state = PhysicalInputState()
        assertTrue(state.canDispatchTransientKey(10, 29))
        assertTrue(state.pressKey(20, 29))
        assertFalse(state.canDispatchTransientKey(10, 29))
        assertFalse(state.canDispatchTransientKey(20, 29))
        assertTrue(state.releaseKey(20, 29))
        assertTrue(state.canDispatchTransientKey(10, 29))
    }
    @Test fun randomizedMultiDeviceStateMatchesReferenceForTenThousandTransitions() {
        val actual = PhysicalInputState()
        val referenceKeys = LinkedHashMap<Int, MutableSet<Int>>()
        val referenceButtons = LinkedHashMap<Int, Int>()
        val random = Random(0xD3E1CE)

        fun globalKeys(): Set<Int> = referenceKeys.values.flatten().toSet()
        fun globalButtons(): Int = referenceButtons.values.fold(0) { acc, mask -> acc or mask }
        fun keySet(deviceId: Int): MutableSet<Int> = referenceKeys.getOrPut(deviceId) { LinkedHashSet() }

        repeat(10_000) {
            val deviceId = random.nextInt(1, 5)
            when (random.nextInt(5)) {
                0 -> {
                    val key = random.nextInt(1, 40)
                    val wasGlobal = key in globalKeys()
                    val wasOnDevice = !keySet(deviceId).add(key)
                    val expected = !wasGlobal && !wasOnDevice
                    assertEquals(expected, actual.pressKey(deviceId, key))
                }
                1 -> {
                    val key = random.nextInt(1, 40)
                    val existed = referenceKeys[deviceId]?.remove(key) == true
                    val stillGlobal = key in globalKeys()
                    if (referenceKeys[deviceId].isNullOrEmpty()) referenceKeys.remove(deviceId)
                    assertEquals(existed && !stillGlobal, actual.releaseKey(deviceId, key))
                }
                2 -> {
                    val before = globalButtons()
                    val next = random.nextInt(0, 32)
                    if (next == 0) referenceButtons.remove(deviceId) else referenceButtons[deviceId] = next
                    val expected = before xor globalButtons()
                    assertEquals(expected, actual.updateButtons(deviceId, next))
                }
                3 -> {
                    val beforeKeys = globalKeys()
                    val beforeButtons = globalButtons()
                    referenceKeys.remove(deviceId)
                    referenceButtons.remove(deviceId)
                    val afterKeys = globalKeys()
                    val afterButtons = globalButtons()
                    val released = actual.clearDevice(deviceId)
                    assertEquals(beforeKeys - afterKeys, released.keyCodes.toSet())
                    assertEquals(beforeButtons and afterButtons.inv(), released.buttonMask)
                }
                else -> {
                    val beforeKeys = globalKeys()
                    val beforeButtons = globalButtons()
                    referenceKeys.clear()
                    referenceButtons.clear()
                    val released = actual.clear()
                    assertEquals(beforeKeys, released.keyCodes.toSet())
                    assertEquals(beforeButtons, released.buttonMask)
                }
            }
            for (key in 1..40) assertEquals(key in globalKeys(), actual.isKeyHeldAnywhere(key))
        }
    }

}
