# KeyMapper 0.14.47 — projeto Android

## Mudanças da versão 0.14.47 — correção do Kotlin compilerOptions / GitHub Actions

- Corrigido o erro fatal do workflow em `app/build.gradle.kts:46`: removido o bloco legado `android.kotlinOptions { jvmTarget = "17" }`, que o Kotlin Gradle Plugin 2.4.21 já rejeita como erro.
- Migrada a configuração para a DSL tipada oficial `kotlin.compilerOptions`, usando `JvmTarget.fromTarget("17")`.
- Mantidos `sourceCompatibility` e `targetCompatibility` em Java 17; os bytecodes Java e Kotlin continuam alinhados.
- Atualizado o artefato do GitHub Actions para `keymapper-0.14.47-debug`.
- Nenhuma dependência, versão do AGP ou versão do Gradle Wrapper foi alterada nesta correção. O aviso sobre Gradle 8.8 indica compatibilidade futura com Kotlin 2.5.0; não é a causa do erro fatal desta execução.

**Validação desta etapa:** testes host C++, verificação de sintaxe dos fontes nativos, contrato CMake/JNI, ausência de `kotlinOptions` antigo, coerência do alvo JVM 17, YAML e metadados de versão/artefato aprovados. O Gradle oficial foi tentado, mas não pôde baixar a distribuição 8.8 porque `services.gradle.org` não resolve neste ambiente. Portanto, não foi possível confirmar Lint/compile/assemble nem gerar APK nesta sessão.

## Mudanças da versão 0.14.46 — ciclo de vida e reconexão do UserService Shizuku

- O `ShizukuInputCaptureBridge` passa a registrar separadamente os listeners de Binder recebido, Binder morto e resultado de permissão. Uma falha isolada de registro não bloqueia tentativas posteriores dos outros listeners nem marca todo o conjunto como registrado.
- `onBindingDied` e `onNullBinding` invalidam o vínculo antigo, limpam o timeout e iniciam uma sequência limitada de rebind. `onServiceDisconnected` preserva a conexão para permitir a reconexão automática definida pelo Android.
- Política de retentativa com backoff de 1, 2, 5, 10 e 30 segundos, limitada a cinco tentativas automáticas por rodada. O orçamento só é zerado depois que `startInputCapture()` confirma sucesso; conexão Binder isolada não renova as tentativas.
- Uma nova solicitação de captura ativa pode iniciar outra rodada limitada quando Shizuku está disponível, autorizado e com UserService suportado. Não cria vínculo duplicado, não inicia a captura quando ela está sendo desativada e respeita tentativas já agendadas.
- O estado de esgotamento agora informa que está aguardando nova solicitação de captura; uma solicitação ativa pode iniciar nova rodada sem reiniciar o serviço.
- O encerramento cancela retentativas e remove os listeners que foram efetivamente registrados.
- Adicionados testes para backoff, limite de tentativas e condições da retomada explícita.
- Atualizado para `versionName 0.14.46` / `versionCode 62`; artefato CI `keymapper-0.14.46-debug`.

**Limite desta etapa:** reconexão foi validada por testes de política e verificação de tipos contra stubs de contrato. Ainda é necessário executar `testDebugUnitTest`, Lint, `assembleDebug` e testes no dispositivo Android com Shizuku reiniciado/desconectado. Esta etapa não altera recursos de mira/disparo nem algoritmos de movimento de jogo.

## Base histórica: KeyMapper 0.14.45

- Padronizado o contrato da biblioteca nativa entre CMake, JNI, serviço Shizuku e diagnóstico (`libkeymapper_evdev.so`).
- Corrigido o diagnóstico que confundia ausência de consulta com backend comprovadamente não inicializado.
- Adicionado teste local do contrato entre o alvo CMake, `System.loadLibrary` e o arquivo empacotado.
- Incremento de versão para `0.14.45` / `61`.

## Base histórica: KeyMapper 0.14.44

Aplicativo Android de mapeamento de teclado/mouse, com HUD, perfis por aplicativo, suporte a displays externos/Samsung DeX, captura física evdev por serviço privilegiado Shizuku e duas rotas independentes de saída touchscreen: uinput opcional no display principal e InputManager como fallback/rota DeX.

## Mudanças da versão 0.14.44 — Radius e Auto Release por controle Sight/Aim

