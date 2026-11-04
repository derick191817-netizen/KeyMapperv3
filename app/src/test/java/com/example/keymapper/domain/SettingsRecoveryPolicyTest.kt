package com.example.keymapper.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRecoveryPolicyTest {
    @Test
    fun absentProfilesRemainCompatibleWithLegacySettings() {
        assertFalse(SettingsRecoveryPolicy.shouldBackupInvalidProfiles(fieldPresent = false, valueIsObject = false))
    }

    @Test
    fun objectProfilesDoNotTriggerBackup() {
        assertFalse(SettingsRecoveryPolicy.shouldBackupInvalidProfiles(fieldPresent = true, valueIsObject = true))
    }

    @Test
    fun nullAndOtherNonObjectProfilesTriggerBackupAndSanitization() {
        assertTrue(SettingsRecoveryPolicy.shouldBackupInvalidProfiles(fieldPresent = true, valueIsObject = false))
    }

    @Test
    fun absentArrayRemainsCompatibleWithLegacySettings() {
        assertFalse(SettingsRecoveryPolicy.shouldBackupInvalidArray(fieldPresent = false, valueIsArray = false))
    }

    @Test
    fun validArrayDoesNotTriggerBackup() {
        assertFalse(SettingsRecoveryPolicy.shouldBackupInvalidArray(fieldPresent = true, valueIsArray = true))
    }

    @Test
    fun nullAndWrongTypeArraysTriggerBackup() {
        assertTrue(SettingsRecoveryPolicy.shouldBackupInvalidArray(fieldPresent = true, valueIsArray = false))
    }
}
