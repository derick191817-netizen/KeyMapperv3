package com.example.keymapper.injection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStartPolicyTest {
    @Test fun firstStartCreatesANewSession() {
        assertTrue(SessionStartPolicy.shouldCreateFreshSession(isActive = false, hasInputCallback = false))
    }

    @Test fun repeatedStartKeepsTheRegisteredCallbackAndEpoch() {
        assertFalse(SessionStartPolicy.shouldCreateFreshSession(isActive = true, hasInputCallback = true))

        val epoch = SessionEpoch()
        val registeredCallbackToken = epoch.begin()
        if (SessionStartPolicy.shouldCreateFreshSession(isActive = true, hasInputCallback = true)) {
            epoch.begin()
        }
        assertEquals(registeredCallbackToken, epoch.currentToken())
        assertTrue(epoch.isCurrent(registeredCallbackToken))
    }

    @Test fun activeSessionWithoutCallbackIsRecovered() {
        assertTrue(SessionStartPolicy.shouldCreateFreshSession(isActive = true, hasInputCallback = false))

        val epoch = SessionEpoch()
        val staleCallbackToken = epoch.begin()
        if (SessionStartPolicy.shouldCreateFreshSession(isActive = true, hasInputCallback = false)) {
            epoch.begin()
        }
        assertFalse(epoch.isCurrent(staleCallbackToken))
    }

    @Test fun inactiveServiceNeverReusesAStaleCallback() {
        assertTrue(SessionStartPolicy.shouldCreateFreshSession(isActive = false, hasInputCallback = true))
    }
}
