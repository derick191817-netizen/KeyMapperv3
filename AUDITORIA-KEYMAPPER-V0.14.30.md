# Auditoria funcional — KeyMapper 0.14.30

**VersionName:** `0.14.30`  
**VersionCode:** `46`  
**Base:** `0.14.29`  
**Foco:** primeiro bloqueio concreto no caminho de toque e limite do backend de captura.

## 1. Achado principal: overlay visual adicional sobre o toque

Na versão 0.14.29, `OverlayManager.show()` criava, quando `captureWindow` estava ativo, uma janela adicional `VirtualCursorView` (`TYPE_APPLICATION_OVERLAY`) com flags `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`. O `LayoutParams.alpha` dessa janela não era reduzido e permanecia no padrão 1,0. Ao mesmo tempo, a `HudView` em tela cheia usava `alpha = 0,8`.

No Android 12 ou superior, toques transmitidos através de janelas overlay não confiáveis são sujeitos ao limite de opacidade combinada. Uma janela de opacidade 1,0 já ultrapassa o limite de 0,8; sobrepor o cursor à HUD poderia fazer o Android descartar o toque direcionado à janela de destino. O posicionamento do cursor era justamente perto do ponto onde os gestos de toque da área de visão começavam.

**Conclusão:** é uma causa concreta e altamente plausível para `ACTION_DOWN` não chegar ao aplicativo de destino em Android 12+, especialmente quando o cursor visual está ativo. A confirmação final depende de reproduzir no telefone com logs de `InputDispatcher` e teste físico.

## 2. Correção aplicada

- O desenho do cursor foi movido para `HudView.drawCaptureCursor()`.
- `OverlayManager` deixou de criar e posicionar uma segunda janela para o cursor.
- `VirtualCursorView.kt` foi removido porque não tem mais referências.
- O cursor permanece estritamente visual; não altera posições, estado ou emissão de eventos.
- A HUD mantém a opacidade configurada em 0,8 e não há mais uma janela cursor com alpha 1 sobre o ponto de contato.

## 3. Caminho de entrada ainda existente

| Etapa | Implementação observada | Limite
|---|---|---|
| Mouse/teclado físico | `CaptureView` | Depende de janela focável e `Pointer Capture`; não é captura global.
| Processamento | `MouseInputProcessor`, `InputEventQueue`, `MappingEngine` | Precisa de validação física para latência, bordas, botões e teclas.
| Conversão para toque | `TouchInjector` | Gestos touchscreen sintetizados não garantem equivalência a mouse nativo.
| Injeção | `ShizukuInjector` / `InputManager` | `injectInputEvent` aceito não prova que o jogo processou o evento.
| Serviço privilegiado | `KeyMapperShellService` | A sondagem só testa abrir/fechar nós; não lê evdev, não usa `EVIOCGRAB` e não cria uinput.

## 4. Bloqueio arquitetural restante

A captura com Pointer Capture pertence à view focada. A janela 1×1 de captura do KeyMapper pode assumir o foco da janela do jogo. O Android encerra Pointer Capture quando a janela perde foco; logo, essa abordagem não equivale a um backend global que leia o dispositivo físico sem transferir o foco do aplicativo de destino.

O `KeyMapperShellService` nesta versão é apenas diagnóstico de permissões. Abertura O_RDONLY/O_WRONLY seguida de fechamento não demonstra que o processo consegue ler eventos reais, controlar o dispositivo ou produzir eventos uinput. O próximo backend precisa de prova de capacidade real e integração no `MappingService`, com consentimento explícito, filtro restrito ao dispositivo externo selecionado, tratamento de hot-plug e liberação garantida de estados pressionados. Não fazer `EVIOCGRAB` nem leitura global silenciosa como teste.

## 5. Verificações locais realizadas

- Fonte 0.14.29 comparada com a versão alterada para confirmar que a janela `VirtualCursorView` era adicionada no `OverlayManager` durante captura.
- Busca estática pós-alteração: nenhuma referência de produção a `VirtualCursorView`, `virtualCursor`, `cursorLp` ou `cursorWindowSize`.
- Conferido que o cursor agora é desenhado dentro da `HudView` e usa coordenadas de tela convertidas para coordenadas locais da view.
- Conferido que os parâmetros do HUD continuam `alpha = 0.8f` e `FLAG_NOT_TOUCHABLE` no modo de jogo.
- XML/YAML/build Gradle não alterados nesta mudança, exceto versionamento.

## 6. O que não foi verificado

- Não houve `assembleDebug`, `lintDebug` ou testes JUnit Gradle: este ambiente não tem Android SDK configurado e não tem a distribuição Gradle 8.8 cacheada; o wrapper depende de download externo.
- Não foi produzido APK.
- Não houve instalação nem teste físico no Samsung Note 20 Ultra SM-N986B, Android 13, One UI ou DeX.
- Não foi possível confirmar em runtime os avisos `Untrusted touch due to occlusion`, nem medir se os cliques e deltas são recebidos pelo jogo após a correção.

## 7. Veredito

A 0.14.30 remove um bloqueador de overlay verificável no código, que poderia fazer o Android descartar toques no Android 12+. Isso é uma correção funcional concreta, não uma prova de que o KeyMapper já está jogável. O principal limite remanescente é a captura de entrada baseada em Pointer Capture e foco da janela. É necessário testar ponta a ponta no dispositivo real e, se a captura perder foco ou o jogo não receber a entrada, implementar e validar um backend de captura privilegiado real antes de declarar o mapeador funcional.
