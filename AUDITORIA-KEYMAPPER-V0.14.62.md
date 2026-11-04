# Auditoria técnica — KeyMapper 0.14.62

## Escopo

Auditoria e implementação da Macro sequencial na base local 0.14.61. O repositório GitHub remoto não foi modificado por esta entrega.

## Correção arquitetural

Antes desta versão, `HudButton.macro: List<Int>` representava apenas Combo: acionamento simultâneo de outros controles. Reutilizá-lo para uma macro sequencial confundiria formatos persistidos e poderia reinterpretar perfis antigos. A 0.14.62 mantém esse campo legado e introduz `sequence: List<MacroStep>` para Macro.

## Modelo e limites

- `Kind.MACRO = 4`; tipo salvo como `"MACRO"`.
- Ações WAIT, TAP, HOLD e SWIPE.
- Máximo de 32 passos; 1 a 20 execuções; duração de WAIT/HOLD de 10 a 10.000 ms; Swipe de 50 a 2.000 ms; Tap de 25 ms.
- WAIT não referencia controle; TAP/HOLD só usam STANDARD simples ou FIRE; SWIPE só usa controle SWIPE.
- Normalização remove destinos apagados, referências ao próprio Macro, controles incompatíveis e referências em ciclo.

## Execução e liberação

O motor captura uma cópia dos passos e dos destinos no início da execução. As etapas são processadas na thread serializada de `TouchInjector`. O controle Macro usa um pointerId próprio; TAP/HOLD liberam após a duração; SWIPE interpola origem e destino; WAIT não injeta toque. O atalho físico permanece a condição de execução e seu `ACTION_UP` cancela a tarefa ativa. `releaseAllOnHandler()` também cancela callbacks e libera eventual pointer ativo.

## Separação Combo/Macro

Combo continua simultâneo e usa a lista de IDs legada. Macro é sequencial e usa lista estruturada de passos. A política impede Combo → Macro e Macro aninhada. Combo vazio continua sem ação.

## UI

- Macro aparece em Avançado na biblioteca principal.
- Macro também aparece no seletor da biblioteca HUD flutuante.
- Painel permite configurar atalho, etapas, alvos, duração, repetição e aparência.
- A prévia identifica Macro com marcador M e cor própria.

## Testes

Adicionados `MacroStepPolicyTest`, casos em `MappingDataPolicyTest`/`MappingEditorPolicyTest` e `tools/architecture-tests/check-macro-sequence-contract.sh`. Os testes host foram executados nesta sessão. JUnit via Gradle, Lint, APK e teste físico continuam pendentes porque o Gradle Wrapper não conseguiu baixar a distribuição no ambiente.

## Limites conhecidos

- Scroll continua sendo associação global da roda do mouse.
- Captura de eixos analógicos físicos de gamepad continua fora do escopo desta versão.
- Não há sincronização de passos com tempo interno do jogo; a macro usa os atrasos configurados e o relógio do processo.