- Adicionado `aimBoundaryRadiusPct` em cada `HudButton` do tipo Aim. Um valor maior que zero usa uma fronteira circular em torno do ponto de recentralização; `0%` preserva o comportamento retangular anterior.
- `LookReanchorPlanner` agora calcula a interseção do vetor de movimento com a circunferência por solução quadrática. O excedente permanece como delta relativo e continua em batches limitados; o raio é reduzido automaticamente para nunca colocar o contato fora dos limites seguros.
- O raio e a origem do controle utilizados durante um gesto são congelados no início e limpos na liberação. Alterar posição/raio durante o gesto não reposiciona o contato nem invalida a geometria atual.
- Adicionado `aimAutoReleaseMs` por controle Sight/Aim: `-1` herda o global, `0` mantém o contato e valores positivos liberam após inatividade. Campos ausentes em perfis antigos usam os padrões legados.
- A interface informa que o raio circular é usado em AUTO/Nonstop; Boundary (OFF) continua limitado e INFINITE depende de mouse relativo nativo aceito pelo destino.
- A inspiração comportamental vem do `Radius`/`Auto Release` do K2er e do Continuous Camera Movement/subpixel do Panda. O pacote GG Mouse Pro registra uma opção de prevenção de congelamento da visão no Free Fire, mas sua implementação interna não está documentada; não foi inventado um algoritmo específico nem transplantado código fechado.
- Comparação de comportamentos e limites de cada referência registrada em `docs/REFERENCIAS-MAPEADORES-V0.14.44.md`.
- Incremento de versão para `0.14.44` / `60`.

**Validação da etapa:** 29 testes Kotlin puros focados em `LookReanchorPlanner` e `LookBoundaryPolicy`; testes host C++ de recuperação JNI; verificação sintática dos dois fontes C++; validação do YAML e da consistência de versão/artefato. O Gradle Android ainda depende de download do Wrapper e do Android SDK, indisponível neste ambiente. Não foi gerado APK.

## Mudanças da versão 0.14.43 — falhas do callback JNI não são mais ocultadas

- Corrigido um segundo ponto de perda: `KeyMapperShellService.onNativeInputBatch()` capturava `RemoteException` e outras exceções, mas não as propagava. Para o C++ isso parecia uma entrega bem-sucedida, mesmo quando o callback Binder falhava.
- Adicionado `NativeBatchCallbackForwarder`, que encaminha o lote, executa o tratamento diagnóstico e sempre relança a exceção original. Uma falha do próprio diagnóstico não substitui a exceção de entrega.
- Callback ausente agora é tratado como falha explícita, em vez de retornar silenciosamente como se o lote tivesse sido aceito.
- Em `RemoteException`, o serviço pausa a captura e propaga a falha para `CallVoidMethod`; `EvdevBatchRecovery` conserva os dispositivos afetados e prepara os marcadores `SYN_DROPPED` para uma nova entrega.
- Adicionados três testes Kotlin/JVM para entrega normal, propagação da mesma exceção e preservação da exceção original quando o diagnóstico falha.
- Incremento de versão para `0.14.43` / `59`.

**Validação:** 81 testes Kotlin/JVM de regressão + 3 testes do encaminhador de callback; testes host C++ de recuperação; verificação sintática de `evdev_capture.cpp` e `uinput_touch.cpp`; consistência YAML/versão/artefato. O build Gradle oficial continua bloqueado porque `services.gradle.org` não resolve e o Android SDK não está configurado neste ambiente. Não foi gerado APK.

## Histórico da versão 0.14.42 — recuperação no transporte JNI/NDK

- `sendBatch()` verifica separadamente alocação do `jlongArray`, cópia `SetLongArrayRegion` e exceção em callback Java.
- Falhas que chegam à camada JNI são registradas por `EvdevBatchRecovery`; marcadores `SYN_DROPPED` por dispositivo são prefixados ao próximo lote.
- O worker tenta reenviar marcadores pendentes mesmo sem movimento novo, em intervalo limitado.
- Adicionados testes host C++ e verificação sintática dos arquivos nativos ao GitHub Actions.


## Histórico da versão 0.14.40 — vetores evdev sincronizados

