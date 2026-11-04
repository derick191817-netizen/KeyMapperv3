# KeyMapper 0.14.47 — correção da compilação Kotlin / GitHub Actions

## Causa raiz

O workflow falhou durante a avaliação do script `app/build.gradle.kts`, antes de executar o Android Lint. A linha `android.kotlinOptions { jvmTarget = "17" }` usa a API de configuração antiga. Com Kotlin Gradle Plugin 2.4.21, a propriedade `jvmTarget` baseada em `String` é erro e deve ser migrada para `compilerOptions` com um valor tipado.

## Correção

- Removido `android.kotlinOptions { jvmTarget = "17" }`.
- Adicionado `kotlin { compilerOptions { jvmTarget = JvmTarget.fromTarget("17") } }`.
- Mantidas as opções Java `sourceCompatibility` e `targetCompatibility` em `VERSION_17`.
- Atualizados `versionName` para `0.14.47`, `versionCode` para `63` e o nome do artefato CI para `keymapper-0.14.47-debug`.

## O que não foi alterado

AGP 8.5.2, Kotlin Gradle Plugin 2.4.21, Gradle Wrapper 8.8, JDK de CI 17, Android SDK 34, NDK e dependências permanecem iguais. O aviso sobre Gradle 8.8 e Kotlin 2.5.0 é uma advertência de compatibilidade futura; não é o erro fatal mostrado nas imagens.

## Verificações executadas

- `bash tools/native-tests/run.sh`: aprovado.
- `bash tools/native-tests/check-syntax.sh`: aprovado.
- `bash tools/native-tests/check-library-contract.sh`: aprovado.
- Varredura por `kotlinOptions` e `jvmTarget = "17"`: nenhum bloco/configuração legado restante.
- Verificação de versionCode/versionName, JDK target 17 e nome de artefato: aprovado.
- YAML: validado separadamente no empacotamento.

## Limitação

A tentativa local de iniciar o Gradle Wrapper falhou antes da avaliação do projeto porque o ambiente não consegue resolver `services.gradle.org` (`UnknownHostException`). Não há Gradle 8.8 em cache nem Android SDK configurado. Assim, a correção remove a causa exata do erro apresentado, mas o `lintDebug`, `testDebugUnitTest` e `assembleDebug` precisam ser confirmados pelo GitHub Actions após o envio desta versão. Nenhum APK é declarado como gerado.

## Referências oficiais

- Kotlin — migração de `kotlinOptions` para `compilerOptions`: https://kotlinlang.org/docs/gradle-compiler-options.html
- Android — requisito mínimo do AGP 8.5: https://developer.android.com/build/releases/about-agp
