package com.example.keymapper.diagnostics

/**
 * Evidência estática extraída dos APKs/XAPK de referência reenviados em 31/10/2026.
 * Strings/símbolos descrevem o que existe no pacote; não provam autorização ou runtime.
 */
object ReferenceBackendEvidence {
    fun report(): String = buildString {
        appendLine("ANÁLISE ESTÁTICA DOS PACOTES DE REFERÊNCIA (31/10/2026; não é estado runtime)")
        appendLine("Panda Mouse Pro 11 (package com.panda.mouse; min API 23, target 36): SHA-256 da amostra reenviada no ZIP = f09f329a3661c8ec0d9d4845b5ac7096e59ada1527dd2e59a70ab43ea27910fb; corresponde byte a byte à amostra Panda já inspecionada anteriormente, portanto o ZIP novo não contém uma release diferente.")
        appendLine("Panda: assets contêm com.panda.mouseinject.dex (477.044 bytes), com.panda.mouselibinject.so (ELF32 ARM, 40.892 bytes), x64com.panda.mouselibinject.so (ELF64 AArch64, 61.544 bytes) e x86_64com.panda.mouselibinject.so (ELF64 x86-64, 60.728 bytes). Não são bibliotecas colocadas em lib/<abi>/.")
        appendLine("Panda: imports das variantes incluem open/ioctl/poll/read/write/inotify, opendir/readdir e popen/pclose; strings incluem /dev/input e uma tabela ampla de códigos Linux ABS_*, REL_* e BTN_*. O binário também contém strings de opções de inspeção/contagem/diagnóstico de eventos, mas isso não identifica sozinho o fluxo de execução usado pelo app.")
        appendLine("Panda: DEX principal e com.panda.mouseinject.dex referenciam GetEventService, InjectService, NativeDeviceInfo, CursorInputMapper, TouchInputMapper, KeyboardInputMapper e JoystickInputMapper; contém fallback MODE_POINTER_RELATIVE -> MODE_POINTER e métodos com nomes invokeInjectInputEvent1111/2222/3333. Os nomes não revelam, por si sós, o conteúdo integral ou a semântica dos métodos.")
        appendLine("Panda: assets/activate.sh e assets/inject2.sh mostram uma arquitetura de preparação de auxiliares em /data/local/tmp/2 e inicialização de InjectService por app_process com DEX separado para componentes mouse/touch/gamepad. O script também tenta alterar uma configuração global de toques não confiáveis; o KeyMapper NÃO reproduz essa alteração nem copia scripts/binários do fornecedor.")
        appendLine("Panda: os nomes de serviços e imports indicam componentes distintos de leitura e injeção, mas não demonstram o comando de inicialização, a autorização efetiva ou a entrega no firmware.")
        appendLine("SS KeyMouse Pro 1.0.76 (com.sousasantoslogic.sspro; min API 32, target 36): libsspro_input.so está no split config.arm64_v8a.apk do XAPK, e o ELF é AArch64; a presença no split precisa ser considerada na instalação por ABI.")
        appendLine("SS: JNI exporta nativeStart/nativeStop, nativeSetGrabMouse/nativeReleaseGrab, nativeRefreshDevices/nativeCensus/nativeRecoverStuckGrab e nativeTouchOpen/Down/Move/Up.")
        appendLine("SS: imports/strings incluem pthread_create/join, eventfd, epoll_create1/ctl/wait, inotify, ioctl, /dev/input, /sys/class/input e /dev/uinput; há mensagens de EBUSY, fd morto, grab falho e recuperação, além da criação de sspro_virt_touch.")
        appendLine("SS: strings de heartbeat mencionam contagem de dispositivos/mouses, relAbs, grabbed, grabFail, errno, reauth, reads, keys, moves, stall e healed. Isso demonstra telemetria prevista no binário, não prova que os valores foram observados em execução.")
        appendLine("SS: nativePinThreadToCpus e nativePinShellHotThread existem, mas afinidade de CPU não deve ser copiada sem medição: pode aumentar consumo e piorar escalonamento em determinados SoCs.")
        appendLine("GG Mouse (com.zjx.ztezscreenshot 1.18.0; min API 27, target 33): libevent_bridge.so existe em quatro ABIs no APK: arm64-v8a, armeabi-v7a, x86 e x86_64.")
        appendLine("GG: a biblioteca exporta JNI EventGrabber: getSupportedEventsNative, getDeviceNameNative, checkIsMouseInputDeviceNative, checkIsKeyboardInputDeviceNative, checkIsTouchInputDeviceNative, start/stopGrabbingEventsFromDeviceNative e start/stopGrabbingTouchEventsNative.")
        appendLine("GG: símbolos C++ internos incluem EventGrabber::startPolling, startPollingLoop, addPollingDescriptor, deletePollingDescriptor e isPolling; strings/imports confirmam epoll_create1/ctl/wait, eventfd, open/ioctl/read e pthread.")
        appendLine("GG: DEX/Manifest incluem EventGrabber, EventBridge, EventInjector, ADBConnector, classes ADB cabeada/sem fio/root/Shizuku, ShellProcessContentProvider e referência a app_process em shell; existe também AdbServerService em processo separado sixo.server.")
        appendLine("GG: isso sustenta a arquitetura de serviço/processo auxiliar com ponte nativa, mas os arquivos estáticos não provam qual caminho de autorização foi usado numa sessão nem se o grab teve sucesso.")
        appendLine("SS e GG demonstram módulos de captura nativa distintos; Panda demonstra biblioteca embarcada com leitores e serviços dedicados. Nenhum binário de referência foi incorporado ao KeyMapper.")
        appendLine("Limite: símbolo JNI, import de ioctl, /dev/input visível ou autorização Shizuku/ADB não provam permissão de abrir/grabar o dispositivo, escrever em uinput ou entregar eventos ao jogo.")
        appendLine("KeyMapper 0.14.13: Pointer Capture continua limitado à janela focada; o app ainda não possui backend evdev/uinput próprio nem processo auxiliar de captura nativa. O estado físico é agregado por deviceId, teclas/botões compartilhados só são liberados quando o último dispositivo os solta, movimentos/scroll pendentes da origem removida são descartados, o relógio de sensibilidade e o acumulador de scroll são independentes por mouse.")
        appendLine("KeyMapper 0.14.13: estados relativos agora separam RELATIVE_ACTIVE (Pointer Capture confirmado) de RELATIVE_FALLBACK_ACTIVE (eixos relativos recebidos por generic MotionEvent sem Pointer Capture). O timeout de captura não gera erro falso quando a rota relativa alternativa já está entregando amostras.")
        appendLine("KeyMapper 0.14.13: a expiração de movimento >50 ms é verificada em cada lote, inclusive quando há menos de 256 eventos; a fila retorna rapidamente sem compactar se sua amostra de mouse mais antiga ainda for recente; a primeira amostra após pausa longa reinicia corretamente a janela temporal.")
        appendLine("Conclusão: captura global e saída uinput nativa continuam NÃO IMPLEMENTADAS/NÃO VALIDADAS no KeyMapper.")
    }
}
