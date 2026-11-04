# Changelog — KeyMapper 0.14.45

## Diagnóstico do backend nativo

- Centralizado o nome do shared library em `NativeBackendLibrary`: `keymapper_evdev` / `libkeymapper_evdev.so`.
- `KeyMapperShellService` usa o identificador central ao carregar o JNI.
- `InputBackendProbe` procura o mesmo nome gerado pelo target CMake.
- Corrigido o falso diagnóstico anterior que procurava `libkeymapper_input.so`.
- Captura evdev e saída touchscreen uinput são identificadas como componentes do mesmo shared library; o diagnóstico não afirma que ambos foram inicializados apenas pela existência do arquivo.
- `BackendCapabilityPolicy` agora diferencia `MODULE_PRESENT_NOT_PROBED` de `MODULE_PRESENT_NOT_INITIALIZED`. Ausência de consulta deixou de ser interpretada como falha confirmada.
- Adicionado teste unitário do contrato de nome e script host que compara target CMake, `System.loadLibrary`, arquivo esperado e sondagem.
- GitHub Actions executa o novo script junto aos testes nativos de host.

## Versão

- `versionName`: `0.14.45`
- `versionCode`: `61`
- Artefato CI: `keymapper-0.14.45-debug`

## Escopo e limites

Esta versão corrige diagnóstico e consistência de carregamento. Não altera algoritmos de câmera, recentralização, injeção ou controles HUD. Presença do `.so` não comprova que o serviço conseguiu abrir os nós do kernel ou que o aplicativo de destino recebeu eventos. Build Android completo e teste físico continuam necessários.
