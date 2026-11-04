# Referências de comportamento — KeyMapper 0.14.44

## Panda Mouse Pro

O histórico público de versões 13/14.2 descreve Continuous Camera Movement para Sight, prevenção de interrupções perto das bordas e melhor preservação de movimento subpixel em sensibilidades baixas. A amostra APK fornecida contém referências a `SightView`, `MouseView`, `TouchView`, `sightArea`, `touchArea`, `boundary`, `mouseX`, `mouseY` e funções de atualização de posição virtual. A amostra é ofuscada; isso não prova a implementação interna exata.

Fontes:
- https://apptopia.com/google-play/app/com.panda.mouse/about
- https://panda-mouse-pro-beta.apps112.com/

## K2er

A documentação do Aiming Mode descreve: `Boundary`, `Radius`, `Re-center Mode` (`Default`/`Nonstop`), `Re-center Delay`, `Release Delay`, `Sensitivity X/Y`, `Auto Release` e `Offset Correction`. Também avisa que o modo Nonstop pode não funcionar em todos os jogos.

Fonte:
- https://doc.k2er.com/mappings/aiming.html

## GG Mouse Pro 3

O histórico público da versão 3.1.6 registra “Freefire prevent view freeze option” e uma correção para reset do dedo controlador da visão e reativação do joystick. A análise estática da amostra 3.2.3 fornecida pelo usuário encontrou referências a tracking de estado de mira de extensão PUBG e classes de gestão de movimento para dedo, mas não identificou um algoritmo Free Fire específico para congelamento da visão.

Fontes:
- https://gg-mouse-pro-2.apk.cafe/
- https://apkfab.com/es/gg-mouse-pro/com.zjx.ztezscreenshot/versions

## SS KeyMouse Pro

A análise estática do split XAPK ARM64 fornecido mostra `libsspro_input.so`, JNI de captura/grab/recovery e JNI separado para abrir, mover e liberar toque virtual; strings incluem `epoll`, `eventfd`, `inotify`, `/dev/input` e `/dev/uinput`. Isso é evidência do desenho nativo esperado, não validação do comportamento em runtime.

## Decisão de implementação

O KeyMapper mantém sua própria implementação Kotlin/C++ (`evdev_capture.cpp`, `uinput_touch.cpp`, `TouchInjector`, `MappingEngine`). A versão 0.14.44 incorpora o comportamento público de Radius e Auto Release do K2er no controle Aim, mantém o movimento contínuo e subpixel estudados no Panda e preserva separação de captura/saída observada no SS KeyMouse. Não foram incorporados binários, código-fonte, assets ou bibliotecas proprietárias.
