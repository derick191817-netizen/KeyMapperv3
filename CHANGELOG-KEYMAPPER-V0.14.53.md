# KeyMapper 0.14.53

## Isolamento de sessões do Shizuku e callbacks AIDL

- Cada sessão do `ShizukuInputCaptureBridge` agora cria seu próprio `IKeyMapperInputCallback`, vinculado a um token monotônico de sessão. Eventos e estados entregues por um Binder antigo são descartados depois que a sessão muda.
- `stop()` invalida o token e desativa o bridge imediatamente no thread chamador; a liberação do UserService continua serializada no Handler principal e no worker.
- Respostas tardias de `startInputCapture()` e da recuperação de uinput são verificadas pelo token e pela identidade do Binder antes de tocar no estado atual.
- Um worker antigo não pode limpar o indicador `virtualTouchRecoveryRunning` pertencente à sessão recém-iniciada.
- As operações síncronas de envio/liberação touchscreen validam o token novamente após o Binder retornar, impedindo que uma resposta da sessão anterior publique estado na sessão nova.
- Criado `SessionEpoch` e quatro testes JVM para início, invalidação e substituição de sessões.
- Adicionado `check-shizuku-session-isolation.sh` ao GitHub Actions para fiscalizar o contrato de isolamento e a ordem da invalidação no stop.
- Incrementados `versionCode` para 69 e `versionName` para 0.14.53.

## Decisão de linguagens nesta etapa

- Kotlin foi mantido no bridge porque o código integra diretamente Android Handler, Binder/AIDL e Shizuku; a nova política `SessionEpoch` é pequena, independente e testável na JVM.
- Python continua responsável pelas validações e testes de CI. C++/NDK permanece inalterado porque a correção é de ciclo de vida Binder, não de processamento nativo.
- Rust não foi introduzido: neste subsistema, não há ganho mensurável que compense JNI/FFI e uma segunda cadeia de build.
