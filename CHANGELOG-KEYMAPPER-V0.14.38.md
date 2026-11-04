# Changelog — KeyMapper 0.14.38

**versionName:** `0.14.38`  
**versionCode:** `54`  
**Base:** `0.14.37-KEYMOUSE-MULTIDEVICE`

## Visão e cursor contínuos

- Removido o clamp físico dos deltas do cursor do HUD em `OverlayManager.onNativeMouseDelta()`. O cursor lógico é acumulado de forma independente das dimensões do display; o retículo visual permanece renderizável e reancora dentro da superfície, preservando o excedente do movimento.
- Unificado o cálculo de movimento do retículo para captura nativa evdev e fallback Pointer Capture/MotionEvent. Os dois caminhos atualizam a mesma posição lógica e o mesmo retículo visual.
- Criado `CursorReanchorPolicy`, com caminho `advanceInto()` sem alocação por evento e cálculo O(1) para deltas que cruzam várias dimensões de tela.
- Expandido o limite numérico de `VirtualCursorController` para `1e300`. Esse valor é somente uma proteção numérica e não representa uma borda de tela ou um limite de cursor.

## Motor de câmera

- `LookReanchorPlanner` agora devolve o movimento que não coube no lote atual (`remainingDeltaX/Y`) em vez de truncá-lo na borda ou no limite de reancoragens.
- O motor processa no máximo 64 reancoragens por lote e agenda continuações ordenadas de 1 ms. O excedente é sempre processado antes de movimentos mais recentes acumulados em `pendX/Y`.
- A posição lógica de câmera avança somente pelo delta efetivamente materializado no lote; o excedente só é contabilizado quando for entregue.
- Corrigido o caso de deadzone zero, no qual um buffer vazio poderia ser interpretado como movimento pronto e agendar flushes sem trabalho.
- Mantido `Rot.AUTO`/`Rot.CENTER` como caminho touchscreen com reancoragem. `Rot.OFF` continua disponível como modo deliberadamente limitado. `Rot.INFINITE` continua condicionado à compatibilidade de mouse relativo do destino.

## Interface e documentação

- Nomes dos modos atualizados para distinguir `Automático (continua além das bordas)` de `Limitado (para na borda)`. Descrição do retículo atualizada para explicar movimento e reancoragem, sem descrevê-lo como indicador fixo.
- README, relatório técnico e artefato GitHub Actions atualizados para `0.14.38` / `54`.
- Incluída em `docs/` a análise estática do SS KeyMouse Pro 1.0.76 fornecido pelo usuário; não foram incorporados binários nem código proprietário.

## Testes puros

- Testes de deslocamentos horizontais e diagonais maiores que a tela, processados por lotes sucessivos sem perda do delta.
- Testes de reancoragem do cursor visual para ambos os sentidos, várias dimensões de tela, movimento repetido, chegada exata à borda e atualização sem alocação no caminho quente.
- Testes de coordenada lógica muito além das dimensões físicas, movimentos extremos finitos e rejeição de NaN/Infinity.
- Resultado desta sessão: **26 testes puros passaram** usando `kotlinc` e um harness JUnit isolado.

## Limites de validação

O build Gradle completo, Android Lint, `assembleDebug`, instalação e teste físico ainda não foram executados nesta sessão. O ambiente não tem Android SDK configurado e não tem a distribuição Gradle 8.8 em cache; a execução completa requer ambiente com JDK 17, Gradle 8.8 e Android SDK 34. Nenhum APK é declarado como gerado ou homologado.
