package com.example.keymapper.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PerformancePolicyTest {
    @Test fun highPerformanceAddsNoSlack() {
        assertEquals(0, PerformancePolicy.extraSlackMs(mode = 1, totalRamGb = 2))
    }

    @Test fun economyAddsFiveMilliseconds() {
        assertEquals(5, PerformancePolicy.extraSlackMs(mode = 2, totalRamGb = 12))
        assertEquals(9, PerformancePolicy.effectiveLookIntervalMs(4, 2, 12))
        assertEquals(9, PerformancePolicy.applySlack(4, 5))
    }

    @Test fun balancedAddsSlackOnlyOnLowMemoryDevices() {
        assertEquals(5, PerformancePolicy.extraSlackMs(mode = 0, totalRamGb = 3))
        assertEquals(0, PerformancePolicy.extraSlackMs(mode = 0, totalRamGb = 4))
    }

    @Test fun intervalIsBounded() {
        assertEquals(20, PerformancePolicy.effectiveLookIntervalMs(20, 2, 12))
        assertEquals(1, PerformancePolicy.effectiveLookIntervalMs(-5, 1, 12))
    }
}
