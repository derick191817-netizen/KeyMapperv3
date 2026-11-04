# Auditoria técnica — KeyMapper 0.14.35

## 1. Base e escopo

- Base selecionada: `KeyMapper-v0.14.33-STUDIO-SOURCE.zip`, cujo SHA-256 (`a892e957d539f0db7bfe46d8f3acfb2531cb03b92c272b59a3b3776aeb54629f`) foi conferido com o arquivo de checksum enviado nesta conversa.
- Referência funcional: SS KeyMouse Pro para o conjunto de capacidades de mapeamento e para a separação entre captura de periféricos e saída touchscreen. Nenhum binário, código-fonte ou biblioteca proprietária foi incorporado.
- Escopo desta atualização: adicionar saída touchscreen virtual opcional baseada em `uinput`, sem reescrever o motor existente, sem modificar parâmetros de mira/disparo e sem remover o caminho `InputManager`.

## 2. Arquitetura implementada

### 2.1 Captura de entrada (mantida)

A captura física existente continua usando o serviço Shizuku, JNI/C++, `epoll`, `eventfd`, `inotify`, enumeração de `/dev/input/event*`, `EVIOCGRAB`, hot-plug e callback AIDL. O backend de captura pode falhar independentemente do backend de saída touchscreen.

### 2.2 Saída touchscreen virtual (nova, opcional)

- `uinput_touch.cpp` cria `KeyMapper Virtual Touch` usando `/dev/uinput` ou `/dev/input/uinput`.
- O dispositivo anuncia `INPUT_PROP_DIRECT`, `EV_ABS`, `EV_KEY`, `BTN_TOUCH`, `BTN_TOOL_FINGER`, coordenadas `ABS_X/Y`, slots Protocol B e tracking IDs.
- O código limita a criação a no máximo 32 slots, enquanto a interface de injeção atual limita a 16 ponteiros simultâneos.
- O transporte processa ações `ACTION_DOWN`, `ACTION_POINTER_DOWN`, `ACTION_MOVE`, `ACTION_POINTER_UP`, `ACTION_UP` e `ACTION_CANCEL`/`releaseAll`. As rotinas de destruição e liberação foram separadas para que uma falha de escrita nunca gere recursão no fechamento.
- A inicialização aguarda brevemente o reconhecimento assíncrono de hot-plug pelo InputReader; essa espera ocorre na inicialização do UserService, não no caminho de movimento por frame.
- O estado nativo é protegido por mutex. Se a escrita de um quadro falhar parcialmente, o dispositivo é destruído para que o kernel encerre os contatos que possam ter chegado ao InputReader, evitando continuar com tabelas de slots divergentes.

### 2.3 Transporte e roteamento

- AIDL estende o contrato existente com métodos explícitos para abrir, enviar quadros, consultar disponibilidade, liberar e fechar o touchscreen virtual. Os IDs de transação existentes 1–5 foram preservados; novas operações usam IDs 6–11.
- `ShizukuInputCaptureBridge` implementa `VirtualTouchOutput`; o estado de captura evdev e o estado do uinput são publicados de forma independente. Se o descritor uinput fechar ou deixar de responder, o bridge agenda uma reabertura serial no worker, com intervalo mínimo de 5 s; não faz Binder/JNI de recuperação na thread de entrada.
- A abertura do dispositivo é idempotente quando já existe um uinput válido com slots suficientes. Uma nova tentativa de iniciar a captura evdev não destrói o touchscreen virtual que possa estar atendendo um gesto.
- `TouchInjector` fixa a escolha do transporte no primeiro DOWN, antes da pré-verificação de permissões, e não alterna entre uinput e InputManager durante um gesto. Se a pré-verificação do InputManager falhar no primeiro DOWN, a escolha é descartada porque nenhum contato foi iniciado.
- A política seleciona uinput somente no display ID 0 e somente quando o backend foi aberto. Display externo ou backend indisponível seleciona `InputManager`.
- A seleção de transporte ocorre antes da verificação de `INJECT_EVENTS`: quando a sequência foi roteada para uinput, `TouchInjector` não exige que `ShizukuInjector` consiga preparar `IInputManager`. O início de `MappingService` passa a aceitar UserService Shizuku disponível mesmo quando `INJECT_EVENTS`/InputManager não está disponível.
- `MappingEngine` não descarta amostras físicas apenas porque o caminho InputManager está indisponível; o destino final decide no transporte. O modo `SOURCE_MOUSE_RELATIVE` continua dependente do InputManager, enquanto os modos touchscreen usam uinput quando elegível.
- A razão técnica para o limite: uinput cria um dispositivo Linux de entrada; os quadros não carregam o `displayId` do Android. Não é seguro presumir que a saída possa ser direcionada a qualquer display. O caminho InputManager existente continua sendo utilizado para DeX/monitor externo.
- Se display muda, fica inválido/bloqueado, o Binder cai ou um quadro é rejeitado, o motor tenta cancelar a sequência virtual inteira. Se o backend continuar disponível mas ainda não confirmar a liberação, novos contatos ficam bloqueados e a operação de liberação é repetida. Uma falha parcial de escrita destrói o dispositivo virtual para o InputReader descartar slots que possam ter sido aceitos parcialmente; o fechamento não recursa na rotina de liberação.
- Os quadros DOWN/POINTER_DOWN, MOVE e UP validam IDs únicos, slots ativos e conjunto completo de ponteiros antes de escrever no kernel, evitando duplicação ou abandono silencioso de contato em um lote inválido.
- Quando a criação ou operação do uinput falha, o app degrada para `InputManager`; o motor de mapeamento existente permanece como dono das associações e estados de controle.

