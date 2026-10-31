package com.example.keymapper.input

/** Backends are capabilities, not assumptions inferred from Shizuku/ADB availability. */
enum class CaptureBackendKind { ANDROID_POINTER_CAPTURE, PRIVILEGED_EVDEV, NONE }
enum class CaptureBackendReadiness { READY, WAITING_FOR_WINDOW_FOCUS, WAITING_FOR_NATIVE_COMPONENT, WAITING_FOR_DEVICE_ACCESS, UNSUPPORTED }

data class CaptureBackendEnvironment(
    val sdkInt: Int,
    val windowFocused: Boolean,
    val nativeComponentInstalled: Boolean = false,
    val evdevAccessConfirmed: Boolean = false,
    val deviceGrabConfirmed: Boolean = false
)

data class CaptureBackendDecision(
    val kind: CaptureBackendKind,
    val readiness: CaptureBackendReadiness,
    val globalCapture: Boolean,
    val explanation: String
)

/** Shizuku/ADB alone never proves access to /dev/input or EVIOCGRAB. */
object CaptureBackendPolicy {
    fun evaluate(environment: CaptureBackendEnvironment): CaptureBackendDecision {
        if (environment.nativeComponentInstalled) {
            if (!environment.evdevAccessConfirmed || !environment.deviceGrabConfirmed) {
                return CaptureBackendDecision(
                    CaptureBackendKind.PRIVILEGED_EVDEV,
                    CaptureBackendReadiness.WAITING_FOR_DEVICE_ACCESS,
                    false,
                    "Componente nativo presente, mas acesso evdev e grab ainda não foram confirmados."
                )
            }
            return CaptureBackendDecision(
                CaptureBackendKind.PRIVILEGED_EVDEV,
                CaptureBackendReadiness.READY,
                true,
                "Acesso evdev e grab confirmados pelo próprio backend."
            )
        }
        if (environment.sdkInt < 26) return CaptureBackendDecision(
            CaptureBackendKind.NONE, CaptureBackendReadiness.UNSUPPORTED, false,
            "Pointer Capture requer Android 8.0 (API 26) ou superior."
        )
        if (!environment.windowFocused) return CaptureBackendDecision(
            CaptureBackendKind.ANDROID_POINTER_CAPTURE, CaptureBackendReadiness.WAITING_FOR_WINDOW_FOCUS, false,
            "Aguardando foco; Pointer Capture pertence à janela solicitante e não é global."
        )
        return CaptureBackendDecision(
            CaptureBackendKind.ANDROID_POINTER_CAPTURE, CaptureBackendReadiness.READY, false,
            "Pointer Capture disponível para a janela focada; não equivale a captura global."
        )
    }
}
