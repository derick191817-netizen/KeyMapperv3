# KeyMapper 0.14.28 — auditoria de robustez, backends e aplicativos de referência

**Versão:** 0.14.28  
**versionCode:** 44  
**Escopo:** aplicativo Android de mapeamento de teclado/mouse, HUD, perfis, displays e integração ADB/Shizuku.  
**Data:** 02/11/2026

## 1. Resultado executivo

A revisão encontrou e corrigiu problemas localizados de validação ADB, consumo de métricas, seleção de display e ciclo de vida de captura. Também comparou a organização interna dos três pacotes de referência fornecidos.

**O projeto ainda não pode ser declarado como compilado, funcional em runtime ou livre de crashes.** A tentativa de build falhou antes de o Gradle avaliar o projeto porque o ambiente não conseguiu resolver `services.gradle.org` (`UnknownHostException`). Não há Android SDK instalado neste ambiente. Nenhum APK foi gerado e nenhum teste físico foi feito no telefone/DeX.

O ponto arquitetural mais importante permanece: **ADB sem fio direto e o motor de mapeamento ainda são subsistemas separados.** O ADB direto autentica, conecta e consulta informações; `MappingService` continua exigindo `ShizukuInjector` para a rota de saída atual.

## 2. O que foi corrigido nesta revisão

### ADB sem fio e identidade

- O limite de pareamento passou a ser Android 11 / API 30. Android 9 (API 28) e Android 10 (API 29) não oferecem o fluxo moderno de Wireless Debugging por código.
- Endereço, porta e código são validados separadamente; o host aceita literal IPv4/IPv6, não hostname ou texto de shell.
- O código de pareamento não é persistido e é limpo ao concluir ou falhar a ação.
- Descoberta, pareamento, teste de conexão e remoção de identidade não podem ser iniciados em paralelo pelo diálogo.
- Cancelamentos de corrotina preservam `CancellationException`; não são apresentados como falhas comuns.
- Snapshots anteriores são invalidados antes de nova inspeção e após falha/cancelamento, evitando que dados de outra sessão permaneçam ativos.
- A chave privada ADB é guardada cifrada com AES-GCM por chave não exportável do Android Keystore em `noBackupFilesDir`; essa proteção foi revisada estaticamente, não testada em dispositivo.

### Validação e consumo de métricas

- A política identifica se o endereço ADB pertence a uma interface local do telefone. Um dispositivo remoto da rede não pode alterar as métricas locais.
- `wm size` prioriza `Override size`, mas cai para `Physical size` se o override for ausente ou inválido.
- `wm density` prioriza `Override density`, mas usa `Physical density` se o override for inválido.
- Dimensões fora de 240–16384 px e densidades fora de 80–1000 dpi são rejeitadas pelo fallback.
- O fallback exige endpoint local, display principal e snapshot com idade de até 15 minutos. Métricas nativas válidas continuam prevalecendo.
- O fallback de display consulta as métricas do `DisplayContext` correspondente, em vez de assumir que a densidade do telefone representa a densidade do monitor externo.
- Se as APIs nativas não conseguirem obter dimensões físicas nem modo do display, o código não inventa uma resolução. `MappingService.refreshInfo()` pode invalidar o destino e suspender a saída até a tela voltar a ser identificada.
- A enumeração de monitores e modos tolera falhas temporárias do serviço de display; taxas não finitas ou fora de faixa são descartadas.

### Ciclo de vida da calibração/captura

- A calibração não aceita duas sessões simultâneas.
- O display e a dimensão usados pela calibração são validados antes de iniciar a captura.
- A captura é parada no `finally` da corrotina e quando a tela de calibração é descartada.
- Uma transição duplicada para `GRABBED` após Pointer Capture foi removida.
- A fórmula de sensibilidade não foi alterada nesta correção; foram corrigidos apenas validação, concorrência e liberação de recursos.

### Manifesto e organização

- Foram removidas as entradas de pacotes específicos de Free Fire do bloco `<queries>`. A seleção de aplicativos deixa de depender desses dois nomes fixos.
- Mantidos `QUERY_ALL_PACKAGES`, Shizuku Provider, overlay e serviço foreground já existentes; suas políticas de distribuição e uso ainda precisam de validação no canal de publicação escolhido.



### Robustez da leitura de hardware/display

