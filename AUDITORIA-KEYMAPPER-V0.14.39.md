# Auditoria técnica — KeyMapper 0.14.39

**Data:** 2026-11-04  
**Base:** código-fonte 0.14.38 / versionCode 54  
**Entrega:** 0.14.39 / versionCode 55  
**Foco:** Continuous Camera Movement, handover de contatos e precisão subpixel

## 1. Referências estudadas e força das evidências

### SS KeyMouse Pro 1.0.76 — referência principal de arquitetura

Foi inspecionado estaticamente o XAPK fornecido pelo usuário. A biblioteca ARM64 `libsspro_input.so` expõe símbolos JNI de captura/recuperação, incluindo `nativeStart`, `nativeStop`, `nativeSetGrabMouse`, `nativeReleaseGrab`, `nativeRefreshDevices`, `nativeCensus`, `nativeRecoverStuckGrab` e `nativeIsRunning`. Imports/strings incluem `epoll`, `eventfd`, `inotify`, `pthread`, `ioctl`, `/dev/input` e `/sys/class/input`.

A mesma biblioteca expõe uma interface separada de saída touchscreen (`nativeTouchOpen/Close/IsOpen/Down/Move/Up`) e referências a `/dev/uinput`/`sspro_virt_touch`. A evidência favorece uma arquitetura dividida entre captura física, monitoramento/hot-plug e saída virtual de toque.

**Limite da evidência:** inspeção estática não prova comportamento em execução, permissões, compatibilidade com cada ROM ou algoritmo exato de câmera. Nenhum binário ou código proprietário foi incorporado ao KeyMapper.

### Panda Mouse Pro — Continuous Camera Movement

O histórico público de versões registra que o Panda 13 (21/22 set. 2026) adicionou Continuous Camera Movement para Sight, com objetivo de evitar interrupções próximo às bordas, e menciona preservação de movimentos subpixel em baixa sensibilidade. A versão 14.2 (29 set. 2026) anuncia melhoria do comportamento perto da borda. A listagem atual do Google Play foi atualizada em 7 out. 2026. Esse histórico comprova a finalidade anunciada, não revela o algoritmo privado usado internamente. Referências: `https://apptopia.com/google-play/app/com.panda.mouse/about` e `https://play.google.com/store/apps/details?id=com.panda.mouse`.

### K2er — Boundary / Re-center / Nonstop

A documentação pública diferencia Boundary (limite do ponto simulado), Radius (distância de retorno), Re-center Mode Default/Nonstop, Re-center Delay, Release Delay, Sensitivity X/Y, Auto Release e Offset Correction. A documentação informa que Nonstop não funciona em todos os jogos. Essa ressalva é coerente com a transição sobreposta de ponteiros adotada aqui, mas não prova que o K2er use a mesma sequência de eventos. Referência: `https://doc.k2er.com/mappings/aiming.html`.

### GG Mouse Pro

A documentação pública acessível descreve o mapeamento de teclado/mouse, mas não detalha de maneira verificável a implementação de recentralização da câmera. Nenhum algoritmo interno específico foi atribuído ao produto.

### Android

O projeto já utiliza entrada relativa/Pointer Capture quando disponível e leitura dos históricos de movimento. As coordenadas relativas de mouse devem ser tratadas como deltas; coordenadas absolutas injetadas continuam sujeitas à superfície/display e às restrições do Android.

## 2. Arquitetura existente reaproveitada

A atualização não cria um segundo motor de mouse:

- `CaptureView`/captura evdev continuam sendo a origem dos eventos físicos.
- `MappingEngine` mantém a transformação, sensibilidade, agregação e roteamento da visão.
- `LookReanchorPlanner` continua calculando os pontos de borda e o delta ainda não representado.
- `TouchInjector` mantém a sequência touchscreen e a rota de saída selecionada para o gesto.
- `VirtualCursorController` mantém a posição lógica acumulada independente da tela.
- `CursorReanchorPolicy` calcula a posição visível do retículo sem limitar a posição lógica.

A implementação continua própria e preserva os caminhos anteriores de InputManager/uinput e DeX conforme suas políticas de roteamento.

## 3. Estratégias de câmera agora explícitas

