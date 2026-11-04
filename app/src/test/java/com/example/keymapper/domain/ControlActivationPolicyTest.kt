package com.example.keymapper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlActivationPolicyTest {
    @Test
    fun sharedContactIsReleasedOnlyAfterLastOwnerLeaves() {
        val state = ControlPressOwnership()
        val physical = PressSource(PressSourceKind.DIRECT, 7)
        val aim = PressSource(PressSourceKind.AIM_FIRE, 20)

        val first = state.acquire(7, physical)
        assertTrue(first.accepted)
        assertTrue(first.firstOwner)

        val second = state.acquire(7, aim)
        assertTrue(second.accepted)
        assertFalse(second.firstOwner)
        assertEquals(2, state.ownerCount(7))

        assertFalse(state.release(7, aim))
        assertEquals(1, state.ownerCount(7))
        assertTrue(state.release(7, physical))
        assertEquals(0, state.ownerCount(7))
    }

    @Test
    fun transientTapCannotReleaseAConcurrentHold() {
        val state = ControlPressOwnership()
        val hold = PressSource(PressSourceKind.DIRECT, 4)
        val tap = PressSource(PressSourceKind.TRANSIENT_TAP, 101)

        assertTrue(state.acquire(4, hold).firstOwner)
        assertFalse(state.acquire(4, tap).firstOwner)
        assertFalse(state.release(4, tap))
        assertEquals(1, state.ownerCount(4))
        assertTrue(state.release(4, hold))
    }

    @Test
    fun duplicateSourceDoesNotIncreaseOwnershipOrTriggerAnotherDown() {
        val state = ControlPressOwnership()
        val source = PressSource(PressSourceKind.FIRE_LINK_AIM, 12)
        assertTrue(state.acquire(3, source).firstOwner)
        val duplicate = state.acquire(3, source)
        assertFalse(duplicate.accepted)
        assertFalse(duplicate.firstOwner)
        assertEquals(1, state.ownerCount(3))
        assertTrue(state.release(3, source))
        assertFalse(state.release(3, source))
    }

    @Test
    fun releaseFromOneFireDoesNotRemoveAnotherFireOwner() {
        val state = ControlPressOwnership()
        val fireA = PressSource(PressSourceKind.FIRE_LINK_AIM, 1)
        val fireB = PressSource(PressSourceKind.FIRE_LINK_AIM, 2)
        assertTrue(state.acquire(9, fireA).firstOwner)
        assertFalse(state.acquire(9, fireB).firstOwner)
        assertFalse(state.release(9, fireA))
        assertTrue(state.contains(9, fireB))
        assertTrue(state.release(9, fireB))
    }

    @Test
    fun physicalInputAndTwoCombosHaveIndependentOwnership() {
        val state = ControlPressOwnership()
        val physical = PressSource(PressSourceKind.DIRECT, 9)
        val comboA = PressSource(PressSourceKind.COMBO, 30)
        val comboB = PressSource(PressSourceKind.COMBO, 31)

        assertTrue(state.acquire(9, physical).firstOwner)
        assertFalse(state.acquire(9, comboA).firstOwner)
        assertFalse(state.acquire(9, comboB).firstOwner)
        assertFalse(state.release(9, comboA))
        assertTrue(state.contains(9, physical))
        assertTrue(state.contains(9, comboB))
        assertFalse(state.release(9, physical))
        assertTrue(state.release(9, comboB))
    }

    @Test
    fun activeAimReturnsToPreviousAimWhenNewestSourceLeaves() {
        val state = AimActivationRegistry()
        val keyboard = PressSource(PressSourceKind.AIM_KEY, 1)
        val fire = PressSource(PressSourceKind.FIRE_LINK_AIM, 2)
        val secondAim = PressSource(PressSourceKind.AIM_KEY, 3)

        assertTrue(state.activate(10, keyboard))
        assertEquals(10, state.activeAimId())
        assertTrue(state.activate(10, fire))
        assertEquals(10, state.activeAimId())
        assertTrue(state.activate(20, secondAim))
        assertEquals(20, state.activeAimId())

        assertTrue(state.deactivate(20, secondAim))
        assertEquals(10, state.activeAimId())
        assertTrue(state.deactivate(10, fire))
        assertEquals(10, state.activeAimId())
        assertTrue(state.deactivate(10, keyboard))
        assertEquals(-1, state.activeAimId())
    }

    @Test
    fun toggleChangesOnlyItsOwnAimSource() {
        val state = AimActivationRegistry()
        val toggle = PressSource(PressSourceKind.AIM_KEY, 11)
        val fire = PressSource(PressSourceKind.FIRE_LINK_AIM, 12)

        assertTrue(state.toggle(5, toggle))
        assertTrue(state.activate(5, fire))
        assertFalse(state.toggle(5, toggle))
        assertTrue(state.contains(5, fire))
        assertEquals(5, state.activeAimId())
        assertTrue(state.deactivate(5, fire))
        assertEquals(-1, state.activeAimId())
    }
}
