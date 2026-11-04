# Changelog — KeyMapper 0.14.41

**versionName:** `0.14.41`  
**versionCode:** `57`  
**Base:** KeyMapper 0.14.40

## Recuperação após perda de lote JNI

- Criado `NativeInputBatchLossGuard`, usando `AtomicBoolean` para sinalizar que um lote evdev não foi aceito pelo `Handler` principal.
- O próximo lote aceito consome a sinalização e, antes de processar novos eventos, libera entradas físicas retidas no `CaptureView`, chama `MappingEngine.releaseAll()` e limpa o estado de teclas/botões evdev e os relatórios REL_X/Y incompletos.
- Perdas consecutivas são coalescidas em um único reset até a próxima recuperação; perdas posteriores agendam novo reset.
- O estado só é efetivamente limpo no looper principal. A thread do callback JNI apenas marca a necessidade de recuperação, sem acessar estruturas não thread-safe.

## Arquivos alterados nesta versão

- `app/src/main/java/com/example/keymapper/input/NativeInputBatchLossGuard.kt` (novo)
- `app/src/main/java/com/example/keymapper/service/MappingService.kt`
- `app/src/test/java/com/example/keymapper/input/NativeInputBatchLossGuardTest.kt` (novo)
- `app/build.gradle.kts`
- `.github/workflows/build.yml`
- `README.md`
- `CHANGELOG-KEYMAPPER-V0.14.41.md`
- `AUDITORIA-KEYMAPPER-V0.14.41.md`
- `TEST-RESULTS-KEYMAPPER-V0.14.41.txt`

## Testes executados

- 60 testes puros Kotlin/JVM em harness isolado: 48 de câmera/cursor/sensibilidade/políticas + 8 do agregador evdev + 4 da recuperação de lote perdido; todos passaram.
- `evdev_capture.cpp`: verificação sintática host com `g++ -std=c++17 -fsyntax-only` passou.
- Verificações estáticas de versão, workflow, estrutura e integridade ZIP preparadas para a entrega.

O harness não é o runner Gradle/JUnit do projeto. `:app:testDebugUnitTest`, `:app:lintDebug` e `:app:assembleDebug` foram tentados, mas o Wrapper falhou ao baixar Gradle 8.8 com `UnknownHostException: services.gradle.org`. O ambiente não possui Android SDK configurado. Não foi gerado APK e não houve execução física/CI.
