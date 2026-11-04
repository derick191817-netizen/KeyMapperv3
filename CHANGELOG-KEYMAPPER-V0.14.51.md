# KeyMapper 0.14.51

## Fronteira de arquitetura para descoberta ADB sem fio

- Removidos os imports diretos da API `com.flyfishxu.kadb.mdns` da tela Compose `WirelessAdbDialog`.
- Criado contrato interno `WirelessAdbDiscovery`, com `Flow<WirelessAdbDiscoverySnapshot>` e modelos próprios `DiscoveredAdbEndpoint`.
- Criado adaptador `KadbWirelessAdbDiscovery`, que concentra os imports e a integração com `KadbMdnsAndroid` / mDNS 2.1.3.
- Criada `WirelessAdbDiscoveryPolicy`, sem dependência Android, para validar host/porta, normalizar campos, eliminar duplicatas e limitar os endpoints exibidos.
- A política não expõe ADB legado não TLS como endpoint de conexão selecionável; a filtragem TLS continua no adaptador.
- Criado teste JUnit `WirelessAdbDiscoveryPolicyTest` cobrindo normalização, entradas inválidas, duplicatas, portas, limite e preservação da ordem.
- Criado `tools/architecture-tests/check-wireless-adb-boundary.sh` e integrado ao GitHub Actions para impedir que a UI volte a depender diretamente da API externa.
- Atualizado o teste de coordenadas Kadb para verificar a nova separação entre UI e adaptador.
- Documentada a divisão de responsabilidades e as decisões de linguagem em `docs/architecture/DECISOES-ARQUITETURA-V0.14.51.md`.

## Decisão tecnológica

Kotlin permanece nas camadas Android/UI/contratos/políticas. C++ permanece na captura/saída nativa já existente. Bash verifica contratos no CI. Rust, Python no APK, TypeScript, C#, Dart e SQL não foram introduzidos artificialmente, pois não há benefício concreto medido nesta etapa.

## Versões

- `versionName`: `0.14.51`
- `versionCode`: `67`
- Artefato CI: `keymapper-0.14.51-debug`

## Limites de validação

- 7 cenários do harness Kotlin puro para o modelo/política: aprovados.
- Testes C++ host, sintaxe dos fontes nativos, contrato de biblioteca, contrato arquitetural, coordenadas Kadb e YAML do workflow: aprovados.
- Gradle `:app:testDebugUnitTest` não iniciou: `UnknownHostException: services.gradle.org` ao baixar Gradle 8.8. Android Lint, testes Gradle e `assembleDebug` não foram executados.
- Nenhum APK foi gerado nesta sessão.
- Teste de descoberta mDNS em rede real e pareamento/conexão TLS em Android físico permanecem pendentes.