- Corrigida uma diferença entre os backends de captura: o Android `MotionEvent` já entregava X/Y como uma amostra, enquanto evdev encaminhava `REL_X` e `REL_Y` separadamente. Isso podia aplicar escalas temporais diferentes aos eixos de um mesmo relatório físico.
- O leitor JNI agora preserva `EV_SYN/SYN_REPORT` para dispositivos mouse. `MappingService` acumula `REL_X/Y` por `deviceId` e entrega um único vetor X/Y ao motor por relatório evdev.
- A agregação permanece correta quando um relatório é dividido entre lotes JNI. Um `EV_KEY` ou scroll fecha o vetor parcial antes de encaminhar a transição, preservando a ordem de entrada; `SYN_DROPPED`, remoção e reset global descartam somente o estado pendente adequado.
- Criado `NativeMouseReportAggregator` com limites numéricos, isolamento por dispositivo e testes de sincronização, relatórios fragmentados, transições de controle, remoção e limpeza.
- Incremento de versão para `0.14.40` / `56`.

**Validação adicional:** 56 testes Kotlin/JVM isolados passaram (48 anteriores + 8 novos). O arquivo `evdev_capture.cpp` passou por `g++ -std=c++17 -fsyntax-only` com os headers JNI do JDK e os headers Linux disponíveis. Isso não substitui a compilação NDK/Gradle nem teste no aparelho.

## Histórico da versão 0.14.39 — Continuous Camera Movement

- Adicionada a estratégia **Nonstop** para o perfil global e cada controle Sight/Aim: no limite, envia `ACTION_POINTER_DOWN` no novo ponto antes de `ACTION_POINTER_UP` no contato antigo. O conjunto de contatos nunca fica vazio durante o handover; o destino recebe temporariamente dois ponteiros, então a compatibilidade precisa ser testada por jogo.
- O resíduo subpixel interno do `SensitivityEngine` passa de `Float` para `Double`. Isso evita perda de um pixel por truncamento em trajetórias de baixa sensibilidade e reversão de direção; a API de eventos continua recebendo deltas `Float`.
- A enumeração `Rot` foi isolada em `data/Rot.kt`, permitindo testar seleção de estratégias sem carregar classes Android/JSON.
- Mantido o modo Automático, baseado em `ACTION_UP`/`ACTION_DOWN`, e o modo de mouse relativo nativo sem contato touchscreen. Os três comportamentos agora são distintos; a opção duplicada `CENTER` foi removida da interface sem apagar valores antigos salvos.
- `TouchInjector.swap()` agora retorna sucesso/falha real, valida os IDs de contato e faz rollback de melhor esforço quando não consegue liberar o contato antigo. `MappingEngine` não muda o ID ativo após uma troca recusada e cancela deltas antigos para evitar movimentos residuais.
- O início do gesto de câmera só marca a visão ativa depois de confirmar que o `TouchInjector` realmente criou o contato.
- Global e propriedades de cada Sight/Aim expõem **Movimento contínuo da câmera** e estratégia de recentralização. Nonstop é descrito como handover multitouch, não confundido com mouse relativo nativo. O painel flutuante também usa o rótulo explícito da funcionalidade, e o painel completo mantém a escolha de estratégia.
- A acumulação subpixel existente recebe testes adicionais com deltas de 0,35 px, sensibilidade baixa, valores negativos e mudanças de direção.
- Incremento de versão para `0.14.39` / `55`.


- A arquitetura foi comparada estaticamente com o pacote SS KeyMouse Pro 1.0.76 fornecido pelo usuário. A referência confirmou a separação entre captura física evdev (epoll/eventfd/inotify) e saída de toque por um backend virtual. O KeyMapper mantém implementação própria; nenhum binário ou código proprietário do KeyMouse Pro foi incorporado.
- O cursor do HUD deixou de aplicar `coerceIn(0, largura/altura)` aos deltas do mouse. A posição lógica acumulada é separada da posição visual: a primeira ultrapassa as dimensões do monitor; o retículo visível reancora dentro da tela e preserva o excedente, em cálculo O(1) e sem alocação no caminho de alta frequência.
- O motor da visão em modo `AUTO`/`CENTER` agora limita cada lote a 64 reancoragens para não monopolizar a thread de toque. Quando um movimento exige mais, o planejador devolve o delta restante e o motor o drena em continuações ordenadas de 1 ms, antes de aceitar movimento mais recente. O limite de trabalho não descarta movimento acumulado.
- Corrigido o caso de `deadzone=0`: um buffer vazio não pode agendar flushes repetidos sem movimento.
- A tela de configurações explica que `OFF` é o modo deliberadamente limitado; `AUTO` permanece como modo recomendado. `INFINITE` continua dependente de o aplicativo de destino aceitar `SOURCE_MOUSE_RELATIVE`.
- Adicionados testes puros para o cursor lógico sem limite de tela, retículo com reancoragem, chegada exata às bordas e preservação de deltas grandes em lotes sucessivos.
- Incremento de versão para `0.14.38` / `54`.

