# KeyMapper 0.14.54 — posicionamento inteligente de controles HUD

## Problema identificado

A inspeção do editor mostrou que `addButton()` sempre criava controles em `(0.5, 0.5)` e que `duplicateSelected()` usava apenas um deslocamento fixo de `4,5%`. Ambos os caminhos podiam criar sobreposição com controles existentes, especialmente em perfis mais densos.

## Implementação

- Criada a política pura `HudPlacementPolicy`, independente de Android e testável na JVM.
- A política procura uma posição normalizada dentro dos limites seguros do controle, maximizando a distância mínima até os controles já existentes.
- A colocação de controles padrão considera controles HUD existentes e o joystick; a zona de visão livre não é tratada como um botão ocupado. Tipos especializados mantêm a posição anterior nesta etapa.
- `addButton()` aplica a nova política aos controles padrão: prefere o centro quando a área está vazia, mas procura uma região com maior espaço quando ele já está ocupado. Tipos especializados mantêm a posição legada.
- `duplicateSelected()` aplica a mesma política apenas aos controles padrão e mantém o deslocamento como preferência inicial. Tipos especializados conservam o posicionamento anterior.
- IDs, associação de teclas, configuração e persistência existentes foram preservados.
- Adicionados cinco cenários de teste JVM e uma checagem arquitetural no GitHub Actions.

## Referências funcionais

A atualização melhora um aspecto do editor alinhado ao padrão de keymap visual observado nos mapeadores de referência: a criação e duplicação de um controle devem deixar o usuário com um item visível e selecionável, sem sobrepor automaticamente um item já posicionado. A matriz de comparação e os limites da pesquisa estão em `docs/COMPARACAO-FUNCIONAL-REFERENCIAS-0.14.54.md`.

## Versão e validação

- `versionName`: `0.14.54`
- `versionCode`: `70`
- Artefato de CI: `keymapper-0.14.54-debug`
- Foram executados cinco cenários diretos com `kotlinc` 1.9.0 e Java 21: centro em canvas vazio, desvio de controle ocupado, limites seguros, geometria inválida e entradas ocupadas inválidas.
- Foram executadas as verificações host/nativas e de contratos disponíveis; o resultado detalhado está em `TEST-RESULTS-KEYMAPPER-V0.14.54.txt`.
- `lintDebug`, JUnit via Gradle e `assembleDebug` dependem de acesso ao Gradle Wrapper e devem ser confirmados no GitHub Actions. Nenhum teste físico foi executado nesta etapa.
