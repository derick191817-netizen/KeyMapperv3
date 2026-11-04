# Aplicar patch — KeyMapper 0.14.66

## Base

Base: KeyMapper 0.14.65. Faça backup do projeto antes de aplicar.

## Aplicação

1. Extraia `KeyMapper-v0.14.66-PATCH-OVERLAY.zip` na raiz do projeto, preservando os caminhos.
2. Confirme `versionName = "0.14.66"` e `versionCode = 82` em `app/build.gradle.kts`.
3. Confirme Gradle 8.14.6 e o checksum da distribuição em `gradle/wrapper/gradle-wrapper.properties`.
4. Confirme `https://jitpack.io` em `settings.gradle.kts`.
5. Confirme o import explícito do callback no AIDL.
6. Execute: `python3 tools/build-tests/check-toolchain-compatibility.py`.
7. Execute: `python3 tools/build-tests/test-toolchain-compatibility.py`.
8. Execute: `bash tools/architecture-tests/check-aidl-contract.sh`.
9. Execute: `bash tools/dependency-tests/check-kadb-coordinates.sh`.
10. Execute no ambiente com Android SDK 34, NDK 26.3.11579264, CMake 3.22.1 e JDK 17: `./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`.

## Observação

Os testes estruturais locais passaram, mas não substituem a compilação Android. O APK só deve ser considerado válido quando `assembleDebug` concluir com sucesso e o artefato for publicado pelo Actions.
