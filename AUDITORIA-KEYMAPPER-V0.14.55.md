# Auditoria técnica — KeyMapper 0.14.55

## Escopo

Base: fonte 0.14.54. Esta etapa concentra-se no ciclo de vida visual do painel de configuração. Não modifica o motor de entrada nem amplia funções especializadas de assistência de combate.

## Fluxo analisado

| Camada | Arquivo | Verificação | Resultado 0.14.55 |
|---|---|---|---|
| Estado do controle | `data/HudButton.kt`, `AppSettings.kt` | Leitura da posição normalizada do controle selecionado | Sem mudança de schema |
| Geometria do display | `display/DisplayUtils.kt` | Dimensões reais do display e densidade por display | Reutilizado sem fallback inventado |
| Política | `overlay/OverlayWindowPlacementPolicy.kt` | Dimensões, margem, posição e clamp | Nova política pura, determinística |
| Janela Android | `overlay/OverlayManager.kt` | `LayoutParams`, gravidade, X/Y e foco | Usa TOP|LEFT e coordenadas explícitas |
| Conteúdo do painel | `overlay/ConfigPanelView.kt` | ScrollView e ajuste do teclado | Mantidos; `SOFT_INPUT_ADJUST_RESIZE` permanece |
| Captura de entrada | `CaptureView`, `MappingService` | Pausa da captura quando o painel abre/fecha | Sem alteração no fluxo; removida notificação duplicada |
| Persistência | `SettingsRepository` | Valores do painel e perfis | Sem alteração |

## Mudanças

- Nova política `OverlayWindowPlacementPolicy`.
- `OverlayManager.openConfig()` utiliza o retorno da política para tamanho, posição e gravidade física.
- Removida a chamada adjacente duplicada `onCaptureRequestChanged(captureWanted)` no caminho de fechamento do painel.
- Cinco testes JUnit adicionados e uma barreira estática integrada no workflow.

## Referências públicas usadas como critérios de produto

O SS KeyMouse Pro anuncia HUD flutuante com opacidade ajustável e mapeamento teclado/mouse para toques. A referência pública foi usada como critério de organização/uso do HUD, não como prova de implementação interna: https://play.google.com/store/apps/details?id=com.sousasantoslogic.sspro

A documentação pública do K2er apresenta uma biblioteca de mapeadores e parâmetros por componente; foi considerada apenas como referência de organização configurável, não como código a copiar: https://doc.k2er.com/

## Validação executada

- `OverlayWindowPlacementPolicy.kt` compilado com `kotlinc-jvm 1.9.0` e cinco cenários executados: PASS.
- `check-overlay-window-placement.sh`: PASS.
- `check-hud-placement-policy.sh`: PASS.
- `check-shizuku-session-isolation.sh`: PASS.
- `check-wireless-adb-boundary.sh`: PASS.
- `check-kadb-coordinates.sh`: PASS.
- `check-toolchain-compatibility.py`: PASS.
- `test-toolchain-compatibility.py`: PASS (quatro casos negativos).
- Testes nativos evdev/JNI: PASS.
- Sintaxe C++17: PASS.
- Contrato CMake/JNI/biblioteca: PASS.

## Não validado

- Gradle não iniciou porque o Wrapper não resolveu `services.gradle.org` (`UnknownHostException`). Portanto JUnit/Gradle, Lint e `assembleDebug` não estão confirmados.
- Nenhum APK foi gerado.
- Nenhum teste físico em Samsung Note 20 Ultra, Android 13, One UI ou DeX foi executado.
- Insets de recorte/áreas de gestos e comportamento de teclado IME em diferentes fabricantes precisam de teste físico.

## Conclusão

A política geométrica foi compilada e exercitada isoladamente e está conectada ao caminho real de criação da janela do painel. A integração Android completa ainda depende do GitHub Actions ou de um ambiente com Gradle e dependências disponíveis.
