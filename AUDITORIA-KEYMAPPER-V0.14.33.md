# Auditoria técnica — KeyMapper 0.14.33

**Base inspecionada:** fonte 0.14.32
**Versão gerada:** `0.14.33` / `versionCode 49`
**Dispositivo-alvo declarado:** Samsung Galaxy Note 20 Ultra SM-N986B, Android 13 / One UI / Samsung DeX
**Referência arquitetural:** KeyMouse Pro 1.0.76, análise estática anterior de manifesto, DEX, símbolos ELF e strings nativas. O binário fechado não foi copiado nem incorporado.

## 1. Veredito executivo

A 0.14.33 melhora o workspace visual, adiciona reposicionamento por arraste na prévia do perfil e corrige problemas concretos de seleção de display tanto no painel quanto no editor, classificação de teclados e normalização temporal do backend evdev. A arquitetura geral permanece a do KeyMapper: UserService Shizuku + JNI/evdev para captura, MappingEngine para processar associações e InputManager via Shizuku para a saída touchscreen.

**Não há evidência suficiente para declarar a aplicação jogável ou pronta para uso real.** O build Android não pôde ser executado neste ambiente, não foi gerado APK desta versão e não houve teste no Note 20 Ultra. Além disso, a saída touchscreen continua dependente de InputManager; não existe ainda o backend touchscreen virtual `uinput` observado estaticamente no KeyMouse Pro.

A interface nova aproxima o fluxo de configuração de um emulador de PC: perfil selecionado, prévia 16:9 arrastável, tabela de associações e painel de propriedades. A prévia grava posições de controles padrão; o HUD sobre o aplicativo de destino continua disponível para ajustes finos e para os controles legados que não são editáveis neste workspace.

## 2. Referência KeyMouse Pro

A inspeção estática anterior do `libsspro_input.so` identificou interfaces JNI de captura (`nativeStart`, `nativeStop`, `nativeSetGrabMouse`, `nativeReleaseGrab`, `nativeRefreshDevices`, `nativeCensus`, `nativeRecoverStuckGrab`, `nativeIsRunning`) e de saída touchscreen (`nativeTouchOpen`, `nativeTouchClose`, `nativeTouchDown`, `nativeTouchMove`, `nativeTouchUp`). Imports/strings incluíam `epoll`, `eventfd`, `inotify`, `/dev/input`, `/sys/class/input`, `/dev/uinput`, `sspro_virt_touch`, heartbeat e recuperação.

Isso sustenta a hipótese arquitetural de captura nativa event-driven, hot-plug e saída virtual separada. **Não prova** que o aplicativo consiga abrir os nós ou criar `uinput` em todas as ROMs Samsung. O KeyMapper reutiliza apenas padrões arquiteturais independentes; não incorpora código, DEX ou biblioteca fechada do KeyMouse Pro.

## 3. Mudanças da 0.14.33

### 3.1 Interface desktop/emulador

- `MainActivity.kt`: quando a largura de janela é desktop, a navegação usa sidebar de 212 dp com identidade KeyMapper Studio, destinos Painel/Editor/Ajustes e rodapé Android/DeX. No celular, a barra inferior original é mantida.
- `MappingEditorScreen.kt`: workspace responsivo com painel de estado, ferramentas, prévia 16:9, tabela de associações e propriedades. Após reservar a sidebar, o layout de três colunas exige 760 dp de largura útil; abaixo disso, empilha os painéis.
- A prévia 16:9 lista, seleciona e permite arrastar controles `STANDARD`; a posição normalizada é salva no perfil ativo e respeita o ajuste de grade configurado. O HUD ao vivo continua disponível para ajustes finos e para controles especializados legados.
- A ação “Adicionar tecla” chama uma ação dedicada no `MappingService`. O serviço aloca ID livre, cria `HudButton` padrão com `key=0`, posição central, salva no perfil ativo e abre o `ConfigPanelView` real para associar uma tecla. O limite de 128 controles continua aplicado.
- Controles especializados legados aparecem na tabela, mas ficam somente leitura no workspace novo. As rotas específicas de mira/disparo não foram ajustadas nesta atualização.

