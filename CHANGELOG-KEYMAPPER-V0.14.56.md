# KeyMapper 0.14.56 — correção de compatibilidade do Kadb/Jetifier

## Correção principal

- Substituído `com.flyfishxu:kadb:2.1.5` pelo AAR Android específico `com.flyfishxu:kadb-android:2.1.4`.
- Mantido `com.flyfishxu:kadb-mdns-android:2.1.3`.
- A variante Android 2.1.5 falhava na transformação Jetifier com bytecode Java 25 (class major 69).
- A versão 2.1.4 é a publicação AAR Android/JVM 21 e mantém a API usada pelo cliente Wireless ADB.

## Proteção de Bouncy Castle

- Atualizados `bcprov-jdk18on`, `bcpkix-jdk18on` e `bcutil-jdk18on` para 1.86, evitando a versão 1.84 transitiva, anterior às correções CVE publicadas em 1.85.
- Adicionada `android.jetifier.ignorelist=bc.*-jdk18on` para não transformar os JARs multi-release Bouncy Castle.
- Os JARs permanecem no classpath e no empacotamento; somente a transformação Jetifier é ignorada para esses artefatos.
- Jetifier continua ativo para as demais dependências.

## CI / prevenção

- Criado `verify-kadb-selected-version.sh`, que executa `dependencyInsight` no `debugCompileClasspath` e exige Kadb Android 2.1.4 e Bouncy Castle 1.86 para os três módulos.
- Adicionados testes para garantir que o verificador aceite Kadb 2.1.4 + Bouncy Castle 1.86 e rejeite Kadb 2.1.5, BC 1.84 ou dependências ausentes.
- Incluída verificação da ignorelist de Jetifier.
- O workflow executa essas verificações antes de `lintDebug`.
- Atualizados os contratos de coordenadas e metadados da release.

## Toolchain preservado

- AGP 8.5.2
- Kotlin / Compose Plugin 2.4.21
- Gradle Wrapper 8.8
- JDK / bytecode do app 17
- Android SDK 34
- NDK 26.3.11579264 / CMake 3.22.1

O aviso relativo ao requisito futuro de Gradle 8.14.4 em Kotlin 2.5.0 não é a causa da falha apresentada no Actions. Não foi feito upgrade desnecessário do toolchain.

## Versão

- `versionName`: `0.14.56`
- `versionCode`: `72`
- Artefato GitHub Actions: `keymapper-0.14.56-debug`

## Validação

Os verificadores locais, testes host e testes negativos foram executados. A resolução Maven real, Lint, JUnit Gradle e `assembleDebug` ainda dependem de nova execução do GitHub Actions, pois o ambiente desta sessão não consegue baixar o Gradle Wrapper por falha DNS. Nenhum APK é declarado como gerado nesta entrega.
