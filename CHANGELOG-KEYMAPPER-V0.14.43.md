# KeyMapper 0.14.43

## Propagação de falha do callback JNI

- Corrigido `KeyMapperShellService.onNativeInputBatch()`: antes, as exceções de `IKeyMapperInputCallback.onInputEvents()` eram capturadas e apenas registradas. O callback Java retornava normalmente, fazendo o `CallVoidMethod` do C++ parecer bem-sucedido mesmo quando o Binder não aceitava o lote.
- Criado `NativeBatchCallbackForwarder`, Kotlin puro, para manter o contrato: sucesso retorna normalmente; falha é diagnosticada e a mesma exceção é relançada à camada JNI.
- Falta de callback agora gera `IllegalStateException` explícita. No stop normal, o worker é encerrado/join antes de `inputCallback` ser limpo.
- `RemoteException` continua pausando captura, mas agora atravessa a chamada JNI; o `sendBatch()` da 0.14.42 pode registrar os dispositivos da sequência perdida e manter `SYN_DROPPED` pendente.
- Falhas no logger/diagnóstico não mascaram a exceção original.
- Três testes adicionados: entrega normal, relançamento idêntico da falha e isolamento de falha no diagnóstico.

## Compatibilidade e versão

- `versionName`: `0.14.43`
- `versionCode`: `59`
- Mantém o transporte de recuperação C++ da 0.14.42 e o guard de lote do looper da 0.14.41.
- Não altera as políticas de Sight/Aim, sensibilidade, HUD ou reancoragem.
