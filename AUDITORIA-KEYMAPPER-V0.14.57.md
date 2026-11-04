# Auditoria técnica — KeyMapper 0.14.57

## Escopo e critério

Revisão estática do código-fonte 0.14.56 e alterações funcionais para 0.14.57. O critério central foi rastrear uma função de ponta a ponta: dado persistido → modelo efetivo → tela/overlay → ação → motor → persistência. Um recurso não é considerado concluído só porque existe uma classe ou campo sem ligação visível.

A amostra KeyMouse Pro previamente fornecida foi analisada estaticamente. Os símbolos e strings encontrados no binário são evidências de arquitetura prevista, não prova de funcionamento em todos os aparelhos. Não foi copiado código ou biblioteca proprietária.

## Arquitetura observada no KeyMapper

1. **Interface Android:** Kotlin + Jetpack Compose em `ui/`, com `SettingsViewModel` expondo `StateFlow<AppSettings>`.
2. **Persistência:** `SettingsRepository`/DataStore serializa `AppSettings`; `AppProfile` guarda layout e sensibilidade por package name. `SettingsViewModel` serializa gravações e reconcilia o estado otimista com o valor confirmado.
3. **Serviço:** `MappingService` coordena perfil em primeiro plano, estado do motor, pausa/edição, display, entrada nativa e sobreposições.
4. **Captura:** a camada nativa C++17/NDK enumera dispositivos evdev, acompanha teclado/mouse e envia lotes por JNI/AIDL através do UserService Shizuku. Há estado físico por dispositivo, proteção contra perda de lote e limpeza de sessões.
5. **Motor:** `MappingEngine` combina eventos de teclado, botões/deltas/scroll do mouse e estado do perfil; executa ações de toque, hold/toggle, duplo toque, macros, joystick e controle de câmera.
6. **Saída:** a rota de toque atual usa `TouchInjector`/`InputManager` no contexto privilegiado Shizuku, com eventos `MotionEvent` controlados pelo motor. Há sondagem/diagnóstico nativo, mas não se deve declarar `uinput` como backend produtivo sem teste de criação, roteamento por display e permissões reais.
7. **HUD:** `OverlayManager`, `HudView`, `ButtonRenderer` e `ConfigPanelView` desenham os controles e oferecem formulários específicos para Tecla, Tiro e Mira.

Fluxo resumido:

`USB/Bluetooth mouse + keyboard → evdev / captura Android → JNI/AIDL → MappingService → MappingEngine → TouchInjector → app/jogo`

`Compose Studio → SettingsViewModel → DataStore/AppProfile → MappingService → OverlayManager/HudView/ConfigPanelView`

## Referência KeyMouse Pro e implicações

A página e a listagem atuais do KeyMouse Pro apresentam mapeamento teclado/mouse para toques, joystick analógico WASD, FPS View e HUD flutuante com opacidade configurável. A interface observada usa ferramentas de edição flutuante e separa a seleção de jogos de configurações/resolução. Isso orienta a experiência, não determina que o KeyMapper deva copiar telas ou implementação fechada.

A amostra binária analisada anteriormente expõe uma divisão arquitetural entre captura de periféricos (`nativeStart`, `nativeStop`, refresh/census/recovery, `/dev/input`, `epoll`, `eventfd`, `inotify`) e saída de toque (`nativeTouchOpen/Close/Down/Move/Up`, strings `/dev/uinput`). Essa separação é uma boa referência: entrada e saída são subsistemas diferentes, com diagnósticos e falhas diferentes.

## Achados concretos corrigidos em 0.14.57

### 1. Controles especializados existiam, mas o Studio os escondia

- `HudButton` e `Kind` já representavam STANDARD/FIRE/AIM.
- `ConfigPanelView` já possuía `buildStandard`, `buildFire` e `buildAim`.
- `MappingService` já conseguia abrir propriedades por ID, mas o caminho `ACTION_CONFIG_STANDARD_BUTTON` filtrava `Kind.STANDARD`.
- `MappingEditorScreen` filtrava a prévia e movimentação a STANDARD; a tabela escrevia `LEGADO` e o painel de propriedades negava edição dos outros tipos.

**Correção:** a prévia usa a lista persistida inteira; o serviço cria qualquer um dos três tipos; configuração por ID funciona para qualquer tipo; tabela/propriedades apresentam tipo e parâmetros específicos; os marcadores Tiro/Mira recebem identificadores próprios. Isso conecta UI → serviço → ConfigPanelView, em vez de manter funcionalidades órfãs.

### 2. Áreas do perfil não apareciam na prévia

Os valores `joyX/Y/R` e `lookX/Y/R` já eram persistidos e usados no HUD real, mas não eram desenhados no canvas do Studio.

**Correção:** desenhar zonas WASD e FPS View na prévia, com legenda. São referências visuais; o raio/posição funcional continuam configurados pelas opções do perfil para não simular um gesto de arraste inexistente.

### 3. Criação e duplicação podiam empilhar controles especializados

A política `HudPlacementPolicy` evitava colisões para controles padrão, enquanto Tiro/Mira eram criados ou duplicados diretamente em posições preferidas, normalmente centro ou deslocamento fixo.

**Correção:** todos os três tipos usam a mesma procura determinística de posição livre, considerando controles existentes e joystick. Se o canvas estiver lotado, a política ainda escolhe a posição menos sobreposta dentro da área.

### 4. Limite de sensibilidade divergente

Os campos de sensibilidade global aceitavam digitação até 1000×, mas `AppSettings.fromJson` e `AppProfile.fromJson` limitavam o valor persistido a 10×. Isso permitia uma entrada visual incompatível com o dado efetivo.

**Correção:** os dois campos da UI agora usam 0,01×–10×, alinhados ao contrato de persistência.

