# Auditoria técnica — KeyMapper 0.14.41

**Data:** 2026-11-04  
**Base:** KeyMapper 0.14.40 / versionCode 56  
**Entrega:** KeyMapper 0.14.41 / versionCode 57  
**Foco:** recuperação determinística depois de um lote nativo evdev não aceito pelo looper principal.

## 1. Problema identificado

O callback nativo envia registros evdev ao `Handler` principal. Quando `Handler.post` retorna `false`, o lote inteiro não chega ao consumidor. Um lote pode conter um `KEY_DOWN`, `KEY_UP`, botão, scroll ou parte de um relatório `REL_X/REL_Y`. Se uma transição for perdida, o estado interno pode ficar diferente do estado físico do periférico. Um relatório relativo incompleto também poderia permanecer no agregador até um marcador futuro.

`Handler.post` retornar `false` indica que a mensagem não foi aceita pelo Handler/Looper (por exemplo, encerramento do looper); não é uma medição de fila cheia. O contador existente permanece como métrica de lote descartado, não de ocupação máxima.

## 2. Correção implementada

### Sinalização sem acesso cruzado a estado não thread-safe

`NativeInputBatchLossGuard` usa `AtomicBoolean`. A thread que recebe o callback JNI apenas marca a recuperação pendente. Não acessa `MappingEngine`, `PhysicalInputState`, `OverlayManager` ou `NativeMouseReportAggregator` fora do looper responsável.

### Recuperação no consumidor

Antes de interpretar o próximo lote aceito, `processNativeInputBatch` consome o sinalizador. Quando há perda pendente:

1. `OverlayManager.releasePhysicalInputs()` libera estados da captura `MotionEvent`.
2. `releaseAllMappedInputs()` limpa o estado físico evdev e relatórios relativos parciais e executa `MappingEngine.releaseAll()`.
3. O lote atual então é processado como nova sequência de entrada.

Múltiplas perdas antes da próxima recuperação são coalescidas em um único reset. Uma perda posterior volta a marcar a recuperação.

A estratégia é fail-safe: após perder a sequência, não tenta adivinhar quais teclas ou botões continuam fisicamente pressionados. O utilizador pode precisar soltar e pressionar novamente uma entrada que estava mantida durante a perda, mas o motor não deve conservar DOWNs órfãos indefinidamente.

## 3. Arquivos alterados

- `app/src/main/java/com/example/keymapper/input/NativeInputBatchLossGuard.kt` (novo)
- `app/src/main/java/com/example/keymapper/service/MappingService.kt`
- `app/src/test/java/com/example/keymapper/input/NativeInputBatchLossGuardTest.kt` (novo)
- `app/build.gradle.kts`
- `.github/workflows/build.yml`
- `README.md`
- `CHANGELOG-KEYMAPPER-V0.14.41.md`
- `AUDITORIA-KEYMAPPER-V0.14.41.md`
- `TEST-RESULTS-KEYMAPPER-V0.14.41.txt`

## 4. Testes e validação

- 60 testes puros Kotlin/JVM isolados passaram: 48 de câmera/cursor/sensibilidade/políticas, 8 de agregação evdev e 4 de recuperação de lote perdido.
- `evdev_capture.cpp` passou por verificação sintática no host (`g++ -std=c++17 -fsyntax-only`) usando headers JNI e Linux disponíveis.
- Versão, `versionCode`, artifact ID, raiz e integridade do pacote foram conferidos.

O harness usa `kotlinc` JVM 1.9.0 e stubs locais de JUnit; não equivale à execução de `:app:testDebugUnitTest`.

## 5. Build e integração

Foi executado:

`./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace --console=plain`

A execução foi interrompida antes de iniciar o Gradle porque o Wrapper não resolveu `services.gradle.org` (`UnknownHostException`). `ANDROID_HOME` e `ANDROID_SDK_ROOT` não estão configurados. Consequentemente, não houve compilação Android/NDK, Lint, APK nem execução remota do GitHub Actions.

## 6. Pendências físicas

- Validar a recuperação se um callback JNI falhar em runtime.
- Verificar soltura de contatos e botões com Shizuku ativo.
- Testar movimento contínuo em jogos e Samsung DeX.
- Medir latência ponta a ponta e perdas reais sob carga.

## 7. Conclusão

A 0.14.41 adiciona um caminho explícito de recuperação de estado após rejeição de lote. A correção é local, não altera o algoritmo de câmera e executa toda mutação no looper principal. A lógica de recuperação passou nos testes puros; a integração Android ainda depende de build e teste em dispositivo.
