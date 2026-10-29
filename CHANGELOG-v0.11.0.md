# KeyMapper 0.11.0 — rotação e redimensionamento do overlay

## Correção

- A mudança de tela passou a comparar monitor, densidade e dimensões reais, em vez de considerar apenas monitor/DPI.
- Rotação ou redimensionamento recria as janelas para que cursor, HUD, bolha e painel usem a nova geometria.
- Durante esse tipo de resize, o modo de edição e o estado de pausa são preservados; o editor é reaplicado após recriar o HUD.
- Mudança de monitor ou densidade continua usando reinicialização completa, porque pode exigir outro contexto de janela.
- Adicionada uma política pura e determinística para classificar mudanças de geometria, com quatro cenários unitários.

## Compatibilidade

- Nenhuma biblioteca ou permissão nova foi adicionada.
- Não modifica a forma como eventos de mouse/teclado são injetados; garante que a reconstrução das janelas acompanha a geometria disponível.
