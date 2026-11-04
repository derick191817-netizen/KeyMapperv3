# Auditoria KeyMapper 0.14.46

## Escopo

Continuidade da versão 0.14.45 com foco no ciclo de vida do serviço privilegiado Shizuku, sem alteração de motores de jogo.

## Problema identificado

`ShizukuInputCaptureBridge` utilizava um único booleano para representar o registro de três listeners diferentes. O booleano era marcado como `true` antes de cada chamada de registro. Se a primeira ou outra chamada falhasse, as chamadas posteriores de `start()` não tentariam registrar novamente os listeners faltantes. Isso podia deixar a reconexão e a observação de permissão parcialmente inoperantes.

Também havia um limite operacional: após cinco falhas transitórias de vínculo, uma nova solicitação ativa de captura não reiniciava a rodada mesmo quando Binder e permissão já estivessem disponíveis.

## Correções

1. Três estados independentes de registro: `binderReceivedListenerRegistered`, `binderDeadListenerRegistered` e `permissionResultListenerRegistered`.
2. Cada listener só é marcado registrado quando sua chamada `add...Listener` retorna normalmente.
3. `unregisterLifecycleListeners()` remove somente listeners efetivamente registrados e limpa cada estado separadamente.
4. `setEnabled(true)` pode reiniciar uma rodada limitada quando não há remote, binding, conexão nem retry pendente, e quando Binder, permissão e versão são válidos.
5. `setEnabled(false)` não tenta iniciar um serviço remoto ausente.
6. A política de elegibilidade foi isolada em `UserServiceReconnectPolicy.shouldAttemptExplicitRetry()` e coberta por testes.
7. O orçamento de retentativas não é zerado em `onServiceConnected`; ele só é zerado quando `startInputCapture()` retorna sucesso. Uma sequência de Binder conectado + captura falhando não pode reiniciar o backoff indefinidamente.
8. Backoff permanece limitado a cinco tentativas por rodada.

## Compatibilidade documental

A API oficial do Shizuku informa que o listener sticky pode ser chamado imediatamente se o Binder já estiver disponível; os listeners são chamados na thread principal por padrão e podem disparar novamente quando o servidor reinicia. A documentação também recomenda acompanhar os eventos de Binder recebido/morto e registrar/remover o listener de resultado de permissão. Isso fundamenta a separação do registro e o ciclo de vida nesta versão. Fontes: [Shizuku API](https://github.com/RikkaApps/Shizuku-API/blob/master/api/src/main/java/rikka/shizuku/Shizuku.java) e [guia oficial](https://github.com/RikkaApps/Shizuku-API/blob/master/README.md).

## Matriz de build

- JDK configurado no workflow: 17.
- AGP: 8.5.2; a tabela oficial do Android exige Gradle 8.7 como mínimo para AGP 8.5.
- Gradle Wrapper: 8.8, acima do mínimo do AGP 8.5.
- Kotlin Gradle Plugin: 2.4.21; a tabela oficial do Kotlin indica compatibilidade completa com Gradle 7.6.3–9.7.0 e AGP 8.5.2–9.3.1. A combinação declarada não exige troca de versões neste patch.
- Fontes: [compatibilidade oficial Kotlin/Gradle/AGP](https://kotlinlang.org/docs/gradle-configure-project.html) e [versão mínima Gradle por AGP](https://developer.android.com/build/releases/about-agp).

## Testes executados

- 251 métodos de teste Kotlin/JVM em harness de regressão: PASS, 0 falhas. O harness reutiliza os testes anteriormente compilados e compila a política de reconexão/testes novos contra stubs JUnit temporários.
- Política `UserServiceReconnectPolicy`: cenários de backoff, máximo de tentativas, retomada explícita, estado inativo/desativado, binding/conexão/retry pendentes, Binder ausente, permissão ausente e versão não suportada: PASS.
- Harness de integração com Shizuku simulado: falha transitória no registro do listener de Binder recebido; a segunda chamada registrou somente o listener faltante, preservou os outros dois e `stop()` removeu cada listener uma única vez: PASS.
- Harness de ciclo de vida do UserService: seis sequências `Binder conectado` + `startInputCapture()` com `RemoteException` esgotaram exatamente cinco retries automáticos; uma solicitação ativa posterior iniciou nova rodada limitada: PASS.
- `ShizukuInputCaptureBridge.kt` + política compilados por `kotlinc` contra stubs de contrato Android/Shizuku/AIDL: PASS. Isso verifica sintaxe e tipos expostos nos stubs, não o artefato Android real.
- `tools/native-tests/run.sh`: PASS.
- `tools/native-tests/check-syntax.sh`: PASS.
- `tools/native-tests/check-library-contract.sh`: PASS.
- YAML do workflow e correspondência de versão/artefato: PASS.
- ZIP e SHA-256: PASS.

## Build e hardware

A tentativa Gradle oficial continua bloqueada antes de iniciar tarefas porque o Wrapper precisa baixar Gradle 8.8 e o ambiente não resolve `services.gradle.org` (`UnknownHostException`). `ANDROID_HOME` e `ANDROID_SDK_ROOT` não estão configurados. Portanto, `lintDebug`, `testDebugUnitTest` via Gradle, `assembleDebug`, APK e GitHub Actions remoto não foram confirmados. Não houve teste físico no Samsung Note 20 Ultra, Android 13, One UI ou DeX.

## Avaliação

A mudança corrige uma falha concreta de registro parcial de listeners e oferece uma nova rodada de recuperação sob demanda sem reinicializar o serviço inteiro. A validação estática e os testes isolados passaram; funcionamento end-to-end permanece pendente de Gradle e teste físico.