| Modo | Sequência | Vantagem | Limite |
|---|---|---|---|
| `AUTO`/legado `CENTER` | Move até margem, `ACTION_UP`, `ACTION_DOWN` no anchor | Maior compatibilidade com jogos touchscreen simples | Pode haver pausa perceptível durante a troca |
| `NONSTOP` | `ACTION_POINTER_DOWN` no novo ponto, depois `ACTION_POINTER_UP` no antigo | Não deixa o conjunto global de contatos vazio no handover | O jogo recebe dois ponteiros simultâneos brevemente; pode interpretar como multitouch ou rejeitar |
| `INFINITE` | Deltas relativos `SOURCE_MOUSE_RELATIVE` | Não depende de coordenada absoluta ou de reancoragem touchscreen | O destino precisa aceitar a origem relativa e o transporte precisa aceitar a injeção |
| `OFF` | Arrasto limitado à margem | Mantém limite explícito | A visão para ao alcançar a margem |

A aplicação não injeta contatos touchscreen fora da área física do display. “Além do limite” significa manter o delta/posição lógica independente e representar o caminho com reancoragem dentro da superfície, ou usar entrada relativa nativa quando aceita.

## 4. Alterações desta versão

### `TouchInjector.swap()`

- Retorna booleano para que o motor saiba se o novo contato realmente ficou ativo e o anterior foi liberado.
- Em Nonstop, tenta criar o ponteiro seguinte antes de liberar o antigo.
- Se não houver slot de ponteiro, usa UP/DOWN clássico para aquele handover, sem ultrapassar o limite definido por `TouchPointerPolicy`.
- Se uma operação de envio falhar, o motor não troca o ID ativo como se tivesse concluído; tenta liberar os IDs envolvidos e descarta o trabalho de câmera antigo.

### `MappingEngine`

- `beginLook()` confirma `touch.isDown()` antes de marcar a câmera ativa.
- O modo selecionado por perfil/controle decide entre Auto, Nonstop, relativo nativo e OFF.
- Após um lote, o cursor lógico avança pelo `delta - remainder` devolvido pelo planejador; não deriva deltas de coordenadas absolutas que foram recentralizadas.
- O caminho pendente continua serializado antes de amostras posteriores. Cancelamento limpa fila, callbacks, residual e estado do gesto.

### Precisão subpixel

`SensitivityEngine` guarda o resíduo em `Double`. A API continua recebendo deltas `Float`; o ganho é impedir o acúmulo de arredondamento adicional no resíduo antes da conversão inteira. A correção foi motivada por teste de regressão que falhava com `0,35 px` em um trajeto de ida e volta: o `Float` aproximado podia deixar um total negativo ligeiramente acima de `-1`, e `toInt()` truncava para `0`.

### Persistência e interface

- `Rot` foi isolado em `data/Rot.kt` para permitir testes de política sem carregar classes Android/JSON.
- `AppSettings.fromJson` e `HudButton.fromJson` aceitam `NONSTOP=4` e preservam as opções antigas.
- A estratégia global é mantida em `AppSettings`; cada botão Sight/Aim pode sobrescrevê-la por `rotMode`, persistido com a lista de botões dentro do perfil. O modo relativo não é confundido com Nonstop.
- `lookR` permanece o raio visual/interativo da área Sight no HUD; não é apresentado como margem física de recentralização. A margem do toque segue a geometria válida do display e o inset interno do motor.
- O painel flutuante mostra “Movimento contínuo da câmera”. A configuração global é descrita como padrão; cada Sight/Aim pode sobrescrever a estratégia nas propriedades individuais, evitando ambiguidade entre o valor global e o perfil de cada controle.

## 5. Arquivos alterados em relação à base 0.14.38

