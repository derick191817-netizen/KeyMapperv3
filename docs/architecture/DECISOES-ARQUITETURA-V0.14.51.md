# Decisões de arquitetura — KeyMapper 0.14.51

## 1. Limites por responsabilidade

O projeto permanece como um aplicativo Android com módulos Gradle mínimos. As responsabilidades são separadas por pacotes e contratos Kotlin; não foram criados subprojetos Gradle vazios.

- `ui`: telas Compose e interação.
- `presentation`: estado de tela/ViewModels.
- `data`: modelos persistidos e repositórios.
- `domain`: políticas e regras puras.
- `input` / `mapping`: processamento e regras de entrada/mapeamento.
- `injection`: integração com Shizuku, ADB, JNI e saídas.
- `overlay`: janelas e renderização HUD.
- `diagnostics`: sondagens, políticas e estado de capacidade.

## 2. mDNS e ADB sem fio

A tela `WirelessAdbDialog` não importa mais tipos de `com.flyfishxu.kadb.mdns`.

A integração usa três elementos internos:

1. `WirelessAdbDiscoveryContract.kt`: modelos próprios e interface `WirelessAdbDiscovery`.
2. `WirelessAdbDiscoveryPolicy.kt`: validação, remoção de duplicatas e limite de endpoints; sem dependência Android.
3. `KadbWirelessAdbDiscovery.kt`: adaptador Android que contém toda a dependência Kadb/mDNS.

A política de descoberta só expõe endpoints TLS de conexão e pareamento. A conexão ADB continua separada da descoberta.

Isso permite trocar Kadb por `NsdManager` nativo ou outro provedor sem alterar a tela Compose.

## 3. Decisão de linguagem

- Kotlin permanece na camada Android, UI, contratos e regras puras, por oferecer integração direta com APIs Android e Gradle.
- C++ permanece no código nativo de captura/saída que já usa NDK/JNI. Não se introduz Rust sem benchmark que demonstre vantagem concreta.
- Bash verifica contratos do repositório e integra os testes host no GitHub Actions.
- Python é adequado para ferramentas de auditoria/geração offline, mas não será empacotado no APK sem necessidade.
- Java/TypeScript/C#/Dart/SQL não são introduzidos artificialmente: não há componente atual que justifique essas linguagens.

## 4. Testabilidade

`WirelessAdbDiscoveryPolicy` não depende de `Context`, `NsdManager` ou Kadb. Os testes unitários verificam host vazio, espaços, portas inválidas, normalização, duplicatas, limites e estabilidade da ordem.

`check-wireless-adb-boundary.sh` impede que a tela volte a importar diretamente a API externa. `check-kadb-coordinates.sh` protege as coordenadas da dependência e o versionamento do workflow.

## 5. Limites conhecidos

- O teste unitário não comprova descoberta mDNS numa rede Wi-Fi real.
- A resolução da dependência Maven e a compilação Android devem ser confirmadas pelo GitHub Actions.
- O funcionamento de ADB TLS exige teste físico com Android Wireless Debugging habilitado.
