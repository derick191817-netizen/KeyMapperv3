# Arquitetura da biblioteca HUD — 0.14.61

## Catálogo visível

### Controles principais

1. **Tap** — `Kind.STANDARD` + `Mode.TAP`.
2. **Hold** — `Kind.STANDARD` + `Mode.HOLD`.
3. **Fire** — `Kind.FIRE`, painel e rota de engine próprios; modo de toque único ou segurar.
4. **WASD / Stick** — abre a zona virtual W/A/S/D persistida no perfil. Não equivale a eixo analógico físico.
5. **Aim / Sight** — único controle de visão/câmera (`Kind.AIM`).
6. **Swipe** — `Kind.SWIPE`, origem no marcador, destino X/Y e duração.

### Avançados

- **Combo**: sequência simultânea de controles elegíveis por IDs; não é macro temporizada.
- **Rapid Tap**: repete toque enquanto a entrada física está pressionada, com intervalo limitado.
- **Scroll**: comportamento global de roda do mouse atualmente implementado.
- **Macro sequencial**: ainda não implementada.

## Separação de identidade e motor

Fire não é um novo alias visual. `Kind.FIRE` possui configuração e caminho de execução específicos (`fireKey`), incluindo escolha de input e vínculo opcional de Aim. A injeção de toque pode continuar sendo compartilhada por baixo, sem colapsar o contrato do controle.

Tap e Hold são ferramentas distintas para o usuário, mas compartilham o tipo persistido `Kind.STANDARD`, diferenciados por `Mode.TAP`/`Mode.HOLD`. Isso evita classes e backends duplicados para a mesma primitiva touchscreen.

Aim/Sight é uma única ferramenta. WASD e Stick permanecem uma ferramenta de zona virtual devido à implementação disponível. Criar um segundo item de joystick que apenas duplicasse a mesma zona seria redundante; suporte analógico de gamepad exige captura de eixos e contrato próprio antes de ser anunciado.

## Combo e migração

`HudButton.isCombo` persiste a identidade do Combo independentemente de a lista `macro` estar vazia. Para perfis antigos, `fromJson` infere Combo a partir do nome antigo ou de uma lista de destinos não vazia. Um Combo sem destinos não dispara o próprio ponto. A política também recusa auto-referência, Combos aninhados, Swipe e Rapid Tap como alvo.

## Fluxo de configuração

`MappingEditorScreen` adiciona tipo/modo via `MappingService`; `HudButton` mantém o estado serializável; `ConfigPanelView` atualiza os parâmetros específicos; o serviço persiste o perfil e o `MappingEngine` executa pela rota do tipo. Prévia, tabela e painel usam labels comuns da política para reduzir divergência de UI.

## Regras de integridade

- IDs únicos e persistência de posição/associação.
- Fire sugere M1 somente se o binding estiver livre; conflito resulta em controle sem associação, não em roubo silencioso.
- Swipe cancela o gesto quando a entrada física é solta.
- Rapid Tap cancela repetição na soltura.
- `releaseAll` encerra tarefas pendentes e libera contatos.
- Edição do HUD deve ser separada da injeção no jogo.
- Recursos ainda não implementados devem aparecer como indisponíveis/informativos, jamais como ação funcional fictícia.
