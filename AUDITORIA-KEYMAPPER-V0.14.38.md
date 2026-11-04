# Auditoria técnica — KeyMapper 0.14.38

**Versão:** `0.14.38`  
**versionCode:** `54`  
**Base:** KeyMapper 0.14.37  
**Referência funcional:** SS KeyMouse Pro 1.0.76 (XAPK fornecido pelo usuário)

## 1. Objetivo

O requisito central desta versão é impedir que a visão/mira ou o cursor lógico fique preso ao limite físico do display. O movimento do mouse precisa continuar produzindo deslocamento mesmo depois de cruzar as bordas, sem enviar coordenadas touchscreen inválidas para fora da superfície Android.

## 2. Uso do KeyMouse Pro como referência

Foi feita inspeção estática do XAPK/manifesto, DEX, ELF, símbolos dinâmicos, imports e strings do pacote SS KeyMouse Pro 1.0.76. A biblioteca `libsspro_input.so` apresenta a interface JNI `nativeStart`, `nativeStop`, `nativeSetGrabMouse`, `nativeReleaseGrab`, `nativeRefreshDevices`, `nativeCensus`, `nativeRecoverStuckGrab` e `nativeIsRunning`. Imports/strings incluem `epoll`, `eventfd`, `inotify`, `pthread`, `ioctl`, `/dev/input` e `/sys/class/input`.

A biblioteca também expõe uma interface separada de toque (`nativeTouchOpen/Close/IsOpen/Down/Move/Up`) e strings de `/dev/uinput` e `sspro_virt_touch`. Isso é consistente com a separação entre captura dos periféricos físicos e transporte de saída touchscreen virtual.

**Limite da evidência:** trata-se de inspeção estática. Ela não comprova permissões em cada firmware, funcionamento em tempo real ou consumo de eventos por um jogo. O KeyMapper mantém código próprio; nenhum binário nem código proprietário do SS KeyMouse Pro foi incorporado.

## 3. Correção do cursor visual

### Antes

`OverlayManager.onNativeMouseDelta()` acumulava `dx/dy` em `cursorX/cursorY` e aplicava `coerceIn(0f, width/height)`. Depois de atingir a borda, o retículo visual permanecia nela mesmo quando novos deltas continuavam chegando.

### Agora

- `VirtualCursorController` guarda uma coordenada lógica separada das dimensões físicas. O teto `1e300` é uma guarda de representação numérica, não um limite de tela.
- `CursorReanchorPolicy.advanceInto()` calcula a posição desenhável do retículo. Ao cruzar uma margem, o retículo reaparece no ponto de referência e o excedente continua aplicado.
- O cálculo trata X e Y independentemente, suporta deltas que cruzam muitas larguras/alturas e é O(1).
- O caminho `advanceInto()` reutiliza buffers para evitar alocação de objetos a cada evento.
- O mesmo algoritmo é utilizado para captura evdev nativa e fallback Pointer Capture/MotionEvent, evitando que o cursor funcione somente em um backend. O texto da configuração foi atualizado para refletir esse comportamento.

O Android não renderiza um ponteiro fora do viewport físico e um dispositivo touchscreen não deve receber coordenadas fora da sua geometria. “Além da tela” é implementado como movimento relativo lógico ilimitado, com reancoragem visual/toque dentro do display e preservação do excedente.

## 4. Correção da visão/mira

- `LookReanchorPlanner` devolve `remainingDeltaX/Y` sempre que o orçamento de reancoragem do lote é atingido. Não comprime o restante na borda.
- `MappingEngine` limita o trabalho a 64 reancoragens por lote para não monopolizar a thread serial de injeção.
- O delta que sobrou é agendado para continuação após 1 ms. A continuação tem prioridade sobre amostras recentes acumuladas; isso preserva ordem e evita perder movimento.
- A posição lógica só avança pelo trecho consumido no lote atual.
- O timer de inatividade antigo é removido durante a continuação para não finalizar o contato antes de o excedente ser drenado.
- Com zona morta zero, um buffer vazio não é considerado movimento pronto; evita flush contínuo sem eventos.

### Modos disponíveis

| Modo | Comportamento | Limite |
|---|---|---|
| `AUTO` | Toque com reancoragem e preservação do excedente | A troca UP/DOWN pode ser percebida por alguns jogos. |
| `CENTER` | Mesmo mecanismo touchscreen de reancoragem | A continuidade depende do tratamento do jogo para UP/DOWN. |
| `INFINITE` | Envia `SOURCE_MOUSE_RELATIVE` | Só funciona se o destino aceitar essa fonte; não é garantido em jogos de toque. |
| `OFF` | Arrasto touchscreen absoluto limitado | Para deliberadamente na borda. |

O modo `OFF` não é um defeito da versão; é a opção explicitamente limitada. Para não parar na borda, usar `AUTO` (recomendado) ou `CENTER`.

## 5. Arquivos alterados

- `app/src/main/java/com/example/keymapper/input/VirtualCursorController.kt`
- `app/src/main/java/com/example/keymapper/input/CursorReanchorPolicy.kt` (novo)
- `app/src/main/java/com/example/keymapper/overlay/OverlayManager.kt`
- `app/src/main/java/com/example/keymapper/mapping/LookReanchorPlanner.kt`
- `app/src/main/java/com/example/keymapper/mapping/MappingEngine.kt`
- `app/src/main/java/com/example/keymapper/ui/SettingsScreen.kt`
- `app/src/test/java/com/example/keymapper/input/CursorReanchorPolicyTest.kt` (novo)
- `app/src/test/java/com/example/keymapper/input/VirtualCursorControllerTest.kt`
- `app/src/test/java/com/example/keymapper/mapping/LookReanchorPlannerTest.kt`
- `app/build.gradle.kts`, README, workflow e changelog.

## 6. Testes executados

Harness isolado com `kotlinc` para os três módulos independentes de Android:

- `LookReanchorPlannerTest`: 8 testes.
- `VirtualCursorControllerTest`: 9 testes.
- `CursorReanchorPolicyTest`: 9 testes.

**Resultado: 26/26 passaram.** Cobrem entradas inválidas, posição lógica além da tela, deltas extremos finitos, reancoragem nos dois sentidos, cruzamento de várias dimensões, movimentos diagonais e preservação do excedente em lotes limitados.

## 7. Testes ainda necessários

Não foram executados nesta sessão:

- `:app:lintDebug`;
- `:app:testDebugUnitTest` via Gradle completo;
- `:app:assembleDebug` / geração de APK;
- build NDK completo e integração Kotlin/JNI em Android;
- instalação, captura/injeção real e teste no Samsung Note 20 Ultra Android 13;
- teste DeX/monitor externo e confirmação de qual display consome os eventos;
- teste em Free Fire ou outro jogo para verificar a resposta real às transições UP/DOWN.

O ambiente atual não tem `ANDROID_HOME`/`ANDROID_SDK_ROOT` configurados e não tem Gradle 8.8 em cache. Não foi possível concluir um build Android nesta execução. Os testes puros não substituem essas verificações.

## 8. Parecer

A lógica isolada de reancoragem do cursor e de preservação de deltas de câmera passou nos testes previstos. O comportamento agora não depende de mover um contato touchscreen para fora da tela: o motor preserva o movimento relativo e recria o contato dentro da geometria válida. A integração Android, a latência sob carga e a resposta de jogos específicos permanecem pendentes de build e teste real.