**Interpretação técnica de “além da tela”:** Android não pode desenhar ou injetar um contato touchscreen fora do viewport físico. A implementação correta mantém a coordenada lógica relativa sem limite de tela e reancora o contato/retículo dentro da superfície, preservando o movimento excedente. Em `AUTO`, a visão não deve ficar travada na borda; podem ocorrer transições `UP/DOWN` durante a reancoragem, e alguns jogos podem mostrá-las.

**Limites da 0.14.38:** os testes puros da versão anterior passaram. O build Gradle completo, Android Lint, APK, instalação e teste físico no Note 20 Ultra/Android 13/DeX ainda não foram executados neste ambiente. Os testes não comprovam o consumo dos eventos pelo jogo.

## Mudanças da versão 0.14.37

- Corrigida a agregação de teclas e botões do backend evdev por `deviceId`. Dois teclados podem manter a mesma tecla pressionada sem que o `KEY_UP` de um deles solte a entrada do outro; a mesma regra vale para botões compartilhados entre mouses.
- Na desconexão ou em `SYN_DROPPED`, o serviço limpa somente o dispositivo afetado. As teclas/botões globais são liberados apenas quando nenhum outro dispositivo ainda os mantém pressionados; o restante do mapeamento e os periféricos conectados continuam ativos.
- A captura nativa agora usa uma instância própria de `PhysicalInputState`, separada da agregação dos eventos `MotionEvent`. Transições duplicadas do mesmo dispositivo e autorepeat evdev não geram novos DOWN/UP. Códigos Linux físicos distintos que viram o mesmo botão Android (como `BTN_SIDE`/`BTN_BACK`) também são rastreados separadamente.
- Pausa, edição, perda de foco/captura, troca de display, falha do Shizuku e encerramento limpam o bookkeeping nativo junto com o `MappingEngine.releaseAll()`. A transição do leitor privilegiado de ativo para inativo também limpa o estado se o Binder/worker cair sem informar cada dispositivo individualmente.
- Adicionados testes para teclas compartilhadas entre teclados, botões compartilhados entre mouses, aliases de botões Linux, desconexão seletiva, remoção do último dispositivo e máscaras inválidas. Os 14 métodos do teste de estado físico passaram em harness Kotlin isolado; a tarefa Gradle completa ainda depende do runner Android.
- Incremento de versão para `0.14.37` / `53`.

**Limites:** o build Android completo, a execução via GitHub Actions e o teste físico no Samsung Note 20 Ultra/Android 13/DeX ainda não foram realizados nesta sessão. O wrapper Gradle não consegue baixar Gradle 8.8 porque `services.gradle.org` não resolve no ambiente de execução. Os testes Kotlin isolados confirmam a lógica de agregação, não a integração instalada.

## Mudanças da versão 0.14.36

- Corrigido o roteamento: uinput só é elegível quando o display principal tem geometria consultada e válida. Se a leitura falhar, o transporte escolhe o InputManager antes de iniciar o gesto.
- Corrigida a normalização: coordenadas finitas são limitadas à tela e convertidas para 0..65535; NaN, Infinity e dimensões inválidas são rejeitados em vez de serem enviados ao canto superior esquerdo.
- Reduzido o limite de espera em backpressure do uinput de 100 ms para 2 ms. Caso o kernel não aceite a escrita prontamente, o quadro falha fechado e o serviço segue o mecanismo de recuperação, sem bloquear longamente o thread de entrada.
- Incluídos testes da política de geometria para 1600×900, cantos, centro, clamp, dimensões inválidas e valores não finitos.
- Mantido o desenho da 0.14.35: captura evdev, saída uinput opcional no display principal e InputManager como fallback e rota para DeX/monitor externo.
- Incremento de versão para `0.14.36` / `52`.

**Limites:** a autorização real de `/dev/uinput`, a classificação pelo Android InputReader, o viewport associado, o consumo de eventos pelos jogos e a estabilidade no Samsung Android 13/One UI/DeX ainda precisam ser confirmados em aparelho real. O ambiente desta sessão não tem Android SDK nem consegue baixar Gradle 8.8; portanto, não há APK gerado nem confirmação de build Android.