## 3. Arquivos adicionados/alterados

- Novo: `app/src/main/cpp/uinput_touch.cpp`.
- Alterado: `app/src/main/cpp/CMakeLists.txt`.
- Alterado: `app/src/main/aidl/com/example/keymapper/injection/IKeyMapperShellService.aidl`.
- Alterado: `KeyMapperShellService.kt`, `ShizukuInputCaptureBridge.kt`, `TouchInjector.kt`, `MappingService.kt`.
- Novos: `VirtualTouchOutput.kt`, `TouchOutputRoutingPolicy.kt`.
- Novo teste: `TouchOutputRoutingPolicyTest.kt`.
- Alterados: `app/build.gradle.kts`, `.github/workflows/build.yml`, `README.md`.

## 4. Riscos técnicos ainda abertos

1. **Acesso ao uinput:** UID shell/ADB, root, permissões do nó e SELinux são específicos do firmware. Existência do nó ou `canWrite` não prova que `UI_DEV_CREATE` será permitido.
2. **Classificação Android:** o Android InputReader precisa classificar o dispositivo sintético como touchscreen direto e associá-lo ao viewport correto. A implementação marca `INPUT_PROP_DIRECT` e descreve os eixos, mas a decisão final depende da ROM e do kernel Samsung.
3. **Viewport/display:** a API uinput não recebe o `displayId` Android. Por segurança, a implementação usa somente display 0. A saída DeX permanece no InputManager.
4. **Permissão separada:** o modo uinput não exige `INJECT_EVENTS`, mas ainda exige Shizuku/UserService ativo e acesso real a `/dev/uinput`. `SOURCE_MOUSE_RELATIVE` e a rota InputManager continuam exigindo `INJECT_EVENTS` e acesso ao serviço Binder `input`.
5. **Consumo do jogo:** retorno positivo de `write()` significa aceitação pelo dispositivo/driver; não prova que InputReader ou o processo do jogo consumiram o evento.
6. **Latência:** o envio AIDL é síncrono no HandlerThread do injetor. Em operação normal o quadro é pequeno; no caso de backpressure, `poll()` pode esperar até 100 ms. Isso deve ser medido no aparelho antes de ajustar o tempo limite.
7. **Compatibilidade kernel:** exige suporte a `UI_DEV_SETUP` e `UI_ABS_SETUP` (uinput versão 5 ou superior). Kernels/ROMs sem esse suporte usam fallback.
8. **Gestos e foco:** mudança de tela, pausa, perda de Binder e parada do serviço precisam ser submetidas a stress test para validar ausência de toque preso.

## 5. Verificações realizadas nesta sessão

### 5.1 Revisão de compatibilidade do toolchain

