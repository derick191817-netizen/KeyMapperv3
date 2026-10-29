# Relatório técnico — KeyMapper 0.11.0

## Base

Derivado da cópia local v0.10.0 disponível nesta sessão. Não foi feita sincronização com o repositório remoto do usuário.

## Problema e correção

O listener de telas comparava apenas `displayId` e `densityDpi`. Quando a resolução efetiva mudava sem alteração desses dois valores — por exemplo, rotação ou redimensionamento de janela DeX — o serviço só chamava `overlay.refresh()`. Isso redesenhava o conteúdo, mas não reconstruía os `LayoutParams` e as dimensões capturadas no momento da montagem das janelas.

Agora `ScreenGeometryPolicy` distingue:

- `NONE`: mesmas dimensões, monitor e densidade; apenas atualiza o overlay.
- `RESIZE`: dimensões mudaram; recria as janelas preservando o modo de edição e a pausa.
- `REINITIALIZE`: monitor ou densidade mudou; executa a reinicialização completa já usada pelo projeto.

O método `rebuild` aceita `preserveEditing`; depois de mostrar as janelas novas, reaplica o modo de edição no novo `HudView` ou mantém a captura desligada quando pausado. `engine.releaseAll()` continua sendo chamado antes da transição, para evitar botões/dedos presos durante a troca de geometria.

## Arquivos alterados

- `app/src/main/java/com/example/keymapper/display/ScreenGeometryPolicy.kt` — classificação pura dos eventos de geometria.
- `app/src/main/java/com/example/keymapper/service/MappingService.kt` — usa a política e preserva a sessão em resize.
- `app/src/test/java/com/example/keymapper/display/ScreenGeometryPolicyTest.kt` — quatro cenários de regressão.
- `app/build.gradle.kts` — `versionCode = 12`, `versionName = 0.11.0`.

## Validação feita

- Política Kotlin pura compilada isoladamente com `kotlinc`.
- Cenários de teste executados por um harness Kotlin sem dependências externas: igualdade, mudança de dimensões, mudança de monitor/DPI e prioridade de mudança de monitor.
- Manifest XML, YAML do workflow e integridade do pacote serão verificados após empacotamento.
- Build Android completo, execução JUnit pelo Gradle, APK e teste em dispositivo ainda não estão confirmados nesta sessão. A tentativa anterior via Gradle falhou ao resolver `services.gradle.org` por indisponibilidade de DNS/rede no ambiente.

## Limites

A correção cobre o caminho do serviço para mudanças detectadas por `DisplayManager.DisplayListener`. A validação final de janelas `TYPE_APPLICATION_OVERLAY`, foco/captura de mouse e Shizuku exige executar o APK em aparelho/DeX real. O projeto não foi publicado nem enviado a um repositório remoto.
