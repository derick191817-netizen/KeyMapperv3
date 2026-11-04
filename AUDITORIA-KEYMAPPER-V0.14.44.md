# Auditoria técnica — KeyMapper 0.14.44

**Base:** 0.14.43 / versionCode 59
**Entrega:** 0.14.44 / versionCode 60
**Foco:** reutilização comportamental de referências e refinamento de Sight/Aim.

## 1. Decisão de engenharia

O pedido de “copiar os outros mapeadores” foi tratado como reutilização de mecânicas comprovadas e de arquitetura observável, não como transplante de código proprietário. Os APKs/XAPK enviados foram examinados estaticamente; não houve engenharia para remover proteção, copiar assets proprietários ou incorporar bibliotecas fechadas.

O KeyMapper já possuía uma arquitetura compatível com a separação vista no SS KeyMouse Pro: captura evdev em `evdev_capture.cpp` e saída touchscreen virtual em `uinput_touch.cpp`, com roteamento Android separado. Em vez de substituir esses componentes, esta versão aproveita o desenho existente e incorpora parâmetros de comportamento presentes na documentação do K2er.

## 2. O que foi alterado

### Sight/Aim por controle

- `app/src/main/java/com/example/keymapper/data/HudButton.kt`
  - `aimBoundaryRadiusPct: Float = 0f`.
  - `aimAutoReleaseMs: Int = -1`.
  - Serialização JSON e leitura retrocompatível dos campos novos.

- `app/src/main/java/com/example/keymapper/mapping/LookBoundaryPolicy.kt`
  - Conversão do percentual em pixels usando o menor lado da área.
  - Limitação do raio pelas quatro bordas de toque seguras.
  - Regra de herança estrita: somente `-1` herda o valor global; `0` mantém o contato; negativos corrompidos viram `0`.

- `app/src/main/java/com/example/keymapper/mapping/LookReanchorPlanner.kt`
  - Novo planejador radial.
  - A interseção segmento-círculo é calculada em `Double`.
  - Deltas restantes são preservados após cada recentralização.
  - O limite de reancoragens por lote continua respeitado.
  - Um raio negativo ou não finito é rejeitado.

- `app/src/main/java/com/example/keymapper/mapping/MappingEngine.kt`
  - O raio e o ponto de origem ficam congelados durante o gesto para evitar alterações do referencial no meio do movimento.
  - A configuração por controle alimenta o motor de movimento.
  - Auto Release usa valor individual ou herda a preferência global.
  - O modo de fronteira limitada e o mouse relativo nativo não recebem a política radial por toque.

- `app/src/main/java/com/example/keymapper/overlay/ConfigPanelView.kt`
  - Controles de raio e Auto Release adicionados ao painel avançado de Aim.
  - Texto explica quais estratégias usam o raio e a semântica dos valores.

### Compatibilidade com perfis existentes

Perfis antigos não contêm as chaves novas. `HudButton.fromJson()` usa defaults seguros (`0f` e `-1`), preservando a operação retangular e a configuração global anterior. `toJson()` grava os dois novos campos nos próximos salvamentos.

## 3. Referências comparadas

| Referência | Evidência observada/documentada | Decisão no KeyMapper |
|---|---|---|
| Panda Mouse Pro | Histórico público descreve Continuous Camera Movement para Sight e preservação de subpixel | Mantida a acumulação subpixel e estendida a estratégia de reancoragem; não se afirma equivalência interna ao algoritmo fechado |
| K2er | Documenta Boundary, Radius, Default/Nonstop, Re-center Delay, Release Delay e Auto Release | Radius e Auto Release foram adicionados por controle; Default/Nonstop permanecem modos distintos |
| GG Mouse Pro 3 | Changelog 3.1.6 menciona prevenção de congelamento da visão no Free Fire; também corrige reset de dedo/reativação do joystick | Não foi inferido o algoritmo interno. A prevenção genérica de estados presos já se apoia nos mecanismos independentes de recuperação de contato/eventos do KeyMapper |
| SS KeyMouse Pro | Binário ARM64 apresenta APIs JNI de capture/grab e APIs separadas de touchscreen virtual; strings incluem epoll/eventfd/inotify/uinput | A captura evdev e a saída virtual do KeyMapper foram preservadas como subsistemas separados; nada proprietário foi copiado |

## 4. Testes executados

### Kotlin puro — 29/29 aprovados

Executados com `kotlinc` 1.9.0 e um harness JUnit-lite temporário, apenas para as classes sem dependência Android:

- `LookReanchorPlannerTest`: 20 métodos.
- `LookBoundaryPolicyTest`: 9 métodos.
- Total: 29 aprovados, 0 falhas.

Casos incluídos:

- Dentro/fora do raio circular.
- Cruzamento exato da circunferência.
- Movimento horizontal e diagonal.
- Raio limitado por bordas seguras.
- Deltas grandes em batches sucessivos.
- Mudanças de direção e ausência de viés direcional.
- Movimento subpixel de 0,35 px.
- Rejeição de raio negativo/não finito e origem externa.
- Herança de Auto Release, valor zero e limites numéricos.
- Geometrias inválidas e folga subpixel.

O harness e seus stubs ficaram em `/tmp/km_junit` e **não foram incluídos** no projeto ou no ZIP.

### Nativo

- `bash tools/native-tests/run.sh`: aprovado (`evdev JNI batch recovery host tests`).
- `bash tools/native-tests/check-syntax.sh`: aprovado para `evdev_capture.cpp` e `uinput_touch.cpp` com os headers JNI do JDK local.

### Estrutura e CI

- `versionName=0.14.44`, `versionCode=60`, artefato `keymapper-0.14.44-debug` conferidos.
- Workflow `.github/workflows/build.yml` preserva as etapas host C++, lint, testes Gradle e assembleDebug.
- ZIP e SHA-256 foram validados após empacotamento.

## 5. Build Android: pendente

Não há Android SDK configurado neste ambiente, e a distribuição Gradle 8.8 não está disponível em cache. A tentativa anterior do Wrapper foi bloqueada por `UnknownHostException: services.gradle.org`.

Consequentemente, nesta sessão:

- `:app:testDebugUnitTest` via Gradle: não executado.
- `:app:lintDebug`: não executado.
- `:app:assembleDebug`: não executado.
- GitHub Actions remoto: não executado.
- APK: não gerado.
- Teste físico em Note 20 Ultra/Android 13/DeX e teste em jogo: pendentes.

## 6. Limitações conhecidas

- A mecânica radial é uma reprodução comportamental, não uma confirmação do algoritmo proprietário do Panda/K2er.
- Nonstop mantém a estratégia de dois contatos sobrepostos; a aceitação depende do jogo e deve ser testada no destino.
- GG Mouse Pro anuncia prevenção de congelamento da visão, mas seu mecanismo interno não foi identificado na amostra ofuscada. Não é correto afirmar que o KeyMapper já reproduz esse mecanismo específico.
- A recente configuração de raio só se aplica a `AUTO`/`NONSTOP`; `OFF` permanece limitado, `INFINITE` usa mouse relativo nativo e depende do suporte do destino.

## 7. Próximos testes prioritários

1. Compilar Android e executar toda a suíte Gradle em CI.
2. Testar as estratégias AUTO e NONSTOP em Free Fire e outro jogo, observando a sequência de ponteiros e os travamentos da visão.
3. Medir latência, jitter e perda de deltas no Note 20 Ultra com DeX.
4. Validar persistência ao fechar/reabrir perfis antigos e novos.
5. Conferir se Auto Release em 0 mantém contato até desativação/pausa e se valores positivos liberam no tempo previsto.