## Histórico da versão 0.14.33

- Interface desktop com sidebar estilo ferramenta de PC; celular mantém navegação inferior.
- Workspace de edição no estilo emulador: estado do perfil, ações, prévia 16:9, tabela de associações e painel de propriedades. Em janelas menores, o workspace empilha os painéis.
- A ação “Adicionar tecla” cria uma associação padrão sem tecla vinculada, aloca ID único, persiste no perfil e abre o painel de propriedades já existente.
- A prévia permite arrastar e salvar controles padrão; o HUD ao vivo continua disponível para ajustes finos. Controles especializados legados permanecem somente leitura neste workspace.
- A abertura do app de destino pelo Painel e pelo Editor agora respeita a preferência de display externo/DeX salva pelo utilizador.
- A classificação evdev de teclado aceita layouts compactos sem exigir simultaneamente A, Espaço e Enter.
- Se o kernel negar `EVIOCSCLOCKID`, os timestamps `CLOCK_REALTIME` são convertidos para monotônico por offset, preservando intervalos de evento.
- Kotlin / Compose plugin atualizados para 2.4.21; versão `0.14.33` / `49`.

**Importante:** esta versão histórica 0.14.33 não foi compilada como APK nem testada no Note 20 Ultra. O backend touchscreen virtual só foi adicionado na versão 0.14.35.

## Mudanças da versão 0.14.32

- O serviço Shizuku mantém um leitor evdev nativo via JNI durante a sessão de mapeamento. O backend abre `/dev/input/event*`, classifica dispositivos com capacidades de mouse/teclado e solicita `EVIOCGRAB`; a disponibilidade só é anunciada quando o grab foi realmente aceito.
- A descoberta de periféricos conectados usa `inotify` em `/dev/input`, com conferência a cada 5 segundos quando o watcher funciona e varredura a cada 1 segundo quando `inotify` não está disponível; EOF, desconexão ou falha de registro no epoll fecha o descritor e solicita nova descoberta.
- O leitor usa `epoll` + `eventfd` para aguardar eventos e comandos, e `inotify` para hot-plug; os lotes JNI continuam sendo entregues ao motor atual sem alterar a transformação de movimento ou os modos de mapeamento.
- O serviço verifica se o worker JNI permanece ativo e tenta reiniciá-lo pelo mesmo UserService quando o leitor termina inesperadamente.
- Ao pausar, encerrar ou detectar perda do cliente, o leitor desativa os grabs. Se `EVIOCGRAB` não puder ser liberado explicitamente, fecha o descritor para que o kernel libere a captura.
- O vínculo Shizuku reutiliza o Binder existente em vez de criar vínculos duplicados quando a inicialização do leitor falha; timeout, Binder nulo e vínculos mortos limpam o estado para permitir nova tentativa, e comandos de pausa/retomada pendentes são serializados com o start.
- Rodas de mouse hi-res são encaminhadas em frações de detente sem duplicar os pulsos legados anunciados pelo mesmo dispositivo.
- O heartbeat do leitor mostra contadores de eventos lidos/enfileirados, perdas de sincronização, erros de leitura e dispositivos removidos; isso ajuda a separar falhas de captura das falhas de motor/injeção.
- Incremento para `0.14.32` / `48`.

**Limites históricos da 0.14.32:** aquela versão ainda não criava um dispositivo touchscreen virtual via `/dev/uinput`; a injeção era feita por `InputManager` via Shizuku. `EVIOCGRAB` e leitura evdev dependem das permissões reais da ROM/SELinux.

## Histórico da versão 0.14.29

- GitHub Actions agora executa Android Lint antes dos testes e da geração do APK; falhas de análise estática bloqueiam a publicação.
- Teste de fronteira confirma a expiração do snapshot ADB aos 15 minutos exatos.

## Histórico incorporado da versão 0.14.28

- `DisplayManager` ausente/indisponível não derruba a construção do helper; enumeração, registro e remoção de listener são tolerantes a falhas do serviço de display.
- A leitura do hardware na tela de configurações agora degrada para campos “Indisponível” quando DeX/DisplayManager falha, em vez de lançar erro durante a composição.
- A proporção de tela usa fallback válido quando largura ou altura ainda não estão disponíveis.

