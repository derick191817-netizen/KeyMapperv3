# Auditoria técnica — KeyMapper 0.14.32

**Base:** `0.14.31` (fonte de trabalho `km31_work`)  
**VersionName / versionCode:** `0.14.32` / `48`  
**Foco:** robustez do leitor evdev nativo, hot-plug, liberação de periféricos e recuperação do worker Shizuku.

## 1. Arquitetura confirmada no código

1. O app principal inicia `ShizukuInputCaptureBridge` durante o setup do `MappingService`.
2. O bridge vincula `KeyMapperShellService` como UserService privilegiado e transporta lotes compactos via AIDL.
3. `KeyMapperShellService` carrega `libkeymapper_evdev.so` e inicia o leitor JNI.
4. `evdev_capture.cpp` varre `/dev/input/event*`, identifica dispositivos com capacidades de mouse relativo e/ou teclado e tenta `EVIOCGRAB`.
5. O worker multiplexa descritores com `epoll`, usa `eventfd` para alterações de estado/encerramento, lê eventos, agrupa lotes e envia amostras via `IKeyMapperInputCallback`.
6. `MappingService.handleNativeInputBatch()` integra os registros com o fluxo existente do `MappingEngine`.
7. A saída continua sendo `TouchInjector`/`ShizukuInjector` via `InputManager`. O backend não cria touchscreen virtual `uinput`.

## 2. Alterações desta versão

### Captura event-driven

- Substituído o loop `poll` + pipe por `epoll` + `eventfd`, como no desenho observado no KeyMouse Pro.
- Os descritores de dispositivos são registrados/removidos do epoll quando conectados/desconectados.

### Hot-plug

- Adicionado watcher `inotify` para `IN_CREATE`, `IN_DELETE`, `IN_MOVED_FROM`, `IN_MOVED_TO` e `IN_ATTRIB` em `/dev/input`.
- Os eventos do diretório solicitam uma nova varredura no próximo ciclo do worker.
- A varredura periódica permanece ativa: a cada 5 segundos com watcher funcional (como conferência de segurança) e a cada 1 segundo quando `inotify` está indisponível.

### Recuperação do worker

- O worker coloca `g_running=false` ao encerrar normalmente ou após erro de `epoll_wait`/leitura.
- Foi exposto `nativeIsCaptureRunning()` para que o serviço Kotlin não trate um thread morto como backend ativo.
- `startInputCapture()` e `setInputCaptureEnabled()` podem reiniciar o worker pelo mesmo UserService.
- Antes do restart, a JNI junta o worker anterior e fecha `eventfd`, `epoll`, watcher e referências globais antigas.
- Se `epoll_ctl(ADD)` falhar para um periférico, o descritor é fechado e não aparece como capturado.
- EOF ou erro definitivo de leitura remove o dispositivo, envia `SYN_DROPPED` e agenda nova enumeração.
- Quando o dispositivo anuncia roda hi-res, o caminho legado correspondente é ignorado para não duplicar scroll; valores hi-res são normalizados em frações de 1/120 de detente.
- Heartbeat periódico informa eventos lidos, eventos enfileirados ao callback, perdas de sincronização, erros de leitura e dispositivos removidos. Assim o diagnóstico distingue ausência de eventos físicos de falha posterior no motor/injeção.

### Liberação segura

- Se `EVIOCGRAB(0)` falhar ao pausar, o fd é fechado imediatamente; o fechamento do descritor faz o kernel liberar a captura.
- Descritores fechados são removidos do snapshot e podem ser reabertos na varredura seguinte.
- `releaseDevice()` continua fechando sempre os descritores no desligamento e no hot-unplug.

### Bridge Shizuku

- Uma inicialização nativa que falha pode ser repetida pelo Binder atual, sem criar vínculo duplicado.
- O bridge serializa solicitações de start enquanto uma chamada Binder ainda está pendente; a última solicitação de pausa/retomada é aplicada após o start terminar.
- Timeout e Binder nulo agora limpam o vínculo e permitem uma nova tentativa de bind.
- `onBindingDied` limpa o vínculo morto; `onServiceDisconnected` mantém a possibilidade de reconexão automática do mesmo vínculo e informa o fallback.
- `setEnabled()` tenta inicializar o leitor de novo quando o serviço está conectado mas a captura não está ativa.

## 3. Segurança e limites

- `EVIOCGRAB` é exclusivo: enquanto ativado, o dispositivo físico pode deixar de entregar eventos a outros consumidores. Por isso, pausar/encerrar fecha o fd se a liberação explícita falhar.
- A capacidade real continua dependente de permissões do dispositivo e políticas SELinux da ROM Samsung; Shizuku UID 2000 não equivale a root.
- `inotify` é opcional; se negado ou indisponível, há fallback periódico.
- `Pointer Capture` permanece fallback por classe de dispositivo quando o evdev não captura mouse e teclado em conjunto.
- A captura física e a injeção são caminhos separados. O output é ainda `InputManager` via Shizuku.
- `/dev/uinput` não é implementado nesta versão; não há dispositivo virtual touchscreen.
- O retorno de `injectInputEvent` registra a aceitação pelo InputManager, não a confirmação de que o app de destino processou o evento.

## 4. Verificações executadas

| Verificação | Resultado |
|---|---|
| Compilar `evdev_capture.cpp` com Clang C++17, `-Wall -Wextra -Werror=return-type` e JNI headers JDK 21 | Aprovado |
| Linkar biblioteca host para smoke test JNI | Aprovado |
| JNI start / isRunning / pause / resume / stop em três ciclos e restart final | Aprovado |
| Callback JNI de estado durante inicialização/encerramento | Aprovado; não houve periféricos para gerar lote de eventos físicos |
| Kotlin do bridge Shizuku compilado isoladamente contra stubs Android/AIDL | Aprovado; não substitui build Android real |
| Normalização de roda hi-res via Kotlin standalone | Aprovado |
| 26 assertions de políticas puras existentes | Aprovado (26/26) |

O smoke test foi executado num host Linux, não no Android. O host reportou zero periféricos evdev compatíveis; portanto, ele valida ciclo de vida JNI e não confirma `EVIOCGRAB` ou entrada física no Samsung.

## 5. Verificações não executadas

- `./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`: não concluído; wrapper Gradle 8.8 não pôde resolver `services.gradle.org` (`UnknownHostException`).
- Android SDK/NDK oficial não está configurado neste ambiente.
- Sem APK gerado.
- Sem teste físico no Samsung Galaxy Note 20 Ultra SM-N986B, Android 13, One UI ou DeX.
- Sem prova real de que a ROM concede `/dev/input` + `EVIOCGRAB` à sessão Shizuku usada no aparelho.
- Sem teste de compatibilidade de `MotionEvent` sintetizado no aplicativo de destino.

## 6. Veredito

A 0.14.32 melhora a robustez do backend de captura evdev em hot-plug, encerramento, erro de worker, registro no epoll, leitura/desconexão e liberação do dispositivo. Também corrige estados pendentes do bridge Shizuku e encaminha scroll hi-res sem duplicar o canal legado. O código nativo compila em host e o ciclo JNI foi exercitado. Isso não demonstra que o firmware Samsung concede captura global, nem que a saída touchscreen via InputManager é consumida pelo aplicativo de destino. O app ainda não pode ser declarado totalmente jogável sem build Android e teste ponta a ponta no dispositivo alvo.
