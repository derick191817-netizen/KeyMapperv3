# Auditoria técnica — KeyMapper 0.14.42

**Data:** 2026-11-04  
**Base:** KeyMapper 0.14.41 / versionCode 57  
**Entrega:** KeyMapper 0.14.42 / versionCode 58  
**Foco:** completar a recuperação de eventos perdidos no transporte JNI/NDK.

## 1. Falha encontrada na auditoria

A versão 0.14.41 tratava a rejeição de um lote pelo `Handler` principal através de `NativeInputBatchLossGuard`. Porém, o C++ `sendBatch()` descartava o vetor em silêncio caso `NewLongArray` falhasse, `SetLongArrayRegion` lançasse uma exceção ou `CallVoidMethod` terminasse com exceção Java. Nesse cenário, o callback Kotlin não recebia o lote e, portanto, não podia marcar recuperação.

Uma sequência perdida pode conter DOWN/UP de teclado/botão ou parte de um vetor relativo de mouse. Sem um marcador explícito, o estado físico poderia ficar retido até outra recuperação global.

## 2. Correção implementada

Novo arquivo `app/src/main/cpp/evdev_batch_recovery.h` define `EvdevBatchRecovery`, classe C++ sem dependência Android/JNI e testável em host.

`sendBatch()` agora:

1. Prepara marcadores pendentes antes dos dados mais recentes.
2. Verifica a criação do `jlongArray`.
3. Verifica a cópia por `SetLongArrayRegion` antes de chamar Java.
4. Verifica exceções após o callback Java.
5. Em qualquer falha, agrega os IDs e classes de todos os dispositivos incluídos no lote.
6. Após entrega bem-sucedida, limpa o conjunto pendente.

Cada marcador é um registro evdev de seis campos contendo `EV_SYN`, `SYN_DROPPED`, o ID e a classe do dispositivo. O consumidor Kotlin já trata esse marcador liberando apenas o estado físico daquele dispositivo e descartando seu relatório relativo parcial.

Quando há recuperação pendente sem entrada nova, o worker usa espera limitada de 20 ms para tentar novamente, sem polling ocupado.

Foi adicionado o contador `batch_delivery_failures` ao diagnóstico, exibido como `falhas_lote_JNI`.

## 3. Arquivos modificados/adicionados

- `app/src/main/cpp/evdev_capture.cpp`
- `app/src/main/cpp/evdev_batch_recovery.h` (novo)
- `tools/native-tests/evdev_batch_recovery_test.cpp` (novo)
- `tools/native-tests/run.sh` (novo)
- `app/build.gradle.kts`
- `.github/workflows/build.yml`
- `README.md`
- `CHANGELOG-KEYMAPPER-V0.14.42.md`
- `AUDITORIA-KEYMAPPER-V0.14.42.md`
- `TEST-RESULTS-KEYMAPPER-V0.14.42.txt`

## 4. Testes executados

- Testes puros Kotlin/JVM focados em câmera, cursor, sensibilidade, agregação evdev, recuperação Kotlin e fila serial: registrados no relatório de testes.
- Testes C++ host do `EvdevBatchRecovery`: executados com `g++ -std=c++17 -Wall -Wextra -Werror`.
- Verificação sintática de `evdev_capture.cpp` com `g++ -std=c++17 -fsyntax-only`, headers JNI e Linux disponíveis no host.
- Validação do YAML, consistência da versão/artefato, estrutura do ZIP e SHA-256.

Os testes host não substituem a compilação NDK/Android nem integração real Binder/JNI.

## 5. Build Android

Foi tentada a execução Gradle de testes, Lint e `assembleDebug`. O Wrapper 8.8 não conseguiu resolver `services.gradle.org` (`UnknownHostException`). `ANDROID_HOME` e `ANDROID_SDK_ROOT` não estão configurados neste ambiente. Não foi produzido APK nem executada compilação Android/NDK.

O workflow GitHub Actions foi atualizado para o artefato `keymapper-0.14.42-debug`, mas não foi executado remotamente nesta sessão.

## 6. Pendências físicas

- Induzir falha real de alocação/cópia/callback JNI e confirmar marcador de recuperação no Binder.
- Desconectar teclado/mouse durante DOWN e confirmar liberação seletiva.
- Validar câmera contínua Sight/Aim e modo Nonstop em jogos reais.
- Validar Samsung Note 20 Ultra / Android 13 / One UI / DeX.
- Medir latência, jitter e perda sob carga a 1000 Hz.

## 7. Conclusão

A 0.14.42 fecha uma lacuna observada entre o worker evdev e a camada Kotlin. A recuperação agora é explícita também quando a falha ocorre antes de o callback Java receber o lote. A política é conservadora: após perda, envia `SYN_DROPPED` por dispositivo, fazendo o consumidor soltar o estado potencialmente inconsistente antes de processar eventos novos.
