package com.example.keymapper.diagnostics

/** Snapshot mínimo e independente do Android para escolher uma orientação de diagnóstico. */
data class InputHealthState(
    val hasMouse: Boolean,
    val captureRequested: Boolean,
    val windowFocused: Boolean,
    val captureConfirmed: Boolean,
    val mouseReceived: Long,
    val mouseReceivedWithoutPointerCapture: Long,
    val mouseProcessed: Long,
    val mouseForwarded: Long,
    val injectionSuccesses: Long,
    val injectionFailures: Long
)

/**
 * Prioriza o estado atual da captura antes de contadores históricos. Assim, um sucesso antigo
 * não esconde que a janela perdeu foco ou que a captura foi desativada nesta sessão.
 */
object InputHealthHintPolicy {
    fun hint(state: InputHealthState): String = when {
        !state.hasMouse ->
            "O Android não lista um mouse. Verifique a conexão e se o periférico aparece nas configurações do sistema."
        !state.captureRequested ->
            "Mouse listado, mas a captura está desativada. Ative a captura e inicie o serviço antes de testar no jogo."
        !state.windowFocused ->
            "Mouse listado, mas a janela de captura não está focada. Pointer Capture depende do foco da própria janela."
        state.captureRequested && !state.captureConfirmed && state.mouseReceivedWithoutPointerCapture == 0L ->
            "Mouse listado, mas Pointer Capture ainda não foi confirmado e nenhuma amostra relativa chegou. A janela de captura pode não obter foco neste jogo/firmware."
        state.mouseReceived > 0L && state.mouseProcessed == 0L ->
            "O mouse já gerou eventos, mas o motor ainda não registrou processamento. Confira o serviço e o log de entrada."
        state.mouseProcessed > 0L && state.mouseForwarded == 0L ->
            "O motor processou movimento, mas nenhum lote foi encaminhado ao injetor. Verifique perfil, modo de câmera e estado do serviço."
        state.mouseForwarded > 0L && state.injectionSuccesses == 0L && state.injectionFailures > 0L ->
            "Há lotes encaminhados, mas as chamadas de injeção falharam. Verifique Shizuku, autorização e o estado do Binder."
        state.mouseForwarded > 0L && state.injectionSuccesses == 0L ->
            "O motor encaminhou lotes, mas ainda não há retorno registrado da injeção. Confira o perfil e o modo de saída."
        state.injectionSuccesses > 0L ->
            "Fluxo observado até o InputManager: ${state.mouseReceived} entradas, ${state.mouseProcessed} processadas e ${state.injectionSuccesses} chamadas aceitas. Isso não confirma que o jogo recebeu os toques."
        state.mouseReceivedWithoutPointerCapture > 0L ->
            "Entrada relativa recebida sem Pointer Capture. O modo é limitado e não representa captura global do mouse."
        else ->
            "Mouse listado, mas ainda não há movimento relativo. Verifique se o dispositivo envia eventos relativos e se o serviço está ativo."
    }
}