### 3.2 Seleção de display / Samsung DeX

O editor e o painel principal agora aplicam a preferência persistida (`settings.useExternalDisplay` / `cfg.useExternalDisplay`) no `DisplayHelper` antes de chamar `target()`. A versão anterior criava helpers novos com `useExternal=false`, podendo abrir o app selecionado na tela interna apesar da preferência por display externo. A abertura usa `ActivityOptions.setLaunchDisplayId()` e falha visivelmente se o display externo selecionado não estiver disponível; não faz fallback silencioso para outro display.

### 3.3 Captura nativa evdev

- `inspectDevice()` não exige mais os três códigos A + Espaço + Enter ao classificar teclado. Conta capacidades de tecla na faixa `1..<BTN_MISC` e exige ao menos quatro. Isso atende layouts compactos/numpads comuns; dispositivos HID incomuns com menos de quatro códigos podem continuar fora da classificação.
- Quando `EVIOCSCLOCKID(CLOCK_MONOTONIC)` falha, o leitor amostra `CLOCK_REALTIME` entre dois horários monotônicos e conserva um offset por dispositivo. O timestamp do evento original é convertido para monotônico, preservando intervalos entre eventos. Se a amostra do offset também falhar, o código usa o relógio monotônico de processamento como último recurso.
- Mantidos `epoll + eventfd`, `inotify` com varredura de segurança, hot-plug, descarte após `SYN_DROPPED`, fechamento de descritores e tratamento de falha no registro do `epoll`.
- **Correção de precisão da documentação:** a versão entregue encaminha os lotes do callback JNI ao looper principal através de `ui.post`. Não foi implementado despacho mouse-only em thread dedicada. A alteração anterior que alegava o contrário não correspondia ao pacote 0.14.32 e não foi preservada aqui.

### 3.4 Bridge Shizuku e motor

A implementação 0.14.32 do bridge, do worker nativo e da ligação ao `MappingEngine` foi inspecionada e mantida. Start, reconnect, binder death, timeout e parada permanecem no fluxo existente. Não foi criada uma nova camada de injeção nem foi alterada a transformação de movimento relativo.

## 4. Auditoria dos subsistemas

| Subsistema | Estado no código | Limitação / teste pendente |
|---|---|---|
| UI celular | Mantida com navegação inferior | Precisa inspeção visual em aparelho real |
| UI desktop/DeX | Sidebar + workspace responsivo | A densidade e largura útil variam; precisa capturar tela no DeX |
| Prévia do HUD | Arrasta controles padrão e salva posição normalizada no perfil | Testar gesto, snap e persistência com mouse/toque no aparelho |
| Perfis por aplicativo | Persistência em `AppSettings`/`AppProfile` | Precisa testar troca de app, reload e reinício do serviço |
| Adicionar controle padrão | Action explícita, ID livre, persistência e painel de propriedades | Precisa teste de interação e confirmação de gravação no aparelho |
| Captura evdev | JNI em UserService Shizuku; `epoll`, `inotify`, `EVIOCGRAB` | `/dev/input` e grab dependem de UID/SELinux/firmware |
| Teclado | Reconhecimento por capacidades de chave | Critério mínimo não cobre todo HID exótico |
| Timestamp | Monotônico pedido; offset realtime como fallback | Depende de amostra de relógio consistente; requer teste no kernel Samsung |
| Mouse / Pointer Capture | evdev e fallback Pointer Capture por classe | Pointer Capture é limitado à janela; não é captura global |
| Motor MappingEngine | Integra eventos ao motor atual | O retorno do callback não prova que o app destino consumiu o evento |
| Saída touchscreen | InputManager via Shizuku | Sem backend próprio `/dev/uinput`; sem confirmação de consumo pelo destino |
| ADB sem fio | Kadb TLS independente do Shizuku para diagnóstico | Não substitui captura nem injeção |
| Recuperação do serviço | Bridge Shizuku e worker nativo com recuperação | Requer stress test Android com desconexão/reconexão real |
| GitHub Actions | Lint, testes unitários e assembleDebug | Workflow foi atualizado, mas não executado nesta sessão |