### 5. Arraste visual podia não persistir e seleção podia se perder

A prévia aceitava o gesto mesmo quando `moveControl()` descartava a gravação porque serviço, overlay ou perfil não estavam prontos. Além disso, a seleção era reinicializada a cada alteração de coordenadas, já que o estado dependia da lista inteira de controles.

**Correção:** o gesto só inicia quando serviço, permissão de sobreposição, Shizuku e perfil ativo estão confirmados; `canEditProfile` também governa os botões de configuração/criação. A seleção é mantida enquanto o ID existir e só é corrigida quando o controle sai do perfil. Assim a prévia não sugere uma edição que será descartada.

### 6. Texto de perfil por aplicativo era falso

A aba de aplicativos afirmava que os aplicativos selecionados compartilhavam a configuração global. Na prática `MappingService.settingsForPackage()` aplica `base.profiles[packageName]` e `persist()` grava um `AppProfile` para o pacote ativo.

**Correção:** texto atualizado e indicação de perfil personalizado/fallback global por app.

## Robustez preservada

- Limite de 128 controles por perfil e reserva de IDs não duplicados.
- O novo serviço valida `kind` contra STANDARD/FIRE/AIM e mantém a action antiga para compatibilidade.
- A configuração só é aberta quando o overlay está pronto e o serviço está ativo.
- O perfil ativo é persistido pelo serviço; alterações da prévia usam o ViewModel/DataStore, cujo fluxo é observado pelo serviço.
- O posicionamento usa a área de jogo atual e considera dimensões inválidas como fallback do algoritmo.
- A correção Kadb/Jetifier 0.14.56 permanece: Android AAR Kadb 2.1.4, mDNS 2.1.3, BC 1.86 e ignorelist Jetifier restrita a `bc.*-jdk18on`.

## Limites que permanecem abertos

1. **Build real:** a sessão não conseguiu baixar Gradle 8.8 por falha DNS de `services.gradle.org`; por isso não foi possível executar `:app:lintDebug`, `:app:testDebugUnitTest` ou `:app:assembleDebug` aqui.
2. **Teste em aparelho:** não houve teste físico no Galaxy Note 20 Ultra/Android 13/One UI/DeX nesta sessão. Não afirmar que o HUD ou captura funcionaram em runtime por causa de testes host.
3. **uinput:** manter como possibilidade experimental. A presença de `/dev/uinput` não prova permissão para criar device, compatibilidade do protocolo multitouch, nem roteamento para display externo. Requer probe de capabilities, teste independente e fallback comprovado antes de promoção.
4. **Android overlays:** a partir do Android 12 há restrições a overlays não confiáveis e tap-through; o cálculo agregado de opacidade e flags de janela precisam continuar sob teste em cada versão-alvo.
5. **Captura nativa:** a referência KeyMouse Pro usa `epoll/eventfd/inotify`; o KeyMapper tem monitoramento e recuperação próprios. Migrar o loop inteiro sem profiling não é melhoria garantida. Medir perda de eventos, hot-plug, latência e recuperação antes de trocar `poll`/pipe.
6. **Sistemas e jogos:** compatibilidade não pode ser declarada universal; depende do firmware, API de input, política do sistema e comportamento de cada aplicativo.

## Recomendação de evolução

- Prioridade 1: obter um ciclo CI verde para 0.14.57 e instalar/testar em Android 13/DeX.
- Prioridade 2: telemetria explícita do pipeline (eventos recebidos/descartados, dispositivos capturados, estado grab, toques DOWN/MOVE/UP, resets e último erro), com exportação sanitizada.
- Prioridade 3: matriz de teste de hot-plug, foco, pausa, troca de perfil, rotação, monitor externo e perda de Binder.
- Prioridade 4: biblioteca de componentes independentes (Tap, Hold, Swipe temporizado, Mouse Scroll, joystick, FPS View, Free Look e Macro), com definição de contrato, cancelamento e `releaseAll` antes de introduzir uma DSL de automação.
- Prioridade 5: avaliar `epoll/eventfd/inotify` e backend `uinput` separadamente, comparando medidas reais com a implementação atual.

## Linguagens por responsabilidade

A arquitetura atual já usa uma combinação adequada: Kotlin/Compose para UI e serviços Android, C++17/NDK para captura de baixo nível/JNI e scripts Python/Bash para CI e auditoria. Adicionar JavaScript/TypeScript, Go ou Rust só por haver “liberdade de linguagem” aumentaria as fronteiras de build e manutenção. Rust pode ser avaliado para um módulo nativo isolado se houver caso de segurança de memória e ganho mensurável; não é requisito para resolver os problemas atuais.

## Resultado de validação

- PASS: verificação de coordenadas Kadb e proteção Jetifier.
- PASS: testes negativos do seletor de dependências e do toolchain.
- PASS: contrato de criação/visibilidade/configuração dos 3 tipos.
- PASS: contrato de posicionamento sem colisão para criação/duplicação.
- PASS: testes host evdev/JNI, sintaxe C++17 e contrato CMake/JNI.
- PASS: fronteira Wireless ADB, isolamento Shizuku e posicionamento de overlay.
- PENDENTE: Gradle Lint, JUnit Gradle, APK Debug e teste físico.

## Fontes de pesquisa externa

- SS KeyMouse Pro — página oficial e recursos declarados: https://keymouse.com.br/index.html
- Android 12 — mudanças de comportamento e bloqueio de toques não confiáveis em overlays: https://developer.android.com/about/versions/12/behavior-changes-all
- Android `WindowManager.LayoutParams` — regras de `FLAG_NOT_TOUCHABLE`, `TYPE_APPLICATION_OVERLAY` e opacidade combinada: https://developer.android.com/reference/android/view/WindowManager.LayoutParams
