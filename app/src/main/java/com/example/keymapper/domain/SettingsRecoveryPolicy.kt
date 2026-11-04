package com.example.keymapper.domain

/** Regras puras para decidir se uma coleção persistida precisa de backup de recuperação. */
object SettingsRecoveryPolicy {
    /** O campo ausente é compatível com versões antigas; qualquer tipo presente que não seja objeto é inválido. */
    fun shouldBackupInvalidProfiles(fieldPresent: Boolean, valueIsObject: Boolean): Boolean =
        fieldPresent && !valueIsObject

    /** Campos de coleção ausentes são compatíveis com versões antigas; null/tipo incorreto não. */
    fun shouldBackupInvalidArray(fieldPresent: Boolean, valueIsArray: Boolean): Boolean =
        fieldPresent && !valueIsArray
}
