# Aplicar patch — KeyMapper 0.14.65

## Base e aplicação

1. A base deste patch é o código-fonte completo **KeyMapper 0.14.64**.
2. Faça uma cópia do projeto atual antes de aplicar.
3. Extraia `KeyMapper-v0.14.65-PATCH-OVERLAY.zip` diretamente na raiz do projeto, preservando os caminhos `app/`, `tools/`, `.github/` e os arquivos Markdown.
4. Confirme em `app/build.gradle.kts`: `versionName = "0.14.65"` e `versionCode = 81`.
5. Execute `bash tools/architecture-tests/check-settings-profile-recovery.sh` e os demais scripts listados em `TEST-RESULTS-KEYMAPPER-V0.14.65.txt`.
6. Em ambiente com JDK 17, Android SDK 34, NDK 26.3.11579264, CMake 3.22.1 e Gradle 8.8 disponível, execute `./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`.

## Mudança funcional

O repositório agora faz backup do JSON original antes de sanitizar `buttons` ou `games` presentes com `null` ou tipo diferente de array. Campos ausentes continuam aceitos por compatibilidade com configurações antigas. O backup é salvo em `settings_json_recovery_backup` e limitado a 2.000.000 caracteres, como os demais backups da recuperação.

## Limitações não resolvidas nesta versão

- Wireless ADB segue limitado a pareamento/conexão e diagnóstico somente leitura; não inicia o motor de entrada.
- Não existe provedor Root independente.
- O serviço de mapeamento segue dependendo do Shizuku.
- Não há APK validado nesta entrega: o Wrapper não consegue baixar Gradle 8.8 neste ambiente por falha de DNS.
