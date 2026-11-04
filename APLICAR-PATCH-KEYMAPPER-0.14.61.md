# Aplicação do patch — KeyMapper 0.14.61

## Base esperada

Patch cumulativo preparado para a base remota `main` que estava em 0.14.55 (`versionCode 71`). Ele incorpora a correção Kadb/Jetifier de 0.14.56 e as alterações locais até 0.14.61 (`versionCode 77`).

## Aplicar com segurança

1. Faça backup/commit do estado atual do repositório.
2. Confirme que sua branch não avançou além da base esperada. Se já estiver em 0.14.56 ou superior, compare os arquivos antes de sobrepor; não aplique cegamente sobre uma versão mais nova.
3. Extraia `KeyMapper-v0.14.61-PATCH-OVERLAY.zip` diretamente na raiz do repositório, preservando a hierarquia. Não extraia dentro de uma pasta adicional.
4. Revise `git diff` e confirme em especial `app/build.gradle.kts`, `.github/workflows/build.yml`, o modelo `HudButton`, `MappingEngine`, `ConfigPanelView` e `MappingEditorScreen`.
5. Execute primeiro os scripts host listados em `TEST-RESULTS-KEYMAPPER-V0.14.61.txt`.
6. Com o Gradle 8.8 disponível, execute `./gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --stacktrace --console=plain`.
7. Só considere a versão pronta depois de instalar o APK gerado e testar captura, soltura, perda de foco, desconexão e reconexão dos periféricos no Android 13/One UI/DeX.

## Controles alterados

Biblioteca principal: Tap, Hold, Fire, WASD / Stick, Aim / Sight, Swipe. Fire é o terceiro item. Ferramentas avançadas: Combo e Rapid Tap funcionais; Scroll segue global; Macro sequencial e eixo analógico físico de gamepad permanecem declarados como não implementados.

## Não fazer

- Não substitua o repositório atual por um ZIP antigo sem comparar a versão/commit.
- Não remova o `isCombo` da serialização; ele distingue Combo vazio de Tap.
- Não converta Fire em Tap/Hold apenas por compartilhar o injetor.
- Não declare Macro sequencial, joystick analógico físico ou build Android aprovados sem implementação e teste correspondentes.