- Kotlin Gradle Plugin 2.4.21: a matriz oficial declara suporte a Gradle 7.6.3–9.7.0 e AGP 8.5.2–9.3.1; a combinação de projeto está dentro da faixa publicada.
- Android Gradle Plugin 8.5.x: a documentação oficial exige Gradle 8.7 como mínimo; o Gradle Wrapper do projeto está em 8.8.
- Essa conferência é de compatibilidade declarada, não um build resolvido. Não prova que os artefatos Maven foram baixados nem substitui `lintDebug`, `testDebugUnitTest` e `assembleDebug`.


| Verificação | Resultado | Significado |
|---|---|---|
| SHA-256 da base 0.14.33 comparado ao checksum recebido | Aprovado | A base corresponde ao arquivo de checksum. |
| `evdev_capture.cpp` com Clang C++17/JNI headers | Aprovado | Sintaxe C++ do backend de captura verificada em host. |
| `uinput_touch.cpp` com Clang C++17/JNI headers | Aprovado | Sintaxe C++ do novo backend verificada em host; não substitui NDK/ABI Android. |
| Policy Kotlin compilada e harness executável | Aprovado | Valida seleção em display 0, fallback, display secundário/ID inválido e continuidade de rota. |
| Abertura uinput idempotente e validação do conjunto de slots | Revisão estática aprovada | Evita recriar dispositivo durante gesto e rejeita IDs repetidos/conjuntos incompletos; precisa de stress test no kernel real. |
| YAML do workflow, IDs AIDL únicos e inventário JNI | Aprovado | Estrutura, unicidade dos IDs e nomes das funções nativas conferidos; não substitui AIDL compiler. |
| `TouchOutputRoutingPolicy.kt` + `VirtualTouchOutput.kt` compilados isoladamente com kotlinc | Aprovado | Verifica sintaxe dos contratos Kotlin puros; não compila `TouchInjector`, AIDL ou Android. |
| AIDL compiler / Kotlin Android compile | Não executado | Android Gradle Plugin/SDK não estão instalados no ambiente de edição. |
| Android Lint / testes unitários Gradle | Não executados | Sem Android SDK e distribuição Gradle 8.8 local; o workflow GitHub Actions deverá executar. |
| `assembleDebug` / APK | Não executado | Não há APK validado nesta entrega local. |
| Instalação em Galaxy Note 20 Ultra SM-N986B Android 13 | Não executada | Requer aparelho e permissões Shizuku reais. |
| Teste em jogo / confirmação de toque consumido | Não executado | Não alegar funcionamento ponta a ponta. |
| Teste Samsung DeX externo | Não executado | DeX mantém InputManager por política de roteamento, mas requer validação real. |

## 6. Critérios de aceite para a próxima validação física

1. Executar GitHub Actions e exigir sucesso de `lintDebug`, `testDebugUnitTest` e `assembleDebug`.
2. Instalar no SM-N986B Android 13; conferir `inspectRuntime`, UID, SELinux e status real de `UI_DEV_CREATE`.
3. Em tela principal, testar DOWN/MOVE/UP, POINTER_DOWN/POINTER_UP, CANCEL, dois ou mais contatos, alternância de app e parada do serviço.
4. Forçar perda do Binder durante gesto e verificar que o kernel remove o dispositivo/slots; depois reconectar e confirmar que um novo gesto é aceito.
5. Negar acesso a `/dev/uinput` ou testar ROM incompatível e confirmar fallback InputManager sem crash.
6. No DeX, confirmar que display externo nunca seleciona uinput e que `InputManager.setDisplayId` direciona a tela escolhida corretamente.
7. Medir latência, taxa de quadros, perdas de evento, ANR e CPU; ajustar `poll()` somente com dados medidos.
8. Confirmar visualmente que o jogo de teste recebe o toque. O status de transporte por si só não é prova de consumo.

## 7. Conclusão

A 0.14.35 adiciona uma alternativa nativa de saída touchscreen com `uinput`, mantendo `InputManager` e a captura evdev como subsistemas independentes. O desenho é compatível em conceito com a separação de captura e toque observada no modelo funcional KeyMouse Pro, mas não é cópia de implementação. O backend está pronto para validação de integração; **não há evidência suficiente para declarar o APK compilado, o uinput autorizado no Samsung, a classificação do InputReader correta ou o KeyMapper jogável nesta fase**.
