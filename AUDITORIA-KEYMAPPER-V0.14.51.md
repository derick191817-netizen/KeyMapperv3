# Auditoria KeyMapper 0.14.51

## Escopo

Auditoria e melhoria de integração entre UI, lógica da aplicação, biblioteca ADB/mDNS, QA e DevOps. Base: fonte 0.14.50.

## Diagnóstico

A tela `ui/WirelessAdbDialog.kt` importava diretamente tipos de `com.flyfishxu.kadb.mdns`. Isso acoplava a UI ao fornecedor e dificultava substituir ou simular a descoberta independentemente. A mesma tela também continha a filtragem de endpoint TLS e a manipulação de tipos da biblioteca externa.

O projeto já separa responsabilidades em pacotes `ui`, `presentation`, `data`, `domain`, `input`, `mapping`, `injection`, `overlay` e `diagnostics`. Criar vários subprojetos Gradle agora acrescentaria complexidade de configuração sem isolar artefatos ou ciclos de build independentes.

## Alterações

1. `DiscoveredAdbEndpoint.kt`: modelos de endpoint e snapshot próprios da aplicação, sem Android/Kadb.
2. `WirelessAdbDiscoveryContract.kt`: contrato `WirelessAdbDiscovery` com fluxo de snapshots, `start()` e `close()`.
3. `WirelessAdbDiscoveryPolicy.kt`: política pura de validação e normalização de endpoints.
4. `KadbWirelessAdbDiscovery.kt`: adaptador Android que contém toda a dependência externa e filtra endpoints de conexão TLS.
5. `WirelessAdbDialog.kt`: consome apenas o contrato interno, atualiza estado Compose com modelos da aplicação e mantém preenchimento manual como alternativa.
6. `WirelessAdbDiscoveryPolicyTest.kt`: testes para host em branco/espaços, porta inválida, trim, nome vazio, duplicatas, múltiplos hosts e limite de itens.
7. `check-wireless-adb-boundary.sh`: valida que a UI não importa a API Kadb diretamente e que contrato/adaptador/factory estão presentes.
8. Workflow: executa também a verificação de fronteira arquitetural.

## Tecnologias e justificativa

- Kotlin: escolha adequada para UI Compose, APIs Android, contratos e políticas.
- C++: permanece somente nas áreas nativas atuais de input/NDK; esta mudança não exige trocar esse componente.
- Bash: verificações de arquitetura e dependências no CI.
- Não foi introduzido Rust/C#/Dart/TypeScript/Python no APK: não há ganho mensurável nesta mudança.
- Não foram criados módulos Gradle vazios; a separação é por contratos e pacotes. Um módulo Gradle separado só deve existir quando houver dependência/build boundary independente que justifique.

## Resultados executados

- Harness independente Kotlin compilou os modelos e a política com `kotlinc`: 7 cenários aprovados.
- `tools/native-tests/run.sh`: aprovado.
- `tools/native-tests/check-syntax.sh`: aprovado.
- `tools/native-tests/check-library-contract.sh`: aprovado.
- `tools/dependency-tests/check-kadb-coordinates.sh`: aprovado.
- `tools/architecture-tests/check-wireless-adb-boundary.sh`: aprovado.
- Validação YAML e alinhamento versionName/versionCode/artifact: aprovado.
- ZIP verificado com `unzip -t`; SHA-256 verificado.

## Limitações / pendências

- Tentativa de `bash ./gradlew --no-daemon :app:testDebugUnitTest --stacktrace --console=plain` parou antes de iniciar Gradle: `UnknownHostException: services.gradle.org`.
- Android Lint, testes unitários via Gradle e `assembleDebug` não foram executados.
- A API de mDNS da tag 2.1.3 foi conferida na fonte oficial, e a coordenada Android é publicada como AAR; a resolução efetiva do artefato deve ser confirmada no GitHub Actions com acesso ao Maven Central.
- Descoberta mDNS em Wi-Fi real, pareamento TLS, reconexão e Samsung DeX ainda dependem de teste físico.
- Nenhum APK foi gerado nesta sessão.

## Versão

`versionName=0.14.51`, `versionCode=67`, artefato `keymapper-0.14.51-debug`.
