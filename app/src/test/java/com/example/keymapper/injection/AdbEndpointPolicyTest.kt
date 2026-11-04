package com.example.keymapper.injection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbEndpointPolicyTest {
    @Test fun acceptsValidIpv4AndCanonicalizesOctets() {
        assertEquals("192.168.1.20", AdbEndpointPolicy.normalizeHost(" 192.168.001.020 "))
    }

    @Test fun acceptsIpv6Literal() {
        assertEquals("fe80::1", AdbEndpointPolicy.normalizeHost("fe80::1"))
    }

    @Test fun rejectsHostnamesAndShellSyntax() {
        assertNull(AdbEndpointPolicy.normalizeHost("example.local"))
        assertNull(AdbEndpointPolicy.normalizeHost("192.168.1.2; reboot"))
        assertNull(AdbEndpointPolicy.normalizeHost(""))
    }

    @Test fun validatesPorts() {
        assertEquals(37123, AdbEndpointPolicy.parsePort("37123"))
        assertNull(AdbEndpointPolicy.parsePort("0"))
        assertNull(AdbEndpointPolicy.parsePort("65536"))
        assertNull(AdbEndpointPolicy.parsePort("37abc"))
    }

    @Test fun validatesPairingCode() {
        assertEquals("123456", AdbEndpointPolicy.normalizePairingCode(" 123456 "))
        assertNull(AdbEndpointPolicy.normalizePairingCode("12345"))
        assertNull(AdbEndpointPolicy.normalizePairingCode("12345x"))
    }

    @Test fun wirelessPairingRequiresAndroid11OrNewer() {
        assertFalse(AdbEndpointPolicy.supportsWirelessPairing(28))
        assertFalse(AdbEndpointPolicy.supportsWirelessPairing(29))
        assertTrue(AdbEndpointPolicy.supportsWirelessPairing(30))
        assertTrue(AdbEndpointPolicy.supportsWirelessPairing(33))
    }

    @Test fun localEndpointDetectionDoesNotTreatLanPeersAsThisPhone() {
        assertTrue(AdbEndpointPolicy.isLocalInterfaceAddress("127.0.0.1"))
        assertFalse(AdbEndpointPolicy.isLocalInterfaceAddress("192.0.2.254"))
    }

    @Test fun wmSizePrefersOverrideOverPhysicalSize() {
        val parsed = AdbEndpointPolicy.parseWmSize("Physical size: 1080x2400\nOverride size: 1600x900")
        assertEquals(1600 to 900, parsed)
    }

    @Test fun wmSizeUsesPhysicalWhenThereIsNoOverride() {
        assertEquals(1080 to 2400, AdbEndpointPolicy.parseWmSize("Physical size: 1080x2400"))
        assertEquals(1080 to 2400, AdbEndpointPolicy.parseWmSize("Override size: 0x0\nPhysical size: 1080x2400"))
        assertEquals(1080 to 2400, AdbEndpointPolicy.parseWmSize("Override size: 1x1\nPhysical size: 1080x2400"))
        assertNull(AdbEndpointPolicy.parseWmSize("Physical size: 0x0"))
        assertNull(AdbEndpointPolicy.parseWmSize("no size data"))
    }

    @Test fun wmDensityPrefersOverrideAndRejectsOutOfRangeValues() {
        assertEquals(320, AdbEndpointPolicy.parseWmDensity("Physical density: 440\nOverride density: 320"))
        assertEquals(440, AdbEndpointPolicy.parseWmDensity("Physical density: 440"))
        assertEquals(440, AdbEndpointPolicy.parseWmDensity("Override density: 20\nPhysical density: 440"))
        assertNull(AdbEndpointPolicy.parseWmDensity("Physical density: 20"))
    }

    @Test fun diagnosticCommandsAreFixedAndReadOnly() {
        assertEquals(6, AdbEndpointPolicy.diagnosticCommands.size)
        assertTrue(AdbEndpointPolicy.diagnosticCommands.all { it in setOf(
            "id",
            "getprop ro.product.manufacturer",
            "getprop ro.product.model",
            "getprop ro.build.version.sdk",
            "wm size",
            "wm density"
        ) })
        assertFalse(AdbEndpointPolicy.diagnosticCommands.any { it.contains("input ") })
    }
}