- `DisplayHelper` não faz mais cast forçado do serviço `DISPLAY_SERVICE` no construtor. Se `DisplayManager` não estiver disponível, a seleção retorna erro controlado e a enumeração/registro de listeners degrada sem derrubar a interface.
- `SettingsScreen.readHw()` isola a consulta do display e da memória. Quando o serviço de display, o monitor externo ou o contexto DeX falha transitoriamente, a tela de configurações continua abrindo e mostra métricas como indisponíveis, em vez de propagar a exceção durante a composição.
- `ScreenInfo.ratio` usa uma proporção neutra válida quando as dimensões ainda não foram obtidas.
- Essas mudanças protegem a interface de diagnóstico/configuração; não substituem os testes de lifecycle do serviço real em hardware.

## 3. Comparação estática dos aplicativos de referência

Os pacotes enviados passaram no teste de integridade ZIP. O conteúdo foi inspecionado de forma estática (manifesto, nomes de classes, DEX, strings e bibliotecas nativas). Isso **não** comprova comportamento em runtime, sucesso de autorização ou funcionamento num telefone específico.

### Panda Mouse Pro

- O pacote contém serviço próprio de pareamento ADB, atividade shell, provider Shizuku e biblioteca ADB nativa.
- Também contém DEX auxiliar e bibliotecas de input em assets, com classes de mapeamento de mouse, teclado, joystick e toque.
- A arquitetura separa atividades de ativação, auxiliares shell e componentes de input; não é apenas uma tela que chama `rish` para cada evento.

### SS KeyMouse Pro 1.0.76

- O XAPK é dividido em base APK e splits por ABI/idioma/densidade. A biblioteca de input está no split ARM64.
- O manifesto identifica atividades de ativação sem fio, serviço de pareamento e serviço flutuante, além de Shizuku Provider.
- A biblioteca nativa expõe operações distintas de iniciar/parar, adquirir/liberar captura, atualizar dispositivos, recuperar captura travada e encaminhar eventos de toque.
- A análise dos símbolos e imports indica uma camada nativa com filas/eventos e monitoramento; os símbolos não provam a taxa real de eventos nem o resultado no runtime.

### ZTE/GG Mouse 1.18.0

- Existem fluxos separados para ADB com fio, ADB sem fio, Root e Shizuku.
- Existem módulos separados para informação do dispositivo e gerenciamento de tela.
- O pacote inclui processo de serviço ADB e componentes de ponte/captura/injeção nativos.

### Conclusão das referências

O padrão mais consistente entre os três é a separação entre **método de ativação**, **processo privilegiado**, **captura de periféricos**, **saída de eventos**, **serviço flutuante/HUD** e **módulos de dispositivo/tela**. Não foram copiados código, DEX ou bibliotecas proprietárias.

## 4. Matriz real de capacidades do KeyMapper 0.14.28

| Capacidade | Estado real | Observação |
|---|---|---|
| Shizuku | Implementado no caminho atual | `MappingService` verifica Binder, autorização do app, `INJECT_EVENTS` e serviço InputManager. |
| ADB sem fio direto | Implementado em código | Kadb executa pareamento TLS, conexão e consultas de diagnóstico; runtime não testado. |
| Descoberta mDNS | Implementada em código | Trata endpoints TLS de pareamento e de conexão separadamente; runtime não testado. |
| ADB direto como backend do mapeador | Não implementado | Não substitui `ShizukuInjector` e não inicia o motor de entrada sozinho. |
| ADB iniciado por computador | Externo ao aplicativo | Pode ser usado no procedimento de ativação do Shizuku; não há cliente USB/host ADB selecionável na UI. |
| Root independente | Não implementado como backend | Identificar UID 0 numa sessão não cria uma rota Root do `MappingService`. |
| Captura nativa evdev contínua | Não implementada | O caminho atual usa Pointer Capture do Android, limitado à janela focada; o fallback genérico aceita apenas eixos explicitamente relativos. |
| Criação de dispositivo uinput | Não implementada | A sondagem de permissões/open-close não equivale a criar dispositivo virtual. |
| Alterar resolução do Android | Não implementado | Não há `wm size <largura>x<altura>` de escrita. |
| Alterar densidade do Android | Não implementado | Não há `wm density <dpi>` de escrita. |
| Ler resolução/densidade via ADB | Implementado em código | `wm size` e `wm density` são somente leitura nesta versão. |
| Selecionar monitor / DeX | Implementado em código | O monitor externo é detectado/selecionado; métricas vêm do contexto do display correto. |
| Preferência de taxa de atualização do overlay | Implementada em código | `OverlayManager` usa `preferredDisplayModeId`/`preferredRefreshRate` quando há modo válido. Isso não é alteração da resolução global do Android. |

## 5. Por que a alteração de resolução não foi ativada nesta versão