- Corrigido o requisito mínimo do pareamento ADB para Android 11 (API 30).
- Impedido que a resolução de outro aparelho da rede seja usada como fallback da tela local.
- Invalidados snapshots ADB antigos após nova tentativa ou falha de consulta.
- Corrigidos fallbacks de parsing quando `Override size` ou `Override density` retorna valor inválido.
- Evitadas operações simultâneas e mudanças de endpoint durante ações ADB em andamento.
- Cancelamento de descoberta/consulta ADB preserva a semântica de CancellationException.
- A tela de calibração impede duas capturas concorrentes, valida as métricas do display e sempre libera Pointer Capture ao concluir/cancelar ou sair da tela.
- DisplayHelper tolera remoção/disconnect de monitor durante listagem de modos/taxas; métricas externas usam o DisplayContext correto e métricas inexistentes suspendem a saída em vez de inventar uma geometria.
- Removida uma transição duplicada de estado após aquisição de Pointer Capture.
- Removidos os dois pacotes Free Fire fixos do manifesto.

### ADB sem fio independente do Shizuku

- Cliente ADB TLS incorporado por meio da biblioteca Kadb 2.1.5; o aplicativo não depende de um executável `adb` instalado no próprio telefone.
- Pareamento direto com IP, porta de pareamento e código de seis dígitos, usando o protocolo TLS do Android Wireless Debugging.
- Descoberta mDNS dos serviços `_adb-tls-pairing._tcp` e `_adb-tls-connect._tcp`; os endpoints de pareamento e conexão são tratados separadamente.
- Conexão autenticada independente do Shizuku, com consulta de identidade remota (`id`), fabricante, modelo, API, resolução e densidade.
- Lista fechada de seis comandos de diagnóstico somente leitura. A interface não recebe comandos shell livres.
- Chave privada do host ADB persistida em `noBackupFilesDir`, criptografada por AES-GCM com chave não exportável do Android Keystore. O código de pareamento nunca é persistido.
- IP e portas são lembrados localmente; o usuário pode remover a identidade ADB salva pela própria interface.

### Uso real dos dados ADB

- O snapshot ADB inclui UID remoto, resolução `wm size`, densidade `wm density` e se o IP conectado pertence a uma interface de rede deste telefone.
- A geometria ADB é consumida por `DisplayHelper` como fallback temporário (até 15 minutos), apenas para a tela principal, somente quando as métricas nativas estão ausentes/inválidas e o endereço conectado pertence a este telefone. Conexões a outro aparelho na rede nunca contaminam as métricas locais.
- Métricas nativas válidas sempre prevalecem. Displays externos/DeX nunca recebem dimensões de `wm size`, pois esse comando não descreve de forma confiável cada display externo.

### Ferramentas de build

- Kotlin Gradle Plugin e Compose Compiler Plugin: 2.4.21, para consumir a versão utilizada pelo artefato Kadb.
- Mantidos AGP 8.5.2, Gradle Wrapper 8.8, JDK 17 e compile/target SDK 34. A matriz oficial do KGP 2.4.21 inclui Gradle 7.6.3–9.7.0 e AGP 8.5.2–9.3.1.
- Coroutines Android atualizada para 1.11.0; HiddenApiBypass para 6.1, alinhando dependências usadas pela biblioteca ADB.

## Modos e limites atuais

| Método | Estado nesta versão | Responsabilidade |
|---|---|---|
| Shizuku | Implementado | Autoriza o UserService privilegiado; InputManager exige `INJECT_EVENTS`, enquanto uinput usa acesso próprio do processo privilegiado. |
| Captura evdev/JNI | Implementada, sujeita a permissões do firmware | Lê mouse/teclado externos em `/dev/input`, tenta `EVIOCGRAB`, trata hot-plug e informa a capacidade realmente confirmada. |
| Pointer Capture | Fallback | Captura pela janela focada quando evdev não cobre uma classe de dispositivo ou o serviço nativo não pode ser iniciado. Não é captura global. |
| ADB sem fio direto (Kadb) | Implementado para pareamento, conexão TLS e diagnóstico | Sessão shell independente do Shizuku para consultas e métricas; não substitui a injeção nem concede automaticamente acesso evdev. |
| Root independente | Não implementado como backend separado | Uma sessão Shizuku que já opere como UID 0 pode permitir outras capacidades, mas o KeyMapper não assume nem solicita root automaticamente. |
| `/dev/uinput` | Implementado como transporte opcional na 0.14.36 | Depende do nó, kernel, permissões/SELinux e classificação InputReader; somente display principal. |
| Visão `AUTO`/`CENTER` | Reancoragem UP/DOWN com preservação do excedente | Caminho compatível; a transição pode causar pausa perceptível em alguns jogos. |
| Visão `NONSTOP` | Novo `POINTER_DOWN` antes do `POINTER_UP` antigo | Evita intervalo sem contato, mas o destino vê dois ponteiros por um instante; alguns jogos interpretam como multitouch ou não suportam. Se não houver slot, degrada para UP/DOWN. |
| Visão `INFINITE` | `SOURCE_MOUSE_RELATIVE` | Não envia toque nem depende de borda; só funciona se o destino e o transporte aceitarem essa origem. |
| Cursor visual do HUD | Reancoragem dentro do display | A coordenada lógica não tem limite de tela; o retículo desenhado volta ao ponto de referência com o excedente preservado. |

