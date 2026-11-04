# Auditoria KeyMapper 0.14.53 — Isolamento de sessões Shizuku

## Objetivo

Eliminar a possibilidade de callbacks Binder atrasados atravessarem o ciclo de vida de duas sessões diferentes do serviço de captura. A revisão ficou restrita à estabilidade de sessão do Shizuku/uinput; não alterou algoritmos de movimento nem funcionalidades de jogo.

## Falha identificada

O bridge era um singleton e mantinha um único `IKeyMapperInputCallback`. `stop()` enfileirava toda a desativação no Handler principal. Isso criava uma janela entre a solicitação de parada e a execução da limpeza na qual o bridge ainda aceitava callbacks. Como o callback era reutilizado, uma entrega atrasada de uma conexão anterior não carregava identidade suficiente para distinguir a sessão de origem.

Além disso, os workers de inicialização e recuperação limpavam um `AtomicBoolean` compartilhado antes de verificar se o resultado ainda pertencia à sessão ativa. Um worker antigo poderia, portanto, alterar o bookkeeping de uma sessão nova.

## Implementação

1. Adicionado `SessionEpoch`, baseado em `AtomicLong`, para emitir e invalidar tokens monotônicos.
2. Cada chamada efetiva de início cria um novo `IKeyMapperInputCallback` capturando o token daquela sessão. `onInputEvents` e `onCaptureState` verificam `active` e o token antes de encaminhar qualquer dado.
3. `stop()` invalida o token e define `active=false` imediatamente, antes de enfileirar a limpeza do Binder/UserService. A limpeza repete a invalidação na fila principal para cobrir uma chamada de início enfileirada antes da parada.
4. As respostas tardias de `startInputCapture` e recuperação touchscreen verificam token e identidade do Binder antes de alterar estado. O lock de recuperação só é liberado por quem ainda possui uma sessão válida.
5. `sendFrame` e `releaseAll` capturam o token antes da chamada Binder e verificam novamente após o retorno, antes de publicar diagnóstico.
6. Adicionados quatro testes para validar tokens atuais, invalidação, troca entre sessões e rejeição de tokens zero/futuros.
7. Adicionada verificação arquitetural ao workflow para bloquear regressões na criação de callback por sessão e na invalidação imediata do `stop()`.

## Matriz de linguagens

- Kotlin: bridge Android, AIDL, Shizuku, Handler e política de epoch.
- Python: verificações do toolchain e testes/validações executados pelo CI.
- C++17: mantido no leitor evdev e no backend uinput, sem alterações nesta release.
- Rust/JavaScript/TypeScript: não introduzidos. Não há neste subsistema um ganho demonstrado que justifique FFI, runtime ou cadeia de compilação adicionais.

## Validações executadas

- Verificador do toolchain: PASS.
- Quatro cenários negativos do verificador de toolchain: PASS.
- Testes host do lote JNI/evdev: PASS.
- Sintaxe C++ de `evdev_capture.cpp` e `uinput_touch.cpp`: PASS.
- Contrato CMake/JNI/nome da biblioteca: PASS.
- Coordenadas Kadb e fronteira UI/adapter: PASS.
- Verificação estática de isolamento de sessão: PASS.
- Compilação direta da classe `SessionEpoch` com kotlinc e harness JVM: PASS.

## Limites

A execução Gradle de Lint, suíte JUnit completa e `assembleDebug` ainda depende do download do Wrapper e das dependências Android/Maven. Não declarei APK nem teste físico como concluído. A validação de política de epoch não substitui um teste real de reconexão com Shizuku desligado, reiniciado e religado no dispositivo.
