# Changelog — KeyMapper 0.14.40

**versionName:** `0.14.40`  
**versionCode:** `56`  
**Base:** KeyMapper 0.14.39

## Captura nativa e normalização do movimento

- Preservado `EV_SYN/SYN_REPORT` do backend evdev no transporte JNI para manter a fronteira de cada relatório físico do mouse.
- Criado `NativeMouseReportAggregator`, que combina `REL_X` e `REL_Y` por `deviceId` em um único vetor antes de chamar `MappingEngine.onDelta()`.
- O timestamp do `SYN_REPORT` é utilizado para calcular a escala temporal uma vez por vetor X/Y. Quando o vetor é encerrado antes por uma transição de tecla/botão ou scroll, o agregador utiliza o timestamp do último eixo.
- Relatórios que cruzam callbacks/lotes JNI permanecem acumulados até o marcador correspondente; a ordem é preservada fechando o movimento antes de eventos de controle intercalados.
- `SYN_DROPPED`, remoção de dispositivo e reset global limpam somente a acumulação apropriada, impedindo que deltas parciais reapareçam depois de uma perda de sincronização.
- A atualização do retículo nativo passa a refletir os mesmos vetores X/Y agregados processados pelo motor.

## Arquivos alterados nesta versão

- `app/src/main/cpp/evdev_capture.cpp`
- `app/src/main/java/com/example/keymapper/input/LinuxEvdevEvent.kt`
- `app/src/main/java/com/example/keymapper/input/NativeMouseReportAggregator.kt` (novo)
- `app/src/main/java/com/example/keymapper/service/MappingService.kt`
- `app/src/test/java/com/example/keymapper/input/NativeMouseReportAggregatorTest.kt` (novo)
- `app/build.gradle.kts`
- `.github/workflows/build.yml`
- `README.md`
- `CHANGELOG-KEYMAPPER-V0.14.40.md`
- `AUDITORIA-KEYMAPPER-V0.14.40.md`
- `TEST-RESULTS-KEYMAPPER-V0.14.40.txt`

## Testes executados

- 56 testes puros Kotlin/JVM executados em harness isolado: 48 de câmera/cursor/sensibilidade/políticas + 8 do agregador evdev; todos passaram.
- `evdev_capture.cpp`: `g++ -std=c++17 -fsyntax-only` passou usando os headers JNI do JDK e Linux disponíveis no host.
- Verificações estáticas de versão, nome do artefato, YAML, arquivos e integridade ZIP executadas.

O harness isolado não executa Gradle, Android Lint, NDK, nem os caminhos de injeção Android. Build APK, workflow remoto e validação física permanecem explicitamente pendentes.
