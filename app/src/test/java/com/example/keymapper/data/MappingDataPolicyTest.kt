package com.example.keymapper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingDataPolicyTest {
    @Test
    fun duplicateIdsAreReassignedWithoutDroppingControls() {
        val input = listOf(
            HudButton(id = 7, key = 29, x = .2f, y = .3f),
            HudButton(id = 7, key = 30, x = .3f, y = .4f),
            HudButton(id = 0, key = 31, x = .4f, y = .5f)
        )
        val result = MappingDataPolicy.normalizeButtons(input)
        assertEquals(3, result.size)
        assertEquals(3, result.map { it.id }.toSet().size)
        assertEquals(7, result[0].id)
        assertEquals(1, result[1].id)
        assertEquals(0, result[2].id)
    }

    @Test
    fun macroAndAimLinksToMissingOrWrongTypeControlsAreRemoved() {
        val input = listOf(
            HudButton(id = 1, key = 29, x = .2f, y = .3f, macro = listOf(2, 99, 2)),
            HudButton(id = 2, key = 30, x = .3f, y = .4f, kind = Kind.STANDARD, linkAimId = 99),
            HudButton(id = 3, key = 31, x = .4f, y = .5f, kind = Kind.FIRE, linkAimId = 2)
        )
        val result = MappingDataPolicy.normalizeButtons(input)
        assertEquals(listOf(2), result[0].macro)
        assertEquals(-1, result[1].linkAimId)
        assertEquals(-1, result[2].linkAimId)
    }

    @Test
    fun aimLinkIsPreservedOnlyWhenTargetIsAnAimControl() {
        val input = listOf(
            HudButton(id = 1, key = 29, x = .2f, y = .3f, kind = Kind.AIM),
            HudButton(id = 2, key = -1, x = .3f, y = .4f, kind = Kind.FIRE, linkAimId = 1)
        )
        val result = MappingDataPolicy.normalizeButtons(input)
        assertEquals(1, result[1].linkAimId)
    }

    @Test
    fun controlCountIsBounded() {
        val result = MappingDataPolicy.normalizeButtons((0..140).map {
            HudButton(id = it, key = 0, x = .5f, y = .5f)
        })
        assertEquals(MappingDataPolicy.MAX_CONTROLS, result.size)
        assertTrue(result.map { it.id }.toSet().size == result.size)
    }
}
