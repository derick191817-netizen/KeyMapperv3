# Auditoria técnica — KeyMapper 0.14.43

**Data:** 2026-11-04  
**Base:** KeyMapper 0.14.42 / versionCode 58  
**Entrega:** KeyMapper 0.14.43 / versionCode 59  
**Foco:** fechar a propagação de falhas entre o callback AIDL e a captura JNI.

## 1. Problema identificado

A 0.14.42 corrigiu falhas em `NewLongArray`, `SetLongArrayRegion` e exceções que permanecessem pendentes depois de `CallVoidMethod`. Na auditoria seguinte, foi observado que `KeyMapperShellService.onNativeInputBatch()` capturava `RemoteException` e `Throwable`, registrava a falha e retornava normalmente.

Isso quebrava o contrato de entrega: o JNI via um retorno normal, considerava o lote entregue e limpava `EvdevBatchRecovery`, mesmo quando a chamada para `IKeyMapperInputCallback` havia falhado.

O early return quando `inputCallback == null` também tratava ausência de destino como sucesso silencioso.

## 2. Correção

### `NativeBatchCallbackForwarder`

Novo helper Kotlin puro em `input/NativeBatchCallbackForwarder.kt`:

- Invoca o callback com o lote original.
- Em falha, executa diagnóstico em bloco protegido.
- Relança a mesma instância da exceção original.
- Não permite que uma falha de logging/diagnóstico substitua a exceção que precisa chegar ao JNI.

### `KeyMapperShellService.onNativeInputBatch`

- Callback nulo gera `IllegalStateException`, em vez de ser considerado entregue.
- Encaminhamento passa por `NativeBatchCallbackForwarder`.
- `RemoteException` continua registrando a desconexão e tentando pausar a captura, mas não é mais ocultada: propaga ao `CallVoidMethod`.
- Outras falhas também atravessam JNI após registro diagnóstico.
- O stop normal chama `nativeStopCapture()` e aguarda o `join()` do worker antes de limpar `inputCallback`, evitando que o caminho normal de desligamento produza falso erro de callback ausente.

A sequência de recuperação fica integrada em três níveis:

1. Kotlin `NativeInputBatchLossGuard`: falha ao postar na fila principal.
2. C++ `EvdevBatchRecovery`: falha de alocação/cópia/retorno do callback JNI.
3. Kotlin `NativeBatchCallbackForwarder`: garante que a falha do callback não seja escondida antes de alcançar o JNI.

## 3. Arquivos da 0.14.43

Modificados:
- `app/src/main/java/com/example/keymapper/injection/KeyMapperShellService.kt`
- `app/build.gradle.kts`
- `.github/workflows/build.yml`
- `README.md`

Adicionados:
- `app/src/main/java/com/example/keymapper/input/NativeBatchCallbackForwarder.kt`
- `app/src/test/java/com/example/keymapper/input/NativeBatchCallbackForwarderTest.kt`
- `CHANGELOG-KEYMAPPER-V0.14.43.md`
- `AUDITORIA-KEYMAPPER-V0.14.43.md`
- `TEST-RESULTS-KEYMAPPER-V0.14.43.txt`

O transporte C++ de recuperação e seus testes host permanecem os da versão 0.14.42.

## 4. Testes executados

- 81 testes de regressão Kotlin/JVM da suíte isolada previamente executados nesta sessão.
- 3 testes novos de `NativeBatchCallbackForwarder`, compilados com `kotlinc` e executados por runner local: sucesso normal, relançamento idêntico e falha do callback de diagnóstico sem mascarar a exceção original.
- `tools/native-tests/run.sh`: passou com `g++ -std=c++17 -Wall -Wextra -Werror`.
- `tools/native-tests/check-syntax.sh`: passou para `evdev_capture.cpp` e `uinput_touch.cpp` com headers JNI/Linux.
- Parse do YAML do GitHub Actions e consistência de `versionName=0.14.43`, `versionCode=59` e artefato `keymapper-0.14.43-debug`: passaram.

Total lógico reportado: **84 testes Kotlin/JVM (81 regressões + 3 novos)**. Testes C++ host e verificação de sintaxe são validações adicionais separadas.

Os testes não executam Binder real, JNI no Android, AGP, CMake/NDK dentro do Android Gradle Plugin nem interação com jogos.

## 5. Build e CI

O workflow agora publica o artefato `keymapper-0.14.43-debug` e executa os testes host C++ e a verificação sintática nativa antes das tarefas Android.

A execução Gradle foi tentada, mas o Wrapper 8.8 não conseguiu resolver `services.gradle.org` (`UnknownHostException`). `ANDROID_HOME` e `ANDROID_SDK_ROOT` não estão configurados. Assim:

- `:app:testDebugUnitTest`: não executado via Gradle.
- `:app:lintDebug`: não executado.
- `:app:assembleDebug`: não executado.
- GitHub Actions remoto: não executado nesta sessão.
- APK: não gerado.

As versões AGP 8.5.2 / Gradle 8.8 / JDK 17 / SDK 34 estão alinhadas com a matriz de compatibilidade do AGP 8.5; a falha observada é ambiente/rede, não evidência de incompatibilidade dessas versões.

## 6. Limitações e próximos testes

- Injetar falha AIDL/Binder real e confirmar que JNI mantém marcadores por dispositivo até uma entrega bem-sucedida.
- Validar a retomada do callback após reconexão do serviço Shizuku e verificar que o marcador de recuperação precede novos eventos.
- Desconectar mouse/teclado durante DOWN e validar soltura no motor.
- Testar Sight/Aim e Nonstop em jogos reais, inclusive compatibilidade com o multitouch transitório.
- Validar Note 20 Ultra / Android 13 / One UI / Samsung DeX.
- Medir latência, jitter e perda a 1000 Hz.

## 7. Conclusão

A versão 0.14.43 fecha a lacuna entre a camada AIDL e o mecanismo de recuperação C++ da 0.14.42: uma falha do callback agora pode chegar ao JNI em vez de parecer uma entrega bem-sucedida. É uma correção de integridade de entrada, não uma afirmação de que o build Android ou o comportamento em jogos foi validado.
