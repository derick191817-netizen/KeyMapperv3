# Changelog — KeyMapper 0.14.36

**versionCode:** 52  
**Base:** KeyMapper 0.14.35 — backend uinput touchscreen opcional.

## Correções críticas no transporte uinput

- A elegibilidade do uinput agora exige geometria válida do display principal (`width > 1`, `height > 1`). Se a consulta falhar, lançar exceção ou retornar dimensões inválidas, o roteamento escolhe InputManager em vez de inventar uma tela `1×1`.
- A normalização de coordenadas foi extraída para `TouchOutputGeometryPolicy`, com rejeição de dimensões inválidas e de coordenadas NaN/Infinity. Pontos finitos fora da tela são limitados aos limites físicos antes da normalização 0..65535.
- `TouchInjector` deixa de usar `1×1` como geometria padrão; a ausência de geometria é inválida. Se a geometria se tornar inválida depois do início de uma sessão uinput, o quadro é recusado e o gesto é abortado/liberado, sem troca de transporte no meio do gesto.
- O backend C++ rejeita coordenadas não finitas ou fora de 0..65535 antes de alterar a tabela local de slots.
- O timeout de backpressure do `write()`/`poll()` foi reduzido de 100 ms para 2 ms. `POLLERR`, `POLLHUP` e `POLLNVAL` são tratados como falha; o transporte fecha em estado inconsistente e pode ser recuperado pelo mecanismo existente.

## Testes

- Novos testes unitários para normalização dos cantos/centro em 1600×900, clamp, dimensões inválidas e NaN/Infinity.
- Mantidos os testes de política de roteamento display principal/secundário e fallback.

## Build

- `versionName`: `0.14.36`; `versionCode`: `52`.
- Artefato GitHub Actions: `keymapper-0.14.36-debug`.
- O workflow continua na sequência Lint → testes unitários → `assembleDebug` → upload.

## Limites

- Não foi possível confirmar a criação uinput, o viewport do InputReader ou o consumo do toque por um jogo sem teste no Samsung SM-N986B.
- Sem Android SDK/NDK e distribuição Gradle disponíveis neste ambiente, não há declaração de APK compilado nesta entrega. A verificação host de sintaxe C++ e os testes Kotlin isolados não substituem GitHub Actions nem teste físico.
