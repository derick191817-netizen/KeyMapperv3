# Changelog — KeyMapper 0.14.59

## Detecção de teclado HID

- Removido o mínimo arbitrário de quatro códigos de tecla para reconhecer um teclado evdev.
- Qualquer capacidade de tecla real entre `KEY_RESERVED` e `BTN_MISC` agora basta para classificar o nó como teclado.
- Macro pads de uma tecla, teclados de três teclas e numpads compactos deixam de ser ignorados.
- Códigos da faixa `BTN_*` não são mais encaminhados pelo ramo de teclado em dispositivos HID compostos.

## Testes adicionados

- Bitmap sem capacidades: não é teclado.
- Macro pad de uma tecla: teclado válido.
- Teclado de três teclas: teclado válido.
- `BTN_LEFT`/`BTN_GAMEPAD` sem teclas: não é teclado.
- HID misto: tecla normal reconhecida e botão `BTN_SOUTH` filtrado.
- Ponteiro nulo e bitmap vazio: retorno seguro.

## Compatibilidade

- Mantida a base de dependências Kadb Android 2.1.4 + mDNS Android 2.1.3 da 0.14.56.
- Mantida a política de sessão Shizuku idempotente da 0.14.58.
- `versionCode`: 75. `versionName`: 0.14.59.
