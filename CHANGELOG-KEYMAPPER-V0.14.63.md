# KeyMapper 0.14.63

## Robustez da Macro sequencial

- Extraído `MacroSequenceCursor`, componente puro responsável por avançar etapas na ordem configurada e encerrar exatamente no número de ciclos solicitado.
- O executor do `MappingEngine` usa o cursor em vez de manter índice e contagem de ciclos manualmente.
- A lista de etapas é copiada na criação do cursor para impedir que alterações externas mudem uma execução em andamento.
- Testes cobrem sequência vazia, ordem das etapas, 1 e 2 ciclos, término imediato após o último passo e limites de repetição.
- Removida uma verificação duplicada de Combo vazio no executor.
- Mantidos o formato legado de Combo e todos os limites de segurança de passo/duração da 0.14.62.

## Validação

A bateria host de C++/JNI, dependências, toolchain, Shizuku, overlays, HUD, biblioteca e contrato da Macro foi executada e passou. Um harness Kotlin local compilou o modelo, as políticas, o cursor e o round-trip JSON com stubs mínimos de Android/JSON; os cenários de macro passaram.

O Gradle Wrapper foi acionado para `lintDebug`, `testDebugUnitTest` e `assembleDebug`, mas não conseguiu baixar Gradle 8.8: `UnknownHostException: services.gradle.org`. Nenhuma dessas tarefas Android foi executada e nenhum APK foi gerado. Teste físico no Android 13/DeX permanece pendente.