Alterar `wm size` ou `wm density` é uma operação persistente sobre a geometria global do aparelho. Para ser robusta, a funcionalidade precisa:

1. capturar e validar os valores originais antes da alteração;
2. permitir somente comandos tipados e valores dentro de limites definidos;
3. exigir confirmação explícita após aplicar o novo modo;
4. reverter automaticamente em caso de não confirmação;
5. persistir o estado de rollback de forma que um encerramento do processo ou reinício do aparelho não deixe a tela presa num override inesperado;
6. restringir a operação ao endpoint local validado, não a outro aparelho descoberto na rede.

O KeyMapper 0.14.28 ainda não tem esse fluxo completo. Por isso, não executa comandos de alteração de resolução ou densidade.

## 6. Toolchain e dependências

- AGP 8.5.2.
- Gradle Wrapper 8.8.
- Kotlin Android / Compose Plugin 2.4.20.
- JDK 17.
- compileSdk/targetSdk 34; minSdk 26.
- Kadb e Kadb mDNS 2.1.5; Shizuku 13.1.5.

A versão Kadb publicada usa Kotlin 2.4.20 e fornece artefatos Android; o módulo mDNS documenta os serviços `_adb-tls-pairing._tcp` e `_adb-tls-connect._tcp`. A compatibilidade de versões do plugin Kotlin lista a faixa que inclui Gradle 8.8 e AGP 8.5.2. Essas verificações de documentação não substituem a resolução efetiva de dependências e compilação deste projeto.

## 7. Verificações executadas nesta sessão

| Verificação | Resultado |
|---|---|
| Compilação isolada de `AdbEndpointPolicy` e `AdbDisplayFallbackPolicy` com `kotlinc` 1.9.0 | Aprovada |
| Smoke test manual das políticas ADB/geometry | 17/17 asserções aprovadas |
| `AndroidManifest.xml` | XML válido |
| `.github/workflows/build.yml` | YAML válido; job `build` encontrado |
| Integridade do ZIP Panda Mouse Pro | Sem erros de compressão |
| Integridade do XAPK SSPro | Sem erros de compressão |
| Integridade do APK ZTE/GG Mouse | Sem erros de compressão |
| `./gradlew :app:testDebugUnitTest :app:assembleDebug` | Bloqueado antes de avaliar o projeto: `UnknownHostException: services.gradle.org` |

O smoke test isolado cobre normalização IPv4/IPv6, rejeição de hostname/shell syntax, limites de porta e código, fronteira API 30, reconhecimento de endpoint local, leitura/parsing de resolução/densidade, allowlist de comandos, prioridade de métricas nativas e rejeição de endpoints remotos, displays externos, snapshots antigos/futuros e métricas inválidas.

## 8. O que não foi possível validar

- compilação integral do projeto Android com Kotlin 2.4.20;
- execução dos testes JUnit via Gradle;
- resolução real de todos os artefatos do Maven Central no build deste checkout;
- geração e instalação de APK;
- pareamento e descoberta mDNS reais no Android 13/One UI;
- uso do Android Keystore e recuperação de identidade no telefone;
- autorização e entrega de eventos pelo Shizuku/InputManager em runtime;
- captura de mouse/teclado, overlay, rotação, reconnect e desconexão do DeX em hardware;
- comportamento após suspensão de processo, encerramento forçado ou reinicialização.

O ambiente não tem Android SDK configurado. O Gradle Wrapper 8.8 não está em cache e falhou ao tentar baixar a distribuição por DNS. Logo, os arquivos fonte não devem ser tratados como um APK pronto para instalação.

## 9. Próxima sequência recomendada

1. Executar `:app:testDebugUnitTest` e `:app:assembleDebug` em CI com Gradle 8.8, JDK 17 e Android SDK 34; corrigir qualquer falha real antes de alterar mais módulos.
2. Validar no telefone pareamento, TLS, mDNS, armazenamento Keystore e reconexão, sem confundir o sucesso ADB com autorização Shizuku.
3. Criar módulo de gerenciamento de tela independente, começando por operações tipadas e rollback persistente para resolução/densidade; incluir testes e uma recuperação após reboot antes de expor a função.
4. Projetar o gerenciador de capacidades para reportar, por backend, o que está realmente disponível. Não mostrar “ativo” quando apenas o método de ativação foi detectado.

**Conclusão:** há correções estáticas e melhorias de robustez no código, e as referências foram analisadas. Ainda não há evidência suficiente para afirmar que o aplicativo inteiro compila ou funciona sem crashes em hardware real.
