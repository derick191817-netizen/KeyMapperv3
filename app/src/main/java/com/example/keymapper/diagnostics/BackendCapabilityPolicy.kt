package com.example.keymapper.diagnostics

/**
 * Política conservadora para classificar backends nativos.
 * Evidência de arquivo presente, permissão Shizuku ou metadado de /dev nunca
 * substitui inicialização real e teste end-to-end.
 */
enum class BackendCapabilityLevel {
    NOT_IMPLEMENTED,
    MODULE_PRESENT_NOT_PROBED,
    MODULE_PRESENT_NOT_INITIALIZED,
    INITIALIZED_NOT_VERIFIED,
    VERIFIED_ON_DEVICE
}

data class BackendCapabilityAssessment(
    val level: BackendCapabilityLevel,
    val description: String
)

object BackendCapabilityPolicy {
    /**
     * Null significa que esta fonte de diagnóstico não consultou o estado.
     * Não converter ausência de observação em false: isso produz falso negativo.
     */
    fun assess(
        nativeModulePresent: Boolean,
        backendInitialized: Boolean?,
        endToEndVerified: Boolean?
    ): BackendCapabilityAssessment = when {
        !nativeModulePresent -> BackendCapabilityAssessment(
            BackendCapabilityLevel.NOT_IMPLEMENTED,
            "módulo nativo não incluído no aplicativo"
        )
        backendInitialized == null -> BackendCapabilityAssessment(
            BackendCapabilityLevel.MODULE_PRESENT_NOT_PROBED,
            "módulo presente; inicialização não consultada por esta sondagem"
        )
        !backendInitialized -> BackendCapabilityAssessment(
            BackendCapabilityLevel.MODULE_PRESENT_NOT_INITIALIZED,
            "módulo presente, mas backend não inicializado"
        )
        endToEndVerified != true -> BackendCapabilityAssessment(
            BackendCapabilityLevel.INITIALIZED_NOT_VERIFIED,
            "backend inicializado, mas fluxo de entrada/saída não foi verificado end-to-end"
        )
        else -> BackendCapabilityAssessment(
            BackendCapabilityLevel.VERIFIED_ON_DEVICE,
            "fluxo end-to-end validado neste dispositivo"
        )
    }

    /** Um grant do Shizuku, isoladamente, não concede acesso a nós de dispositivo do kernel. */
    fun shizukuGrantAloneConfirmsKernelNodeAccess(): Boolean = false
}