**Importante:** a captura física, a saída touchscreen uinput e a saída InputManager são caminhos separados. A 0.14.36 pode usar uinput para toques no display principal mesmo sem `INJECT_EVENTS`, desde que o UserService consiga abrir/criar o dispositivo; o InputManager é mantido como fallback e para displays externos/DeX, e o modo explícito `SOURCE_MOUSE_RELATIVE` ainda depende de `INJECT_EVENTS`. A abertura de `/dev/input`, `EVIOCGRAB` e uinput dependem da ROM/SELinux; UID shell 2000 não equivale a root. Aceitação no transporte/kernel não prova que o aplicativo de destino consumiu o evento. A leitura de `wm size`/`wm density` não altera resolução/densidade.

## Segurança e escopo do diagnóstico

Os comandos permitidos são fixos: `id`, propriedades básicas do dispositivo, `wm size` e `wm density`. Não existe campo para executar texto shell arbitrário. O serviço privilegiado lê fluxos dos nós evdev compatíveis e pode usar `EVIOCGRAB` durante uma sessão explicitamente iniciada; os descritores são liberados ao pausar/parar. Na 0.14.36, o KeyMapper pode criar um dispositivo virtual em `/dev/uinput`; a criação é opcional e não equivale à confirmação de consumo pelo aplicativo de destino.

A autenticação ADB é independente do Shizuku, mas o UID e as permissões remotas dependem da sessão e da versão/firmware Android. Shell UID 2000 não equivale a root UID 0.

## Configuração de compilação

- Android Gradle Plugin: 8.5.2
- Gradle Wrapper: 8.8
- Kotlin / Compose Compiler Plugin: 2.4.21
- JDK: 17
- compileSdk / targetSdk: 34
- minSdk: 26
- applicationId: `com.example.keymapper`
- versionName / versionCode: `0.14.46` / `62`

## Validação

A validação local de políticas puras e arquivos de configuração pode ser executada sem SDK. Nesta revisão, o harness Kotlin/JVM isolado passou 242 testes; os testes C++ host, a análise sintática nativa e o contrato CMake/JNI/diagnóstico também passaram. Isso não substitui Gradle. Build Android, resolução de dependências Maven, testes Gradle completos, geração de APK, instalação e teste físico no Samsung Note 20 Ultra/Android 13/DeX ainda precisam ser executados em um ambiente com rede, JDK 17, Gradle 8.8 e Android SDK 34.

Comando de validação completo:

```bash
./gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --stacktrace --console=plain
```

APK esperado: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

O workflow `.github/workflows/build.yml` executa testes C++ host, análise sintática JNI/C++, verificação do contrato CMake/JNI/diagnóstico, Android Lint, testes unitários e `assembleDebug`; publica o artefato `keymapper-0.14.47-debug` somente quando as etapas anteriores passam.


## Correções da versão 0.14.36

- O roteamento uinput só é selecionado quando a geometria do display principal foi consultada com dimensões válidas (`width > 1` e `height > 1`). Consulta ausente, exceção ou dimensões inválidas deixam o uinput inelegível e preservam o fallback InputManager.
- Coordenadas inválidas ou não finitas são rejeitadas; não são mais convertidas silenciosamente para o ponto (0, 0).
- A escrita uinput não aguarda mais até 100 ms em backpressure. A espera de prontidão é limitada a 2 ms e a falha fecha o transporte para recuperação, evitando congelamento prolongado do thread serial de entrada.
- Foram adicionados testes puros de normalização, limites de tela, geometria inválida e entradas NaN/Inf.
