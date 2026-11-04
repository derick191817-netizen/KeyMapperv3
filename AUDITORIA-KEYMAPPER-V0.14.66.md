# Auditoria de correção — KeyMapper 0.14.66

## Base do diagnóstico

Foram analisados os três prints enviados em 4 de novembro de 2026, o `main` do repositório `derick191817-netizen/KeyMapperv3` e a fonte v0.14.65. O projeto remoto e o ZIP apresentavam a mesma configuração relevante.

## Falhas observadas

1. O Wrapper usava Gradle 8.8. O log alerta que a futura linha Kotlin 2.5.0 exigirá pelo menos Gradle 8.14.4. A revisão passa a usar Gradle 8.14.6, preservando AGP 8.5.2 e KGP/Compose 2.4.21, cuja faixa publicada inclui Gradle até 9.7.0.
2. `:app:checkDebugAarMetadata` não conseguia resolver `com.github.Flyfish233:spake2-java:1.1.1`, dependência transitiva do `com.flyfishxu:kadb-android:2.1.4`. A pesquisa de repositórios do projeto mostrava apenas Google Maven e Maven Central; SPAKE2 está publicado no JitPack. JitPack foi adicionado aos repositórios de dependências.
3. `:app:compileDebugAidl` não resolvia `IKeyMapperInputCallback` a partir de `IKeyMapperShellService.aidl`. O arquivo callback existia no diretório AIDL correto; o contrato agora declara o import totalmente qualificado explicitamente. Um teste estrutural impede a regressão.

## Arquivos alterados

- `gradle/wrapper/gradle-wrapper.properties`
- `settings.gradle.kts`
- `app/src/main/aidl/com/example/keymapper/injection/IKeyMapperShellService.aidl`
- `app/build.gradle.kts`
- `.github/workflows/build.yml`
- `tools/build-tests/check-toolchain-compatibility.py`
- `tools/build-tests/test-toolchain-compatibility.py`
- `tools/dependency-tests/check-kadb-coordinates.sh`
- `tools/architecture-tests/check-aidl-contract.sh` (novo)
- README, changelog e instruções de aplicação.

## Resultado local

Passaram: verificador positivo do toolchain, quatro testes negativos do verificador, coordenadas Kadb, regra Jetifier, testes de versão selecionada simulada, contrato AIDL, fronteira Wireless ADB, isolamento Shizuku, idempotência de sessão, política HUD, posicionamento do overlay, tipos de controles, macro, ownership de contatos, recuperação de perfis, testes nativos EVDEV/JNI, análise sintática C++ e contrato CMake/JNI.

## O que ainda não foi confirmado

- Resolução Maven real com Gradle no runner;
- `:app:lintDebug`;
- `:app:testDebugUnitTest`;
- `:app:assembleDebug` e publicação do APK;
- instalação e validação física em Android 13 / One UI / Samsung DeX.

Não há APK nesta entrega. A integração GitHub recusou a criação da branch de correção com HTTP 403, portanto nenhuma alteração remota foi publicada.
