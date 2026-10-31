# Auditoria técnica — KeyMapper 0.14.14

Data: 31/10/2026

## 1. Defeito identificado

O `CaptureView` já tinha dois caminhos de leitura de movimento:

1. Listener de Pointer Capture: `getX()/getY()` são tratados como deltas relativos.
2. `onGenericMotionEvent` sem Pointer Capture: `MouseDeltaPolicy` só aceita `AXIS_RELATIVE_X/Y`, rejeitando X/Y absolutos do cursor.

O segundo caminho era aceito pelo parser e encaminhado a `onDelta`, mas `dispatchDelta()` só movia a máquina para `RELATIVE_ACTIVE` quando `captured == true` e `hasPointerCapture() == true`. O estado ficava em `GRAB_REQUESTED` ou `ERROR` mesmo depois de uma amostra relativa válida. Além disso, o timeout inicial podia marcar `ERROR` apesar da rota alternativa estar entregando eventos.

## 2. Correção implementada

- Novo estado `RELATIVE_FALLBACK_ACTIVE`, distinto de `RELATIVE_ACTIVE`.
- `RELATIVE_ACTIVE` continua significando amostra relativa com Pointer Capture confirmado.
- `RELATIVE_FALLBACK_ACTIVE` significa que o KeyMapper recebeu uma amostra relativa pelo caminho genérico sem Pointer Capture; não significa captura global.
- A transição fallback é permitida enquanto a captura está pendente ou depois de timeout.
- O timeout consulta o instante monotônico da última amostra fallback. Uma amostra recente suprime o erro falso; uma entrada absoluta não passa por `MouseDeltaPolicy` e não ativa esse estado.
- `InputDiagnostics` conta separadamente as amostras recebidas sem Pointer Capture e mantém `Pointer Capture confirmado` como flag real de View/janela.
- `InputStatePolicy.isCaptureConfirmed()` não considera o estado fallback como captura confirmada.

## 3. Engenharia reversa Panda Mouse Pro

O ZIP reenviado contém um APK cujo SHA-256 é `f09f329a3661c8ec0d9d4845b5ac7096e59ada1527dd2e59a70ab43ea27910fb`, idêntico ao APK já presente na pasta de referência. Portanto não houve atualização de binário.

A inspeção estática encontrou:

- `assets/com.panda.mouseinject.dex` (477.044 bytes).
- `assets/com.panda.mouselibinject.so`: ELF32 ARM, 40.892 bytes.
- `assets/x64com.panda.mouselibinject.so`: ELF64 AArch64, 61.544 bytes.
- `assets/x86_64com.panda.mouselibinject.so`: ELF64 x86-64, 60.728 bytes.
- As três variantes exportam `JNI_OnLoad`; imports incluem `ioctl`, `poll`, `read`, `write`, `inotify`, `opendir/readdir`, além de `popen/pclose`.
- Strings incluem `/dev/input`, códigos `ABS_*`, `REL_*`, `BTN_*` e mensagens/opções de inspeção de dispositivos/eventos.
- DEX principal e DEX auxiliar incluem nomes `GetEventService`, `InjectService`, `NativeDeviceInfo`, `CursorInputMapper`, `TouchInputMapper`, `KeyboardInputMapper` e `JoystickInputMapper`; há fallback de `MODE_POINTER_RELATIVE` para `MODE_POINTER`.
- `assets/activate.sh` e `assets/inject2.sh` indicam preparação de auxiliares num diretório temporário e inicialização de `InjectService` por `app_process`, com DEX por componente. Os scripts também tentam alterar uma configuração global de toques não confiáveis; esse comportamento não foi reproduzido.

**Limite probatório:** imports, strings, JNI_OnLoad, nomes de classe e scripts empacotados mostram a arquitetura prevista no pacote, mas não provam quais caminhos foram acionados em uma sessão, se os nós de dispositivo foram abertos, se houve grab, nem se eventos foram entregues ao jogo. Não foi realizada execução dinâmica, attach, instrumentação ou teste de tráfego. Nenhum binário ou script proprietário foi incorporado ao KeyMapper.

## 4. Validação local

- 117 métodos `@Test` passaram no runner offline do projeto.
- Smoke de fila: 149.930 assertions, incluindo stream de 50.000 eventos.
- Smoke do pipeline de sensibilidade: 8 assertions.
- Smoke de diagnóstico: 15 checks, incluindo `RELATIVE_FALLBACK_ACTIVE`, contador de amostras sem Pointer Capture e `Pointer Capture confirmado=false`.

A bateria offline usa runner JUnit Lite local e stubs mínimos para partes sem runtime Android. Não substitui Gradle/JUnit oficial, compilação Android, testes instrumentados ou teste de hardware.

## 5. Limitações remanescentes

- Não foi gerado APK: Android SDK e distribuição Gradle necessários não estavam disponíveis/configurados.
- Não houve teste físico em Samsung DeX, telefone ou jogo.
- A disponibilidade de `SOURCE_MOUSE_RELATIVE` e `AXIS_RELATIVE_X/Y` como eventos genéricos varia conforme firmware/periférico; este patch classifica corretamente o fluxo caso os eventos sejam entregues, mas não consegue forçar o Android a entregá-los.
- O gargalo arquitetural permanece: Pointer Capture depende do foco da janela; não existe backend próprio de captura global evdev/`EVIOCGRAB` nem writer `/dev/uinput`.
- Resultado positivo de `InputManager.injectInputEvent` não comprova consumo pelo app de destino.
