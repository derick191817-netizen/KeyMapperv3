# Decisões de arquitetura — KeyMapper 0.14.53

## Isolamento de sessão do serviço privilegiado

`ShizukuInputCaptureBridge` é um singleton de transporte, mas callbacks não devem ser tratados como globais. Cada tentativa lógica de sessão emite um token `SessionEpoch`; o callback AIDL captura esse token e só encaminha dados enquanto ele continuar atual.

A política está isolada em Kotlin JVM puro para permitir teste sem Android. O código de integração permanece Kotlin porque depende diretamente de `Handler`, `ServiceConnection`, `IBinder`, Shizuku e AIDL. A validação estrutural é executada em Python/Bash no CI.

## Critério para novas linguagens

Linguagens podem ser introduzidas por subsistema quando reduzem risco, latência, custo de manutenção ou tornam testes/isolamento significativamente melhores. Não se introduz Rust neste bridge porque ele acrescentaria uma fronteira FFI sem benefício mensurável para operações Binder. O C++ atual continua limitado aos componentes NDK que efetivamente precisam de acesso nativo.
