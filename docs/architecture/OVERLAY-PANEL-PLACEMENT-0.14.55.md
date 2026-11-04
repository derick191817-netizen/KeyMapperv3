# Posicionamento do painel overlay — 0.14.55

## Problema confirmado

`OverlayManager.openConfig()` escolhia apenas gravidade `START/END` em paisagem e `TOP/BOTTOM` em retrato. Não havia um contrato puro que calculasse explicitamente dimensões e coordenadas com margem em função da geometria do display. Como as janelas overlay usam `FLAG_LAYOUT_NO_LIMITS`, a posição implícita era uma fragilidade para resoluções reduzidas, janelas DeX e mudanças de proporção.

## Solução

`OverlayWindowPlacementPolicy.forControl()` recebe:

- largura e altura do display em pixels;
- densidade da interface;
- posição normalizada do controle selecionado.

Retorna largura, altura, X, Y e orientação. Em paisagem, o painel é posicionado no lado físico oposto ao controle; em retrato, na borda vertical oposta. A margem nominal é de 24 dp, limitada a 8% da menor dimensão do display para que uma densidade alta não consuma o espaço útil de telas pequenas. Valores não finitos são tratados como centro/densidade padrão.

`OverlayManager` converte a saída em `WindowManager.LayoutParams` com `Gravity.TOP | Gravity.LEFT`, `x` e `y` explícitos. Isso evita que a direção RTL mude a interpretação de START/END.

## Garantias testadas

- Painel de paisagem cabe dentro da margem e troca de lado conforme o controle selecionado.
- Painel de retrato cabe dentro da margem e escolhe a borda oposta ao controle.
- Resolução 320×240 e densidade 3.0 não produzem posições negativas ou overflow.
- Densidade/posições inválidas usam fallback determinístico.
- Display de 1×1 retorna geometria válida (1×1 em 0,0).

## Limites

Esta política usa as dimensões informadas por `DisplayUtils.info()`. Não calcula recortes específicos do fabricante, áreas de gestos ou insets dinâmicos da barra do sistema; a margem geométrica reduz o risco de encostar nas bordas, mas testes físicos em Samsung One UI/DeX continuam necessários. O ajuste do IME continua usando `SOFT_INPUT_ADJUST_RESIZE` na janela do painel.