## 5. Toolchain e build

Configuração resultante:

- AGP `8.5.2`
- Gradle Wrapper `8.8`
- Kotlin Gradle Plugin / Compose plugin `2.4.21`
- JDK alvo `17`
- compileSdk / targetSdk `34`
- minSdk `26`
- NDK `26.3.11579264`
- CMake `3.22.1`

A documentação oficial do Kotlin lista KGP `2.4.20–2.4.21` como compatível com Gradle `7.6.3–9.7.0` e AGP `8.5.2–9.3.1`; portanto Gradle 8.8 e AGP 8.5.2 estão dentro da faixa declarada. Kotlin 2.4.21 consta como release de 8 de outubro de 2026. Fontes: https://kotlinlang.org/docs/gradle-configure-project.html e https://kotlinlang.org/docs/releases.html.

O ambiente desta sessão não tem `ANDROID_HOME`/`ANDROID_SDK_ROOT`, não tem distribuição Gradle 8.8 em cache e não consegue resolver `services.gradle.org` pela rede. Portanto não foram executados `lintDebug`, `testDebugUnitTest` ou `assembleDebug`.

## 6. Verificações realizadas nesta atualização

| Verificação | Resultado |
|---|---|
| `evdev_capture.cpp` com Clang C++17, JNI JDK 21, `-Wall -Wextra -Werror=return-type` | Aprovado nesta sessão |
| Leitura estática de `MappingService`, `ShizukuInputCaptureBridge`, `KeyMapperShellService`, `OverlayManager`, `ConfigPanelView` e `MappingEngine` | Concluída; sem alteração do fluxo de mira/disparo |
| Parser Kotlin 1.9 em arquivos Kotlin alterados sem classpath Android/Compose | Sem erro sintático (`expecting`/tokens); erros de resolução de tipos são esperados sem dependências e não contam como compilação |
| Gradle Lint / unit tests / assembleDebug | Não executados: SDK ausente e wrapper sem distribuição/cache; download depende de rede indisponível |
| Instalação e teste no SM-N986B Android 13 | Não executados |
| EVIOCGRAB no firmware Samsung | Não confirmado |
| Injeção touchscreen consumida pelo app alvo | Não confirmado |
| Jogabilidade ponta a ponta | Não confirmada |

## 7. Critérios necessários antes de chamar de jogável

1. Gerar APK no GitHub Actions e confirmar que Lint, testes unitários e `assembleDebug` passam.
2. Instalar no SM-N986B com Shizuku autorizado; verificar o relatório de UID/SELinux e se `/dev/input` é legível e o `EVIOCGRAB` funciona.
3. Confirmar captura de teclado e mouse, troca de perfil e hot-plug; conferir liberação do mouse após pausar/parar.
4. Confirmar que o overlay e o HUD aparecem no display selecionado, inclusive após conectar/desconectar monitor DeX.
5. Testar uma associação padrão neutra (por exemplo, uma tecla de navegação em uma tela de teste) e confirmar no destino o evento DOWN/UP e a liberação sem eventos presos.
6. Medir estabilidade contínua, uso de CPU, perdas de eventos, ANR e falhas ao alternar foco/rotação/display.
7. Confirmar o funcionamento da saída no destino. O retorno positivo do InputManager não equivale a confirmação de evento consumido; o backend `/dev/uinput` ainda não existe.

## 8. Conclusão

A 0.14.33 é uma atualização de código-fonte com workspace de aparência desktop, prévia de mapeamento arrastável com persistência, correção da seleção de display, reconhecimento menos restritivo de teclado e normalização mais correta do timestamp evdev. O projeto está mais organizado para configuração semelhante a um emulador de PC, mas a camada de injeção permanece a diferença técnica mais importante em relação às evidências do KeyMouse Pro. Oito verificações isoladas das políticas puras passaram, mas elas não validam o gesto Compose nem a persistência DataStore. Sem build Android e teste físico ponta a ponta, a conclusão honesta é: **não há confirmação de que o app esteja jogável**.
