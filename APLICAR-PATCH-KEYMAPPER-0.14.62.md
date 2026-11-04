# Aplicar patch — KeyMapper 0.14.62

1. Faça backup do repositório e confirme que sua base contém a versão 0.14.61 ou a base 0.14.55 usada pelo patch cumulativo.
2. Extraia `KeyMapper-v0.14.62-PATCH-OVERLAY.zip` diretamente na raiz do repositório, mantendo os caminhos `app/`, `tools/`, `.github/` e `docs/`.
3. Não extraia dentro de uma pasta intermediária e não misture com alterações de outra versão.
4. Confira `app/build.gradle.kts`: `versionName = "0.14.62"` e `versionCode = 78`.
5. Execute primeiro os scripts host do workflow. Em seguida execute `./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`.
6. Se o CI falhar, compartilhe o log da primeira tarefa que falhou; não substitua dependências ou Gradle sem verificar a causa.

A versão 0.14.62 adiciona Macro sequencial sem reutilizar o campo `macro` de Combo. Não remova migrações nem arquivos de persistência. O APK não está incluído neste ZIP; precisa ser gerado por `assembleDebug`.
