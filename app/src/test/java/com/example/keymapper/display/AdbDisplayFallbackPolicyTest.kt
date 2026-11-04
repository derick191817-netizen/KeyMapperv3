package com.example.keymapper.display

import org.junit.Assert.assertEquals
import org.junit.Test

class AdbDisplayFallbackPolicyTest {
    @Test fun fillsOnlyMissingPrimaryDisplayMetrics() {
        val result = AdbDisplayFallbackPolicy.resolve(
            native = AdbDisplayFallbackPolicy.Values(0, 0, 0),
            adb = AdbDisplayFallbackPolicy.Values(1600, 900, 280),
            isPrimaryDisplay = true,
            isLocalEndpoint = true,
            snapshotAgeMs = 5_000
        )
        assertEquals(AdbDisplayFallbackPolicy.Values(1600, 900, 280), result)
    }

    @Test fun nativeMetricsAlwaysWinWhenValid() {
        val native = AdbDisplayFallbackPolicy.Values(1080, 2400, 420)
        val result = AdbDisplayFallbackPolicy.resolve(
            native = native,
            adb = AdbDisplayFallbackPolicy.Values(1600, 900, 280),
            isPrimaryDisplay = true,
            isLocalEndpoint = true,
            snapshotAgeMs = 1_000
        )
        assertEquals(native, result)
    }

    @Test fun adbNeverOverridesExternalDisplay() {
        val native = AdbDisplayFallbackPolicy.Values(1600, 900, 240)
        val result = AdbDisplayFallbackPolicy.resolve(
            native = native,
            adb = AdbDisplayFallbackPolicy.Values(1080, 2400, 420),
            isPrimaryDisplay = false,
            isLocalEndpoint = true,
            snapshotAgeMs = 1_000
        )
        assertEquals(native, result)
    }

    @Test fun staleOrFutureSnapshotIsIgnored() {
        val native = AdbDisplayFallbackPolicy.Values(0, 0, 0)
        val adb = AdbDisplayFallbackPolicy.Values(1080, 2400, 420)
        assertEquals(native, AdbDisplayFallbackPolicy.resolve(native, adb, true, true, 901_000))
        assertEquals(native, AdbDisplayFallbackPolicy.resolve(native, adb, true, true, -1))
    }

    @Test fun snapshotAgeBoundaryIsInclusiveAndExpiresImmediatelyAfterward() {
        val native = AdbDisplayFallbackPolicy.Values(0, 0, 0)
        val adb = AdbDisplayFallbackPolicy.Values(1080, 2400, 420)
        assertEquals(1080, AdbDisplayFallbackPolicy.resolve(native, adb, true, true, 900_000).width)
        assertEquals(native, AdbDisplayFallbackPolicy.resolve(native, adb, true, true, 900_001))
    }

    @Test fun invalidAdbMetricsAreRejected() {
        val native = AdbDisplayFallbackPolicy.Values(0, 0, 0)
        val invalid = AdbDisplayFallbackPolicy.Values(1, 1, 5)
        assertEquals(native, AdbDisplayFallbackPolicy.resolve(native, invalid, true, true, 1_000))
    }

    @Test fun remoteAdbEndpointNeverChangesLocalDisplayMetrics() {
        val native = AdbDisplayFallbackPolicy.Values(0, 0, 0)
        val remote = AdbDisplayFallbackPolicy.Values(2560, 1440, 320)
        assertEquals(native, AdbDisplayFallbackPolicy.resolve(native, remote, true, false, 1_000))
    }
}
