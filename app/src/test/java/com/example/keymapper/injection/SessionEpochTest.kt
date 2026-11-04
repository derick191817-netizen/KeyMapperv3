package com.example.keymapper.injection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEpochTest {
    @Test
    fun newSessionTokenIsCurrent() {
        val epoch = SessionEpoch()

        val token = epoch.begin()

        assertTrue(epoch.isCurrent(token))
    }

    @Test
    fun invalidationRejectsPreviousSession() {
        val epoch = SessionEpoch()
        val oldToken = epoch.begin()

        epoch.invalidate()

        assertFalse(epoch.isCurrent(oldToken))
    }

    @Test
    fun startingNewSessionRejectsOldCallbacks() {
        val epoch = SessionEpoch()
        val oldToken = epoch.begin()

        val newToken = epoch.begin()

        assertFalse(epoch.isCurrent(oldToken))
        assertTrue(epoch.isCurrent(newToken))
    }

    @Test
    fun zeroAndFutureTokensAreRejected() {
        val epoch = SessionEpoch()
        val token = epoch.begin()

        assertFalse(epoch.isCurrent(0L))
        assertFalse(epoch.isCurrent(token + 1L))
    }
}
