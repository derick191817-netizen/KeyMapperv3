# Auditoria técnica — KeyMapper 0.14.59

## Escopo

Revisão do backend nativo evdev e dos contratos de identificação de dispositivos. O pacote fonte da 0.14.58 foi a base local; o repositório GitHub remoto consultado permanecia na 0.14.55.

## Problema identificado

`inspectDevice()` exigia ao menos quatro capacidades de tecla (`keyboardCapabilities >= 4`). Esse limiar não é exigido pelo protocolo evdev e rejeitava teclados HID válidos que anunciam somente uma a três teclas, como macro pads e dispositivos compactos.

Havia ainda uma classificação permissiva em `isKeyboardKey()`: `code <= KEY_MAX && !isMouseButton(code)` admitia códigos da família `BTN_*`, que começa em `BTN_MISC`. Em um HID composto, um botão de gamepad poderia passar pelo ramo de teclado, embora normalmente não tivesse mapeamento Android correspondente.

## Correções

- Adicionado `evdev_device_classification.h` com política pura e testável.
- `hasKeyboardCapabilities()` reconhece qualquer código acima de `KEY_RESERVED` e abaixo de `BTN_MISC`, limitado a `KEY_MAX`.
- `isKeyboardKeyCode()` aplica o mesmo intervalo ao filtrar eventos `EV_KEY`.
- `inspectDevice()` passou a usar a política pura; a coleta de capacidades não depende de heurísticas de quatro teclas.
- `isKeyboardKey()` agora exclui toda a faixa `BTN_*`, não apenas alguns botões de mouse.
- `tools/native-tests/run.sh` compila e executa teste próprio para a política HID. O workflow já chama esse script.

## Validação executada

- C++17 host: bitmap vazio, macro pad de uma tecla, teclado de três teclas, mouse/gamepad isolados, HID misto, ponteiro nulo e bitmap vazio.
- Sintaxe de `evdev_capture.cpp` e `uinput_touch.cpp` com headers JNI.
- Contrato entre CMake, nome carregado em Kotlin e biblioteca nativa.
- Verificadores de dependência e arquitetura (ver relatório de testes).

## Limites

Os testes host validam a política de classificação, não a enumeração real de dispositivos na ROM do aparelho. É necessário confirmar em hardware se o kernel expõe os nós `/dev/input/event*`, se Shizuku recebe acesso e se `EVIOCGRAB` é permitido. A compilação Gradle, Lint, testes JUnit Android e `assembleDebug` ainda precisam rodar no GitHub Actions.
