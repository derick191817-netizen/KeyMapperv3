# Aplicar patch — KeyMapper 0.14.63

1. Faça backup do repositório. Este patch cumulativo parte da base remota 0.14.55 e incorpora as correções até 0.14.63.
2. Extraia `KeyMapper-v0.14.63-PATCH-OVERLAY.zip` diretamente na raiz do repositório, mantendo `app/`, `tools/`, `.github/` e `docs/`.
3. Confira `app/build.gradle.kts`: `versionName = "0.14.63"` e `versionCode = 79`.
4. Execute as verificações host do workflow. Em seguida, no runner com rede e Android SDK disponíveis, execute `./gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`.
5. Confira o artefato `keymapper-0.14.63-debug`. Não instale no dispositivo até confirmar que o build terminou e revisar os logs.

A 0.14.63 mantém a Macro sequencial da 0.14.62 e extrai o avanço de ciclos para `MacroSequenceCursor`, com testes dedicados. O formato legado de Combo não é reutilizado. O ZIP não contém APK.
