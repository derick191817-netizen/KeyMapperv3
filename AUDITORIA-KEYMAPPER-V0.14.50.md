# Auditoria técnica — KeyMapper 0.14.50

## Objetivo

Eliminar a ambiguidade da coordenada do módulo mDNS que interrompeu a tarefa `:app:compileDebugAidl` com `Could not find com.flyfishxu:kadb-mdns:2.1.5`.

## Diagnóstico

O código Android importa `KadbMdnsAndroid`, `MdnsConfig`, `MdnsEndpoint` e `MdnsServiceType` do pacote `com.flyfishxu.kadb.mdns`. O projeto dependia do artefato multiplataforma raiz `kadb-mdns`. Para um consumidor Android, o Maven Central também publica o AAR direcionado à plataforma Android: `com.flyfishxu:kadb-mdns-android:2.1.3`. A página oficial do Maven Central identifica o artefato como `aar`; o código-fonte oficial da tag 2.1.3 foi conferido e mantém as classes e o pacote esperados.

## Alterações

- `app/build.gradle.kts`: `kadb-mdns` substituído por `kadb-mdns-android:2.1.3`.
- `kadb:2.1.5` mantido.
- `tools/dependency-tests/check-kadb-coordinates.sh`: valida a coordenada Android explícita, rejeita o artefato raiz ambíguo, confirma os imports utilizados e verifica Maven Central, versionCode/versionName e nome do artefato CI.
- `.github/workflows/build.yml`: nome do artefato atualizado para `keymapper-0.14.50-debug`; sequência das etapas Gradle preservada.
- `README.md`, changelog, relatório de testes e auditoria atualizados.

## Compatibilidade de API

A API-fonte da tag `2.1.3` do repositório oficial `flyfishxu/Kadb` foi consultada para `KadbMdnsAndroid.kt`, `MdnsConfig.kt`, `MdnsEndpoint.kt` e `MdnsServiceType.kt`. Os imports e assinaturas usados por `WirelessAdbDialog.kt` são compatíveis com a fonte consultada. O artefato `kadb-mdns-android:2.1.3` é publicado como AAR Android no Maven Central.

## Testes executados nesta execução

- `tools/dependency-tests/check-kadb-coordinates.sh`: PASS.
- `tools/native-tests/run.sh`: PASS.
- `tools/native-tests/check-syntax.sh`: PASS.
- `tools/native-tests/check-library-contract.sh`: PASS.
- YAML do workflow: validado com parser.
- Consistência `versionCode=66`, `versionName=0.14.50` e `keymapper-0.14.50-debug`: PASS.
- Estrutura e integridade do ZIP: PASS.

## Não executado

- `./gradlew :app:dependencies --configuration debugCompileClasspath`.
- `:app:lintDebug`.
- `:app:testDebugUnitTest` via Gradle.
- `:app:assembleDebug`.
- Instalação e testes físicos no Android 13 / Samsung DeX.

O ambiente desta execução não tem distribuição Gradle 8.8 em cache e não consegue resolver `services.gradle.org` ou `repo.maven.apache.org`. Portanto, esta auditoria não declara resolução Maven ou compilação Android bem-sucedida. A confirmação final deve ocorrer no runner do GitHub Actions.

## Estado do GitHub remoto

O pacote contém o projeto completo atualizado. A tentativa de gravação por integração no repositório remoto foi recusada anteriormente com HTTP 403; não declaro `main` atualizado por esta entrega.
