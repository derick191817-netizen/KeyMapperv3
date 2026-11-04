# Comparação funcional — SS KeyMouse Pro e KeyMapper 0.14.64

## O que está documentado publicamente no SS KeyMouse Pro

A página oficial e a listagem da Google Play descrevem: mapeamento de teclas e botões do mouse para toques na tela; analógico WASD configurável; visão FPS; HUD flutuante com opacidade ajustável; suporte com Shizuku e permissão de sobreposição. O guia de Script descreve uma tecla que inicia sequência de ações com `tap`, `wait`, `swipe`, `hold`, `mouseUp`, `cursorMove`, repetição e loop. O guia também orienta coordenadas normalizadas em porcentagem e cancelamento de sequências contínuas ao soltar a tecla.

## Mapeamento de capacidades

| Capacidade | SS KeyMouse Pro (documentação pública) | KeyMapper 0.14.64 |
|---|---|---|
| Teclado/mouse para toque | Documentado | Implementado no motor atual |
| WASD configurável | Documentado como analógico WASD | Zona virtual WASD com movimentos simultâneos/diagonais |
| Visão FPS | Documentado | Aim/Sight com sensibilidade, modo de câmera e recentralização |
| HUD flutuante | Documentado, opacidade ajustável | Overlay HUD com edição e posicionamento limitado |
| Tap / Hold | Script descreve tap e hold | Controles próprios Tap e Hold |
| Fire individual | Não detalhado na página principal consultada | Controle próprio Fire |
| Swipe | Script documenta swipe | Controle Swipe e etapa Macro Swipe |
| Macro sequencial | Script com pausa, toque, arraste, segurar, cursorMove e loops | WAIT/TAP/HOLD/SWIPE, repetição finita e cancelamento por soltura |
| Combo simultâneo | Não detalhado na documentação pública consultada | Combo independente de Macro sequencial |
| Mouse/tecla hot-plug e recovery | FAQ orienta testar dispositivo, reiniciar mapper/Shizuku | Captura EVDEV com recuperação e política de sessão; hardware ainda requer teste |
| Joystick analógico físico | Não afirmado na página pública consultada | Não implementado; não deve ser apresentado como disponível |

## Decisões de engenharia

1. Reproduzir os comportamentos de produto documentados, não copiar código ou binários proprietários.
2. Manter captura física separada da geração de toques; isso facilita depuração de perdas, estados presos e recuperação.
3. Manter `InputManager` como saída principal compatível com display selecionado. uinput só pode ser preferido quando a rota e o display de destino forem comprovados no dispositivo.
4. Não apresentar Macro com `cursorMove` ou loops arbitrários como concluída até implementar e testar sua semântica integral. A versão atual oferece passos WAIT/TAP/HOLD/SWIPE e repetição finita.
5. Exibir limitações reais de hardware: WASD usa o joystick touchscreen virtual; eixos analógicos físicos não estão implementados.

## Fontes públicas

- SS KeyMouse Pro — página do produto: https://keymouse.com.br/index.html
- SS KeyMouse Pro — guia de ativação: https://keymouse.com.br/guia/ativar.html
- SS KeyMouse Pro — guia de Macros (Script): https://keymouse.com.br/guia/macros.html
- SS KeyMouse Pro — FAQ: https://keymouse.com.br/guia/faq.html
- Google Play — SS KeyMouse Pro: https://play.google.com/store/apps/details?id=com.sousasantoslogic.sspro
