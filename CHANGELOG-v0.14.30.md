# Changelog — KeyMapper 0.14.30

## Correção no caminho visual de entrada

- Eliminada a janela de overlay independente `VirtualCursorView`.
- O cursor de captura passou a ser renderizado na `HudView` já existente.
- Evita uma janela `TYPE_APPLICATION_OVERLAY` adicional com opacidade padrão 1, que ficava sobre o HUD com alpha 0,8 no ponto de início do toque virtual.
- O desenho do cursor usa coordenadas de tela convertidas para o espaço local do HUD; ele não consome nem injeta eventos.
- Incremento de versão para `0.14.30` / `46`.

## O que esta versão ainda não comprova

- Não foi possível compilar o APK neste ambiente (sem Android SDK e sem distribuição Gradle cacheada/disponível).
- Não foi feito teste com mouse/teclado físicos no Samsung Note 20 Ultra/Android 13/DeX.
- A captura ainda usa Pointer Capture numa janela focável do KeyMapper; portanto, ainda pode haver disputa de foco com o jogo.
- A injeção retorna aceitação do InputManager, o que não comprova que o app de destino processou o toque.

Esta versão corrige uma causa concreta e provável de toques descartados em Android 12+, mas não deve ser anunciada como totalmente jogável sem teste ponta a ponta no aparelho-alvo.
