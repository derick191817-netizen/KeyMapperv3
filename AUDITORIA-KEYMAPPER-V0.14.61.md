# Auditoria — KeyMapper 0.14.61

## Escopo

Auditoria focada na biblioteca HUD e no encadeamento entre seleção, modelo persistido, configuração, engine e exibição. Base analisada: fonte 0.14.60 preparada localmente e atualizada para 0.14.61.

## Resultado por controle

| Ferramenta | Estado | Comportamento real / limite |
|---|---|---|
| Tap | Implementado | `Kind.STANDARD` + `Mode.TAP`; toque curto em posição configurada. |
| Hold | Implementado | `Kind.STANDARD` + `Mode.HOLD`; mantém o contato até a soltura/cancelamento. |
| Fire | Implementado como tipo independente | `Kind.FIRE`, painel próprio, entrada física própria, modo Toque único/Segurar, posição e vínculo opcional com Aim. Reutiliza a infraestrutura comum de toque, mas tem rota e política próprias. |
| WASD / zona virtual | Implementado | Abre a configuração da zona virtual W/A/S/D persistida no perfil. |
| Joystick analógico físico | Não implementado | A biblioteca não promete captura de eixo analógico de gamepad. A etiqueta “WASD / Stick” refere-se à zona virtual existente. |
| Aim / Sight | Implementado | Um único controle de câmera (`Kind.AIM`), com configuração X/Y, recentralização e modos disponíveis no motor. |
| Swipe | Implementado | `Kind.SWIPE`; associação, origem, destino X/Y e duração; soltura/cancelamento encerra o gesto. |
| Combo | Implementado como disparo simultâneo | Lista IDs de controles elegíveis e aciona os alvos. Não é macro sequencial temporizada. Combo vazio não injeta toque. |
| Rapid Tap | Implementado | Repetição limitada enquanto a entrada física permanece pressionada; cancelamento na soltura. Não é aplicado ao Fire. |
| Scroll | Implementado como mapeamento global | Roda vertical/horizontal tratada pela configuração global existente; não é instância de controle HUD. |
| Macro sequencial | Não implementado | Não deve ser apresentado como ferramenta funcional até existir scheduler, modelo de passos, cancelamento e testes. |

## Pontos corrigidos

1. **Fire estava ausente como opção própria no primeiro nível da biblioteca.** Agora é terceiro item, após Tap e Hold, com ícone, cor, painel e rota de motor próprios.
2. **Tap e Hold eram pouco explícitos na biblioteca.** Agora são itens separados, mesmo compartilhando `Kind.STANDARD` internamente.
3. **Aim e Sight podiam parecer ferramentas duplicadas.** Foram consolidadas em uma única ferramenta “Aim / Sight”.
4. **WASD e Joystick analógico não devem ser falsamente equivalentes.** O item WASD / Stick abre apenas a zona virtual suportada e a UI declara o limite do backend.
5. **Swipe não tinha caminho de configuração completo no mesmo painel.** Agora expõe associação, destino e duração junto da configuração do tipo.
6. **Combo vazio podia ser confundido com um controle Tap comum.** `isCombo` é agora persistido independentemente de `macro` não vazia; o engine retorna sem injetar toque quando não há alvos.
7. **Combo podia aceitar tipos cuja semântica não pode ser composta com segurança.** A política recusa destino próprio, Combo aninhado, Swipe e Rapid Tap.
8. **Macro sequencial e Scroll individual não tinham motor/estado de instância completos.** A interface informa corretamente que Macro sequencial não está implementada e que Scroll é global.

## Fluxo verificado estaticamente

`Biblioteca HUD → MappingService.addControl → HudButton JSON/profile → ConfigPanelView → MappingEngine → TouchInjector`

- A biblioteca envia o tipo/modo explícito.
- O serviço cria ID e posição; Fire sugere M1 apenas se não houver conflito.
- O modelo persiste `kind`, `mode`, configuração, posição, associação e `isCombo`.
- O painel altera o mesmo controle persistido e expõe campos específicos.
- O engine diferencia Fire, Aim, Swipe e Standard; Rapid Tap e Combo têm caminhos condicionais próprios.
- A soltura/cancelamento encerra Rapid Tap e Swipe e `releaseAll` executa a limpeza de contatos.

A auditoria de contratos não equivale a teste Android em runtime. A execução de callbacks, foco e comportamento de janelas requer teste real no aparelho.

## Riscos ainda abertos

- **Fire/Hold com foco perdido ou dispositivo desconectado:** manter validação física de que todos os contatos sejam liberados em perda de foco, stop e reconexão. A política `releaseAll` existe; o comportamento do driver Android e do jogo não foi exercitado no telefone.
- **Aim/Sight continuous camera:** depende do backend de captura/injeção, permissões e flags de janela. A biblioteca apenas expõe a ferramenta já existente; não é prova de movimento infinito em todos os jogos.
- **WASD / Stick:** zona virtual é suportada; eixo analógico físico não é.
- **Combo:** simultâneo e restrito aos tipos elegíveis; não deve ser confundido com uma sequência.
- **Swipe:** duração e destino são configuráveis; a sensação e o timing devem ser medidos no Android real.
- **Compose/Android compilation:** Lint e JUnit Gradle não foram executados por falta de download da distribuição Gradle 8.8.

## Estado de integração

Pacote local 0.14.61 (`versionCode 77`). O repositório remoto não foi atualizado pela integração disponível; falha HTTP 403 ao criar branch. Não existe APK 0.14.61 validado nesta entrega.
