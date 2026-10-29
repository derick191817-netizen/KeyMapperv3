# Auditoria total — KeyMapper 0.13.0

## Achados classificados

### Alta prioridade

1. **Possíveis IDs repetidos no perfil global:** `AppSettings.fromJson` aceitava IDs repetidos no array global, diferente de `AppProfile.fromJson`, que já removia duplicatas. Como o motor indexa ponteiros pelo ID do controle, dois marcadores com o mesmo ID poderiam partilhar estado de ponteiro. Corrigido por `MappingDataPolicy.normalizeButtons` em leitura e gravação de configurações e perfis.
2. **Persistência atrelada ao serviço:** chamadas assíncronas de `persist()` usavam o escopo de `MappingService`, cancelado em `onDestroy()`. Corrigido com escopo de persistência da aplicação; o escopo do serviço segue responsável pelo coletor de configurações, listeners e demais tarefas que devem parar no encerramento do serviço.

### Prioridade alta adicional

3. **Conflito entre atalhos globais e controles de perfis:** o serviço intercepta as teclas globais de editar/pausar antes de encaminhá-las ao motor. Selecionar uma tecla que já estivesse associada a qualquer controle podia inutilizar essa associação. A tela de configurações agora analisa controles globais e de todos os perfis carregados, mostra o controle conflitante e bloqueia Salvar até o conflito ser resolvido. A regra pura possui teste dedicado.

### Prioridade média

4. **Captura de mouse incompleta:** o painel anunciava suporte a botões do mouse, mas o modo de captura livre só processava `KeyEvent`. Agora o painel captura `ACTION_DOWN` de fonte mouse e `ACTION_BUTTON_PRESS` para os botões primário, secundário e terciário e converte para os códigos internos -1, -2 e -3. Botões adicionais não são mapeados e recebem mensagem explícita.
5. **Semântica incorreta do botão -3:** era exibido como “Scroll”, embora o pipeline o associe a `MotionEvent.BUTTON_TERTIARY` (botão central). Corrigido para “Mouse do Meio”. A roda de rolagem não é declarada como suportada.
6. **Opções de atalho incompatíveis com valor salvo:** o seletor listava apenas F1–F12, embora o atalho de pausa padrão fosse NUM3. As opções agora mantêm o valor atual e oferecem também F1–F12.
7. **Campos globais sem normalização uniforme:** estilo do HUD, tema, taxa de atualização, modo de desempenho e atalhos podiam receber valores fora da faixa através de JSON. Foram incluídos limites e fallback de atalhos distintos.

## Fluxos examinados

- **Editor/overlay:** `HudView` → `OverlayManager.setEditing/openConfig` → `ConfigPanelView`; a edição permanece na janela `TYPE_APPLICATION_OVERLAY`. A captura do botão do mouse foi conectada no painel de propriedades.
- **Motor:** `CaptureView` → `MappingService.handleKey`/callbacks → `MappingEngine` → `TouchInjector`/`ShizukuInjector`.
- **Perfil:** `SettingsRepository` → `AppSettings.fromJson`/`AppProfile.fromJson` → normalização dos controles; na gravação, `AppSettings.toJson`/`AppProfile.toJson` também normalizam a lista.
- **Gravação pelo overlay:** snapshot é capturado antes de lançar o trabalho e o trabalho não depende mais do escopo de vida do serviço.
- **CI:** workflow já compila Kotlin, roda `testDebugUnitTest`, gera APK Debug, valida pacote e checksum, e publica artefato. Atualizada a versão do upload de artefatos para `v7.0.2` (release publicada no repositório oficial: https://github.com/actions/upload-artifact/releases).

## Arquivos principais alterados

- `app/build.gradle.kts`
- `app/src/main/java/com/example/keymapper/KeyMapperApp.kt`
- `app/src/main/java/com/example/keymapper/data/AppSettings.kt`
- `app/src/main/java/com/example/keymapper/data/AppProfile.kt`
- `app/src/main/java/com/example/keymapper/data/HudButton.kt`
- `app/src/main/java/com/example/keymapper/data/MappingDataPolicy.kt` (novo)
- `app/src/main/java/com/example/keymapper/domain/InputBindingPolicy.kt` (novo)
- `app/src/main/java/com/example/keymapper/domain/MappingEditorPolicy.kt` e `SettingsScreen.kt` (validação de atalhos globais contra todos os perfis)
- `app/src/main/java/com/example/keymapper/overlay/ConfigPanelView.kt`
- `app/src/main/java/com/example/keymapper/service/MappingService.kt`
- `app/src/main/java/com/example/keymapper/ui/SettingsScreen.kt`
- `app/src/test/java/com/example/keymapper/data/MappingDataPolicyTest.kt` (novo)
- `app/src/test/java/com/example/keymapper/domain/InputBindingPolicyTest.kt` (novo)
- `app/src/test/java/com/example/keymapper/domain/MappingEditorPolicyTest.kt` (teste de conflito de atalho global)
- `.github/workflows/build.yml`

## Removidos

Foram removidos apenas os JARs de smoke test gerados manualmente em `.validation/`; são binários locais obsoletos, não dependências do app. A fonte `ScreenGeometrySmoke.kt` foi mantida. Nenhuma funcionalidade do aplicativo ou perfil do usuário foi removida.

## Pendências

- Build Android completo, testes JUnit do Gradle e geração de APK.
- Teste de mouse real no painel e verificação de `ACTION_BUTTON_PRESS`/`ACTION_DOWN` conforme o driver do dispositivo.
- Teste no jogo real sobre o overlay e verificação da política de foco do OEM.
- Teste de persistência após fechar o serviço e durante várias gravações rápidas.
- Teste de injeção via Shizuku, liberação de entrada em interrupção e comportamento em múltiplos displays.
