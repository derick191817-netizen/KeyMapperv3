# KeyMapper 0.14.60

## Reconhecimento e tradução de teclas

- Corrigida a classificação evdev: `BTN_MISC` não é o limite superior de todos os códigos `KEY_*`.
- O filtro exclui apenas faixas BTN distintas e preserva teclas consumer/media/navigation, incluindo `KEY_OK`, `KEY_SELECT`, `KEY_INFO`, `KEY_CHANNELUP`, `KEY_CHANNELDOWN`, `KEY_NEXT` e `KEY_PREVIOUS`.
- Incluída a faixa de botões BTN_DPAD/BTN_GRIP (0x220–0x227) na exclusão de botões, evitando que controles de gamepad sejam tratados como teclado.
- `LinuxInputKeyMapper` converte teclas multimídia e de navegação comuns para os códigos Android correspondentes.
- Adicionados testes C++ para faixas BTN não contíguas, macro pads, teclas KEY acima de BTN_MISC e HID misto.
- Adicionados testes unitários Kotlin para os novos mapeamentos.
- Atualizados `versionName=0.14.60`, `versionCode=76` e o artefato GitHub Actions.

## Validação

Verificações host C++17/JNI, arquitetura, dependências, toolchain e YAML passaram. O mapper Kotlin foi compilado e exercitado em harness host com constantes compatíveis com Android API. Lint, testes JUnit via Gradle, geração do APK e teste físico permanecem pendentes pela indisponibilidade do Gradle 8.8 neste ambiente.
