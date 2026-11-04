# Classificação de códigos EV_KEY — 0.14.60

## Por que não usar BTN_MISC como corte simples

`EV_KEY` transporta tanto códigos `KEY_*` quanto `BTN_*`. Os códigos não estão separados em dois blocos contíguos. O cabeçalho UAPI do Linux define, por exemplo, `KEY_OK`/`KEY_INFO` depois das faixas de botões do mouse, joystick, gamepad e digitalizador. Também define faixas adicionais de botões D-pad/Grip e Trigger Happy em regiões posteriores.

Portanto, a regra `code < BTN_MISC` é insuficiente para reconhecer teclados de mídia, controles de navegação e alguns HID compostos. A regra inversa `code >= BTN_MISC` também não serve para classificar: confundiria botões com teclas válidas.

## Regra da 0.14.60

`isButtonCode()` exclui os intervalos BTN conhecidos no cabeçalho Linux:

- `BTN_MISC` até `BTN_9`;
- `BTN_MOUSE` até `BTN_TASK`;
- `BTN_JOYSTICK` até `BTN_DEAD`;
- `BTN_GAMEPAD` até antes de `BTN_DIGI`;
- `BTN_DIGI` até `BTN_TOOL_QUADTAP`;
- `BTN_WHEEL` até `BTN_GEAR_UP`;
- `BTN_DPAD` e `BTN_GRIP` (0x220–0x227);
- `BTN_TRIGGER_HAPPY` até `KEY_MAX`.

`isKeyboardKeyCode()` aceita códigos `KEY_*` válidos que não pertençam a esses intervalos. `hasKeyboardCapabilities()` aplica a regra ao bitmap `EVIOCGBIT(EV_KEY)` e continua detectando dispositivos compactos com uma única tecla.

## Tradução de entrada

O serviço entrega o código Linux à camada Kotlin. `LinuxInputKeyMapper` traduz os códigos conhecidos para `KeyEvent` Android. Os códigos reconhecidos que ainda não possuem tradução permanecem `null` e são ignorados: não existe fallback arbitrário que transforme um evento desconhecido em outra tecla.

## Casos cobertos

- Macro pad de uma tecla e teclado de três teclas.
- `KEY_OK`, `KEY_CHANNELUP` e outras teclas consumer/media acima de `BTN_MISC`.
- `BTN_LEFT`, `BTN_SOUTH`, `BTN_DPAD_*`, `BTN_GRIP*` e `BTN_TRIGGER_HAPPY*` rejeitados como teclas.
- HID misto com KEY e BTN simultâneos.
- Bitmap vazio/nulo.

## Referências técnicas

- Linux UAPI: https://github.com/torvalds/linux/blob/master/include/uapi/linux/input-event-codes.h
- Linux input event semantics: https://docs.kernel.org/input/event-codes.html
- Android `KeyEvent`: https://developer.android.com/reference/android/view/KeyEvent
