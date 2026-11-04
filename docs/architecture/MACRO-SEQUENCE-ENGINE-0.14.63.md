# Macro sequencial — arquitetura 0.14.63

## Separação dos conceitos

- **Combo** usa `HudButton.macro: List<Int>` e despacha controles em paralelo no mesmo evento físico.
- **Macro** usa `HudButton.sequence: List<MacroStep>` e executa uma sequência finita de ações ordenadas.
- Os dois formatos são persistidos separadamente; o normalizador remove referências incompatíveis e não converte silenciosamente Combo em Macro.

## Modelo das etapas

| Ação | Efeito | Duração
|---|---|---:|
| WAIT | Aguarda sem injetar toque | 10–10.000 ms |
| TAP | Toque curto no alvo configurado | 25 ms |
| HOLD | Mantém o contato no alvo e depois libera | 10–10.000 ms |
| SWIPE | Interpola do ponto inicial ao ponto final do controle Swipe | 50–2.000 ms |

Limites do modelo: 32 etapas por sequência, 1–20 ciclos, alvos referenciados por ID e verificados no carregamento.

## Cursor e executor

`MacroSequenceCursor` é lógica pura: recebe uma cópia das etapas e o número de ciclos, retorna um passo por chamada e informa término imediatamente após o último passo do último ciclo. Não possui referências a Handler, Android, TouchInjector ou UI.

`MappingEngine` mantém a orquestração de I/O na thread serializada do `TouchInjector`:

1. recebe a tecla/botão físico e cria um `MacroRun`;
2. valida e congela a lista de alvos do perfil;
3. solicita ao cursor a próxima etapa;
4. agenda WAIT/TAP/HOLD/SWIPE;
5. solicita o próximo passo somente depois da etapa atual terminar;
6. conclui ao acabar os ciclos ou cancela quando a tecla é solta, o motor pausa ou `releaseAll()` é executado.

O ponteiro é dedicado ao controle Macro e é liberado nos caminhos de cancelamento e conclusão. A execução usa o relógio do processo e não é sincronizada com o estado interno do jogo.

## Testabilidade

A política de alvos/duração, persistência e avanço de ciclos pode ser testada sem Android. A injeção real, agendamento do Handler e comportamento do backend touchscreen ainda exigem o build Android e teste em aparelho.
