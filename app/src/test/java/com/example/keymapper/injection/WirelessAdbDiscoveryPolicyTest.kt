package com.example.keymapper.injection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessAdbDiscoveryPolicyTest {
    @Test
    fun trimsHostAndNameAndUsesHostWhenNameIsBlank() {
        val result = WirelessAdbDiscoveryPolicy.sanitize(
            listOf(DiscoveredAdbEndpoint("  ", "  adb.local  ", 37123))
        )

        assertEquals(listOf(DiscoveredAdbEndpoint("adb.local", "adb.local", 37123)), result)
    }

    @Test
    fun rejectsBlankWhitespaceHostsAndInvalidPorts() {
        val input = listOf(
            DiscoveredAdbEndpoint("blank", "", 37123),
            DiscoveredAdbEndpoint("space", "adb local", 37123),
            DiscoveredAdbEndpoint("zero", "adb.local", 0),
            DiscoveredAdbEndpoint("too-high", "adb.local", 65536)
        )
        assertTrue(WirelessAdbDiscoveryPolicy.sanitize(input).isEmpty())
    }

    @Test
    fun removesDuplicateHostAndPortWithoutReorderingFirstOccurrence() {
        val first = DiscoveredAdbEndpoint("first", "ADB.LOCAL", 37123)
        val duplicate = DiscoveredAdbEndpoint("duplicate", "adb.local", 37123)
        val differentPort = DiscoveredAdbEndpoint("other-port", "adb.local", 37124)

        assertEquals(
            listOf(first, differentPort),
            WirelessAdbDiscoveryPolicy.sanitize(listOf(first, duplicate, differentPort))
        )
    }

    @Test
    fun preservesDifferentHostsOnSamePort() {
        val input = listOf(
            DiscoveredAdbEndpoint("one", "192.168.1.10", 37123),
            DiscoveredAdbEndpoint("two", "192.168.1.11", 37123)
        )
        assertEquals(2, WirelessAdbDiscoveryPolicy.sanitize(input).size)
    }

    @Test
    fun honorsDisplayLimitAndNonPositiveLimit() {
        val input = (1..10).map {
            DiscoveredAdbEndpoint("device-$it", "192.168.1.$it", 37123 + it)
        }
        assertEquals(8, WirelessAdbDiscoveryPolicy.sanitize(input).size)
        assertTrue(WirelessAdbDiscoveryPolicy.sanitize(input, 0).isEmpty())
        assertTrue(WirelessAdbDiscoveryPolicy.sanitize(input, -1).isEmpty())
    }
}
