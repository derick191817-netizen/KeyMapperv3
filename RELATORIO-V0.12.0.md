# Relatório técnico — KeyMapper 0.12.0

## Classificação do resultado

**Validação parcial; aplicativo Android ainda não compilado.** Não é correto classificar a versão como compilada ou testada no dispositivo.

## Auditoria e correções realizadas

1. **Editor falso na tela principal:** o `MappingEditorScreen` anterior oferecia uma área simulada 16:9. Essa área foi removida. A tela atual informa que o posicionamento é feito no overlay real e mostra pré-requisitos para abrir o app selecionado.
2. **Sobreposição real:** `OverlayManager` mantém janelas Android `TYPE_APPLICATION_OVERLAY`. Essa existência foi verificada no código-fonte, mas não confirma que a janela consegue permanecer acima de todos os jogos/dispositivos.
3. **Gestos no HUD:** os alvos da barra só executam no `ACTION_UP` se a distância desde o início estiver dentro do touch slop e o toque terminar dentro do alvo. Arraste do corpo/alça é tratado como movimento, não clique.
4. **Seleção sobreposta:** os candidatos são avaliados pela distância relativa ao raio; escolhe-se o marcador com menor distância normalizada.
5. **Persistência da sessão:** `HudView` oferece Salvar e sair e Cancelar sessão. Cancelar restaura `AppSettings` guardado no início da sessão e persiste a restauração; o X também descarta a sessão antes de fechar o overlay. O snapshot é mantido pelo `OverlayManager` quando as janelas são reconstruídas por alterações de tela, enquanto o serviço continua vivo.
6. **Criação e duplicação:** IDs livres são escolhidos por `MappingEditorPolicy`; há limite de 128 controles; conflitos não são duplicados silenciosamente; cópias ficam sem tecla até serem reconfiguradas.
7. **Entrada e foco:** `CaptureView` consome eventos de teclado quando o editor está ativo e mantém o processamento de movimento do mouse parado. O painel de propriedades recupera o foco para edição. O comportamento real depende da política de foco das janelas de sobreposição do fabricante/versão Android.
8. **Exclusão e referências:** a exclusão remove referências `macro` e `linkAimId` para o ID excluído. `MappingEngine` não executa controles cuja tecla seja `0`.
9. **Políticas testáveis:** regras do editor foram separadas em `MappingEditorPolicy.kt` e têm cobertura de testes JUnit adicionada, embora esses testes JUnit não tenham sido executados pelo Gradle neste ambiente.

## Arquivos modificados

- `app/build.gradle.kts`
- `app/src/main/java/com/example/keymapper/MainActivity.kt`
- `app/src/main/java/com/example/keymapper/data/HudButton.kt`
- `app/src/main/java/com/example/keymapper/input/CaptureView.kt`
- `app/src/main/java/com/example/keymapper/mapping/MappingEngine.kt`
- `app/src/main/java/com/example/keymapper/overlay/ConfigPanelView.kt`
- `app/src/main/java/com/example/keymapper/overlay/HudView.kt`
- `app/src/main/java/com/example/keymapper/overlay/OverlayManager.kt`
- `app/src/main/java/com/example/keymapper/ui/HomeScreen.kt`
- `app/src/main/java/com/example/keymapper/ui/MappingEditorScreen.kt`

## Arquivos adicionados

- `app/src/main/java/com/example/keymapper/domain/MappingEditorPolicy.kt`
- `app/src/test/java/com/example/keymapper/domain/MappingEditorPolicyTest.kt`
- `CHANGELOG-v0.12.0.md`
- `RELATORIO-V0.12.0.md`
- `AUDITORIA-EDITOR-V0.12.0.md`

## Validações executadas

- **34/34** assertions do smoke test Kotlin puro passaram, compilando `MappingEditorPolicy`, `SensitivityEngine`, `VirtualCursorController`, `MouseInputProcessor` e `ScreenGeometryPolicy` junto do harness independente.
- **10/10** verificações estáticas do contrato de fonte passaram: retirada do Canvas falso, existência de `TYPE_APPLICATION_OVERLAY`, salvar/cancelar, duplicação, política de gesto, bloqueio de teclas na edição, ignorar tecla 0, presença dos testes e versionamento.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace` **falhou antes de iniciar o build** porque o Wrapper não conseguiu resolver `services.gradle.org` (`UnknownHostException`). Não é um erro de compilação Kotlin/Android conhecido; é um bloqueio de obtenção da distribuição Gradle.

## Ainda não validado

- Compilação de todo o módulo Android e execução do conjunto JUnit.
- Geração/instalação de APK.
- Abrir editor sobre jogo real e confirmar visibilidade dos botões de jogo.
- Captura real de teclas enquanto a janela do HUD está em edição e entrada do teclado no painel de propriedades.
- Injeção de eventos, foco e limpeza de entradas em jogos/dispositivos diferentes.
- Sobrevivência a encerramento forçado do processo e reconstrução de overlay em DeX/rotação com alterações não salvas.

## Integridade/proveniência

Este ZIP é uma atualização do projeto local de trabalho baseado no pacote v0.11.0 disponível nesta sessão. Não foi clonado do repositório remoto, enviado ao GitHub nem instalado em um dispositivo físico. O Wrapper real e a configuração de build do projeto foram preservados.
