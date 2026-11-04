# Changelog — KeyMapper 0.14.35

**versionCode:** 51  
**Base:** KeyMapper 0.14.33 (fonte validada pelo SHA-256 fornecido)

## Backend touchscreen virtual — referência funcional KeyMouse Pro

- Implementado transporte opcional de saída touchscreen com C++17/JNI e Linux `uinput` no processo privilegiado do Shizuku UserService.
- O dispositivo virtual anuncia `INPUT_PROP_DIRECT`, eixos absolutos e multitouch Protocol B (`ABS_MT_SLOT`, `ABS_MT_TRACKING_ID`, `ABS_MT_POSITION_X/Y`, `ABS_MT_PRESSURE`), além dos estados `BTN_TOUCH` e `BTN_TOOL_FINGER`.
- Os IDs de ponteiro do `MotionEvent` são mantidos como slots estáveis; coordenadas da tela são normalizadas para a faixa configurada do dispositivo virtual.
- A sessão virtual suporta DOWN, POINTER_DOWN, MOVE, POINTER_UP, UP e CANCEL/liberação total. Falha parcial de escrita destrói o dispositivo para eliminar slots locais divergentes.
- O bridge AIDL consulta, abre, envia quadros, libera e fecha o backend separadamente da captura evdev; quando o descritor fecha, agenda reabertura serial com cooldown de 5 s.
- A abertura do dispositivo é idempotente se o touchscreen já estiver válido, evitando recriação durante tentativas de reiniciar somente a captura física.
- O backend valida IDs exclusivos e o conjunto de slots esperado em cada quadro; uma falha parcial destrói o dispositivo sem recursão entre liberação e fechamento.
- `InputManager` permanece como fallback automático quando uinput não pode ser criado ou deixa de funcionar.
- uinput é limitado ao display ID 0. Displays externos/DeX continuam no `InputManager` com seleção explícita de display, pois um dispositivo uinput touchscreen não recebe `displayId` do app.
- Mudança de display, perda do Binder, rejeição de quadro e cancelamento tentam encerrar todos os contatos por uma operação atômica `releaseAll`; quando a liberação não é confirmada, o motor bloqueia novos contatos e repete a tentativa.
- Adicionados testes unitários da política de roteamento para display primário, display externo, ID inválido e fallback.

## Integração e DevOps

- Incrementado `versionName` para `0.14.35` e `versionCode` para `51`.
- Artefato do GitHub Actions atualizado para `keymapper-0.14.35-debug`.
- O workflow mantém a ordem Lint → testes unitários → `assembleDebug` → upload do APK.

## Limites e compatibilidade

- A criação real do nó `/dev/uinput`, os ioctls `UI_DEV_SETUP`/`UI_ABS_SETUP`, a classificação do dispositivo pelo Android InputReader e o viewport associado variam por kernel, firmware, UID e SELinux.
- O backend não garante que o jogo consumiu o toque. O status positivo comprova apenas que o quadro foi aceito pelo transporte/kernel, não que chegou à aplicação final.
- O roteamento uinput não é usado em DeX/monitor externo; estes continuam dependendo de `InputManager` e da API de seleção de display.
- O KeyMouse Pro foi usado como referência funcional, sem copiar biblioteca, código ou implementação proprietária.
- Não foram alterados os parâmetros atuais de mira, disparo, sensibilidade ou perfis do MappingEngine.
