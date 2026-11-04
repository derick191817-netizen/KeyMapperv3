# Auditoria técnica — KeyMapper 0.14.37

## 1. Base e objetivo

- Base: `KeyMapper-v0.14.36-KEYMOUSE-ROBUSTNESS-SOURCE.zip`.
- Objetivo: corrigir o gerenciamento das transições físicas `EV_KEY` no caminho evdev quando há mais de um mouse/teclado conectado e quando um dispositivo perde sincronização ou desconecta.
- Escopo preservado: captura evdev por Shizuku/JNI, saída touchscreen uinput opcional no display principal, InputManager para fallback/DeX, mapeamentos existentes, HUD, perfis, modo câmera e parâmetros de mira/disparo.

## 2. Defeito identificado

`MappingService.processNativeInputBatch()` encaminhava diretamente cada tecla e botão para `handleKey()`/`MappingEngine.onMouseButton()`, apesar de o registro transportado conter `deviceId`. Já existia `PhysicalInputState`, mas era utilizado apenas pelo caminho MotionEvent do `CaptureView`. Assim, no caminho nativo, um `KEY_UP` de um dispositivo podia encerrar uma tecla que ainda estava pressionada em outro dispositivo.

O tratamento anterior de `SYN_DROPPED` chamava `MappingEngine.releaseAll()`, que limpava o motor inteiro mesmo quando apenas um dispositivo havia perdido eventos. Isso podia interromper movimento, teclas, joystick, botões e contatos acionados por outros periféricos ainda conectados.

## 3. Alterações implementadas

### 3.1 Agregação nativa por dispositivo

- `MappingService` ganhou uma instância dedicada de `PhysicalInputState`, pertencente ao looper principal que processa os lotes nativos.
- A instância nativa é independente da instância MotionEvent mantida no `CaptureView`; os IDs nativos evdev e os IDs Android não são misturados.
- Teclas são convertidas para keycodes Android e agregadas globalmente. DOWN só é encaminhado quando o primeiro dispositivo pressiona; UP só é encaminhado quando o último dispositivo solta.
- Botões evdev são mapeados para bits de botão e agregados com a mesma regra, incluindo mouses que compartilham botão primário/secundário. O estado mantém também o código Linux físico, pois códigos distintos (por exemplo `BTN_SIDE` e `BTN_BACK`) podem mapear para o mesmo bit Android; o UP de um alias não libera o outro.
- Repetições DOWN/UP do mesmo dispositivo não emitem novas transições. O autorepeat evdev (`value == 2`) continua ignorado.
- `PhysicalInputState.updateButtonCode()` aplica uma transição rastreando `(deviceId, sourceCode, buttonBit)`, sem depender de snapshot completo do driver, e rejeita código físico inválido, zero, máscaras com múltiplos bits e bits fora de `KNOWN_BUTTON_MASK`.

### 3.2 Remoção seletiva e reset global

- `SYN_DROPPED` invoca `engine.onInputDeviceRemoved(deviceId)`, limpa o estado físico daquele dispositivo e envia apenas os UP globais necessários. O `releaseAll()` global foi removido desse caminho.
- A remoção de um periférico não interrompe tecla/botão que outro dispositivo ainda mantém pressionado.
- Um helper central `releaseAllMappedInputs()` limpa o bookkeeping nativo e chama `MappingEngine.releaseAll()` nos contextos de reset global: falha do injetor/Shizuku, captura perdida, falha de setup, rebuild/troca de display, edição, menu rápido, pause/resume e `onDestroy`.
- A callback de estado mantém `nativeCaptureEnabled`; uma transição de ativo para inativo aciona reset global de contingência. Isso cobre a desconexão do UserService ou término do worker quando não há garantia de entrega de um evento de remoção para cada periférico.
- O estado de atalhos de serviço correspondente às teclas nativas limpas também é removido para não manter edit/pause shortcut preso após uma transição global.

## 4. Arquivos alterados

- `app/src/main/java/com/example/keymapper/input/PhysicalInputState.kt`
- `app/src/main/java/com/example/keymapper/service/MappingService.kt`
- `app/src/test/java/com/example/keymapper/input/PhysicalInputStateTest.kt`
- `app/build.gradle.kts`
- `.github/workflows/build.yml`
- `README.md`
- `CHANGELOG-v0.14.37.md`

## 5. Testes adicionados e executados

- Tecla igual pressionada em dois teclados: o primeiro UP não libera a tecla global; o segundo UP libera.
- Botão igual pressionado em dois mouses: a remoção/liberação do primeiro não envia UP global enquanto o segundo mantém o botão.
- Códigos físicos `BTN_SIDE` e `BTN_BACK` no mesmo dispositivo, ambos convertidos para o bit `BUTTON_BACK`: o primeiro UP não solta o botão enquanto o alias ainda estiver pressionado.
- `clearDevice()` retorna somente estados que não permanecem em outros dispositivos.
- Remoção do último dispositivo retorna as teclas/botões que precisam ser liberados.
- `updateButton()` rejeita bit zero, máscara multi-bit e bits não reconhecidos.
- Os 14 métodos de `PhysicalInputStateTest.kt` foram executados em harness isolado com `kotlinc` e shim mínima da API JUnit; todos passaram, incluindo o teste aleatório de 10.000 transições.
- Um segundo harness executou cinco cenários explícitos, incluindo aliases de botões, e terminou com `PASS: 5 lifecycle/aggregation scenarios`.

## 6. Limitações de validação

- `./gradlew --no-daemon :app:testDebugUnitTest --stacktrace --console=plain` foi tentado. O Gradle Wrapper tentou baixar Gradle 8.8, mas a resolução DNS para `services.gradle.org` falhou (`UnknownHostException`).
- Não foi possível executar Android Lint, JUnit do Gradle, `assembleDebug`, instalação ou testes no Note 20 Ultra.
- A compilação isolada de `PhysicalInputState.kt` com `MouseButtonStatePolicy.kt` valida essa classe e a lógica pura; não equivale à compilação integrada de `MappingService` com Android SDK.
- As verificações de runtime multidevice ainda precisam ser executadas em aparelho real: dispositivos que compartilham teclas/botões, hot-unplug durante DOWN, `SYN_DROPPED`, pause/resume, perda de foco e troca DeX.

## 7. Critérios para homologar

1. `:app:lintDebug`, `:app:testDebugUnitTest` e `:app:assembleDebug` aprovados em GitHub Actions.
2. Testar teclado A + teclado B pressionando a mesma tecla: remover A não pode soltar B.
3. Testar mouse A + mouse B com o mesmo botão: remover A não pode soltar B.
4. Remover a última origem durante DOWN: a saída deve liberar sem ficar presa.
5. Repetir os cenários em pause/resume, editor, perda de foco, mudança de perfil e DeX externo.
6. Confirmar que uinput/InputManager continua roteando como na versão 0.14.36.

**Status:** alterações de fonte aplicadas e testes isolados aprovados. Build Android e homologação de hardware continuam pendentes; não declarar APK compilado até o pipeline completo passar.
