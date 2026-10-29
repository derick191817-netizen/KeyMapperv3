# Relatório técnico — KeyMapper 0.9.1

## Escopo

Atualização incremental a partir da última cópia local verificável da interface mobile (pacote 0.8.0 / v7.9), com foco em robustez numérica do pipeline de mira. O arquivo ZIP nomeado v9.0 encontrado no ambiente desta sessão tinha tamanho zero; por isso não foi usado como fonte de código. As alterações são reconstruídas sobre a cópia v7.9 íntegra e versionadas como 0.9.1.

## Arquivos alterados

- `app/src/main/java/com/example/keymapper/input/VirtualCursorController.kt`
- `app/src/main/java/com/example/keymapper/input/MouseInputProcessor.kt`
- `app/src/main/java/com/example/keymapper/domain/SensitivityEngine.kt`
- `app/src/main/java/com/example/keymapper/mapping/MappingEngine.kt`
- `app/src/main/java/com/example/keymapper/injection/TouchInjector.kt`
- `app/build.gradle.kts`
- `app/src/test/java/com/example/keymapper/domain/SensitivityEngineTest.kt`
- `app/src/test/java/com/example/keymapper/input/VirtualCursorControllerTest.kt`
- `app/src/test/java/com/example/keymapper/input/MouseInputProcessorTest.kt`
- `CHANGELOG-v0.9.1.md`, `README-COMPILACAO.md`

## Verificações feitas

- Compilação isolada com `kotlinc` de `SensitivityEngine`, `VirtualCursorController`, `MouseInputProcessor` e harness temporário.
- Seis grupos do harness manual passaram: curva linear, acumulação fracionária, fallback de sensibilidade inválida, saturação em valores extremos, wrap/reset do cursor e validação do processador de mouse.
- Integridade do Wrapper JAR: SHA-256 `cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8`, igual ao valor validado pelo workflow.
- Workflow YAML carregado por parser local; Manifest e todos os recursos XML analisados por parser XML sem erro.
- ZIP final validado por CRC e verificação estrutural após o empacotamento.
- Testes JUnit adicionados no projeto: 16 métodos no total entre sensibilidade, cursor virtual e processador de mouse. Foram compilados/executados localmente com um harness compatível de testes e 16/16 passaram; isto não equivale à execução do Gradle/JUnit runner.

## Não executado

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:compileDebugKotlin`
- `./gradlew :app:assembleDebug`
- Teste instrumentado no Android.
- Teste de compatibilidade do modo mouse relativo em jogos.

O Wrapper tentou baixar Gradle 8.8 e falhou com `UnknownHostException: services.gradle.org`. Portanto, esta versão não deve ser classificada como build Android aprovado até a execução no GitHub Actions.