- `.github/workflows/build.yml`
- `README.md`
- `app/build.gradle.kts`
- `app/src/main/java/com/example/keymapper/data/AppSettings.kt`
- `app/src/main/java/com/example/keymapper/data/HudButton.kt`
- `app/src/main/java/com/example/keymapper/data/Rot.kt` (novo)
- `app/src/main/java/com/example/keymapper/domain/SensitivityEngine.kt`
- `app/src/main/java/com/example/keymapper/injection/TouchHandoffPolicy.kt` (novo)
- `app/src/main/java/com/example/keymapper/injection/TouchInjector.kt`
- `app/src/main/java/com/example/keymapper/mapping/LookModePolicy.kt`
- `app/src/main/java/com/example/keymapper/mapping/MappingEngine.kt`
- `app/src/main/java/com/example/keymapper/overlay/ConfigPanelView.kt`
- `app/src/main/java/com/example/keymapper/ui/SettingsScreen.kt`
- `app/src/test/java/com/example/keymapper/domain/SensitivityEngineTest.kt`
- `app/src/test/java/com/example/keymapper/injection/TouchPointerPolicyTest.kt`
- `app/src/test/java/com/example/keymapper/mapping/LookModePolicyTest.kt`
- `app/src/test/java/com/example/keymapper/mapping/LookReanchorPlannerTest.kt`

## 6. Testes executados nesta sessão

Compilados e executados em harness Kotlin/JVM isolado, usando JUnit stubs locais para os testes sem dependência Android:

- `LookReanchorPlannerTest`
- `LookModePolicyTest`
- `TouchPointerPolicyTest`
- `SensitivityEngineTest`
- `VirtualCursorControllerTest`
- `CursorReanchorPolicyTest`

**Resultado: 48/48 passaram.** Cobertura inclui quatro bordas, diagonal, mudanças de direção, múltiplas telas por lote, drenagem de excedente, cursor lógico fora das dimensões do display, retículo reancorado, política Nonstop e ausência de slot livre, sensibilidade baixa, acumulação de 0,35 px e retorno na direção oposta.

Também foram verificados:

- parse do workflow `.github/workflows/build.yml` como YAML válido;
- `versionName=0.14.39` e `versionCode=55`;
- nome do artefato `keymapper-0.14.39-debug`;
- aceitação de `Rot.NONSTOP` nos parsers de configuração;
- presença das novas políticas no código e integração de `TouchInjector`.

## 7. Validações não executadas / pendentes

Tentativa real:

```text
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug --stacktrace --console=plain
```

Falhou antes de iniciar o Gradle porque o wrapper não conseguiu resolver `services.gradle.org` (`java.net.UnknownHostException`). O ambiente não tem Android SDK configurado. Assim:

- `:app:testDebugUnitTest` Gradle: não executado;
- Android Lint: não executado;
- `assembleDebug`: não executado;
- APK: não gerado;
- workflow GitHub Actions: YAML verificado localmente, mas execução remota não realizada;
- instalação no Note 20 Ultra/Android 13/One UI: pendente;
- Samsung DeX/monitor externo: pendente;
- testes com jogos reais: pendentes.

## 8. Riscos a validar em aparelho/jogo real

1. **Nonstop:** o destino pode interpretar o segundo ponteiro como gesto de zoom/pinch, trocar o alvo ou cancelar o controle. Testar por jogo; oferecer Auto como fallback.
2. **Auto:** preserva todos os deltas, mas a transição UP/DOWN pode provocar breve descontinuidade visual dependendo do jogo.
3. **Relativo nativo:** `SOURCE_MOUSE_RELATIVE` só funciona quando o transporte e o jogo aceitam a origem de mouse relativa; não afirmar compatibilidade universal.
4. **DeX:** a saída uinput é restrita à política de display principal; monitor externo continua dependendo da rota InputManager compatível. O comportamento real exige dispositivo físico.
5. **Permissões:** `/dev/input`, `EVIOCGRAB`, `/dev/uinput` e `INJECT_EVENTS` dependem de Shizuku/UID, kernel e SELinux da ROM.
6. **Latência:** o limite nominal de movimento touchscreen perto de 120 Hz é política do injetor, não medição de latência ponta a ponta.

## 9. Critério de conclusão

A versão 0.14.39 é uma atualização de fonte com lógica e testes puros aprovados; não está homologada como APK ou em jogos reais. Para declarar Continuous Camera Movement concluído, ainda é necessário executar o build Gradle no CI e validar no aparelho: todos os sentidos, quatro bordas, diagonal, baixa sensibilidade, direção reversa, pausa/retomada, mouse desconectado, ausência de toque preso, Nonstop em cada jogo e DeX.
