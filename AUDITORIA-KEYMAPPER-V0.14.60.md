# Auditoria técnica — KeyMapper 0.14.60

## Escopo

Auditoria do caminho de reconhecimento evdev e tradução de teclas, partindo do código 0.14.59. O objetivo foi corrigir periféricos que eram reconhecidos incorretamente ou detectados, mas não tinham códigos traduzidos para a camada Android.

## Defeito confirmado

A regra anterior considerava todos os códigos a partir de `BTN_MISC` como botões. Essa interpretação é incorreta: o espaço `EV_KEY` alterna intervalos `BTN_*` com códigos `KEY_*` de mídia/controle. Exemplos reais incluem `KEY_OK`, `KEY_SELECT`, `KEY_INFO`, `KEY_CHANNELUP`, `KEY_CHANNELDOWN`, `KEY_NEXT` e `KEY_PREVIOUS`. A regra antiga rejeitava todos esses códigos.

Também existem códigos de botão adicionais junto à faixa do D-pad (`BTN_DPAD_*` e `BTN_GRIP*`). Uma exclusão curta até `BTN_DPAD_RIGHT` ainda deixaria alguns botões passarem como teclas.

## Alterações aplicadas

### 1. Classificação nativa

Arquivo: `app/src/main/cpp/evdev_device_classification.h`

- `isButtonCode()` agora exclui intervalos BTN distintos, em vez de usar `code >= BTN_MISC` como regra universal.
- `isKeyboardKeyCode()` aceita códigos KEY válidos até `KEY_MAX` que não pertençam às faixas BTN.
- `hasKeyboardCapabilities()` percorre os códigos válidos e consulta o bitmap EV_KEY. A detecção continua aceitando macro pads de 1–3 teclas.
- A faixa de botões inclui BTN_MISC–BTN_9, BTN_MOUSE–BTN_TASK, BTN_JOYSTICK–BTN_DEAD, BTN_GAMEPAD–antes de BTN_DIGI, BTN_DIGI–BTN_TOOL_QUADTAP, BTN_WHEEL–BTN_GEAR_UP, BTN_DPAD/BTN_GRIP (0x220–0x227) e BTN_TRIGGER_HAPPY–KEY_MAX.

### 2. Tradução Android

Arquivo: `app/src/main/java/com/example/keymapper/input/LinuxInputKeyMapper.kt`

Foram adicionadas traduções para volume mute, pausa, play/stop/record, next/previous, rewind/fast-forward, media close/eject, DPAD_CENTER, INFO, GUIDE e channel up/down. Teclas desconhecidas continuam retornando `null`; não são convertidas para uma tecla arbitrária.

### 3. Testes

- `tools/native-tests/evdev_device_classification_test.cpp`: macro pads, teclas KEY acima de BTN_MISC, botões de mouse/gamepad/D-pad/grip/trigger-happy, HID misto e bitmap vazio.
- `app/src/test/java/com/example/keymapper/input/LinuxInputKeyMapperTest.kt`: mapeamentos de mídia/consumer e rejeição de códigos desconhecidos/botões.
- `tools/native-tests/run.sh` compila e executa a suíte C++ de host antes das etapas Android no CI.

## Fluxo verificado

1. O serviço privilegiado abre `/dev/input/event*`.
2. `inspectDevice()` consulta `EVIOCGBIT` para eventos, eixo relativo e teclas.
3. O helper classifica capacidades KEY sem confundir intervalos BTN.
4. `processDeviceEvents()` envia EV_KEY apenas quando `isKeyboardKey()` aceita o código.
5. O lote AIDL/JNI transporta `[deviceClass, type, code, value, deviceId, timeNs]`.
6. `MappingService` resolve `LinuxInputKeyMapper.toAndroidKeyCode()` e entrega o código ao motor somente quando existe tradução.

## Resultados

Aprovados: C++17, JNI syntax check, testes de classificação, recuperação de lote JNI, contratos CMake/JNI, dependências Kadb/Jetifier, toolchain, políticas Shizuku, HUD, overlay, controles e YAML do workflow. O Kotlin mapper passou em compilação/harness host contra stub de constantes Android API.

Pendentes: `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:assembleDebug` e ensaio em aparelho Android 13/One UI/DeX. A distribuição Gradle 8.8 não estava disponível localmente e o Wrapper não conseguiu baixá-la por falha DNS. Nenhum APK foi gerado.

## Limitações

A descoberta de uma capacidade KEY não garante por si só que o aplicativo destino aceite aquela tecla; a tradução para Android depende de existir um mapeamento. Códigos desconhecidos continuam sendo ignorados explicitamente. A permissão para abrir e capturar cada nó evdev depende do UID/SELinux/ROM e precisa ser verificada em aparelho real.
