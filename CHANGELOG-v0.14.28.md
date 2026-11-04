# KeyMapper 0.14.28 — endurecimento da camada de display/UI

## Correções adicionais

- Removido cast forçado de `DisplayManager` no construtor do `DisplayHelper`; indisponibilidade do serviço passa a ser tratada como erro recuperável.
- Registro e remoção de `DisplayListener` toleram falhas transitórias do serviço.
- A tela de configurações isola as leituras de hardware/display e exibe campos indisponíveis quando a consulta falha, sem derrubar a composição.
- `ScreenInfo.ratio` evita proporção inválida quando uma dimensão está temporariamente ausente.
- Atualizado `versionCode` para 44 e `versionName` para 0.14.28.

## Limites mantidos

- ADB direto não é backend de entrada; a saída atual ainda usa InputManager via Shizuku.
- Root independente, evdev contínuo, uinput e alteração global de resolução/densidade não estão implementados.
- Não foi possível executar build Gradle neste ambiente: distribuição Gradle 8.8 não pôde ser baixada por falha DNS (`UnknownHostException: services.gradle.org`).
