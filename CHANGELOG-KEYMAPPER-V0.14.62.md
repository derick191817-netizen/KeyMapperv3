# KeyMapper 0.14.62

## Macro sequencial implementada

- Criado `Kind.MACRO` como tipo de controle persistido e independente.
- Preservado o campo legado `HudButton.macro: List<Int>` exclusivamente para Combo simultâneo.
- Criado `MacroStep` com ações WAIT, TAP, HOLD e SWIPE, serialização JSON independente e regras de normalização.
- Limites: 32 passos, 1–20 repetições, pausas/holds de 10–10.000 ms, Swipe de 50–2.000 ms e Tap de 25 ms.
- Alvos TAP/HOLD aceitam controles STANDARD simples e FIRE; SWIPE aceita somente um controle SWIPE.
- Alvos Combo, Macro, Rapid Tap, Toggle, Double Tap, Aim ou inexistentes são rejeitados como destinos quando incompatíveis.
- O motor executa etapas em série no Handler de TouchInjector, com um ponteiro dedicado ao controle Macro.
- Soltura do atalho, pausa e `releaseAll()` cancelam tarefas e liberam o contato ativo.
- Macro não pode ser embutida em Combo; Combo não pode referenciar Macro.
- A biblioteca Avançada e o editor HUD flutuante exibem a ferramenta Macro e abrem o editor de sequência.
- UI permite adicionar/remover/editar pausas, toques, holds, swipes e repetição da sequência.
- Exclusão de um controle remove referências a ele em Combos e passos Macro.

## Validação

- Verificações host e arquitetura executadas; incluir novo `check-macro-sequence-contract.sh` no CI.
- Testes JUnit adicionados para elegibilidade, normalização, limites, repetição, diferenciação Combo/Macro e duração do Swipe.
- Build Gradle Android e teste no dispositivo ainda pendentes neste ambiente.
