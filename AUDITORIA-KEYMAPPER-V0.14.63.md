# Auditoria técnica — KeyMapper 0.14.63

## Base e escopo

Continuação da 0.14.62. O escopo foi reduzir a complexidade e a ambiguidade do executor de Macro, não introduzir novos tipos de controles.

## Alteração principal

A 0.14.62 mantinha avanço de sequência por `stepIndex` e ciclos por `cycle` diretamente em `MacroRun`. Na 0.14.63, essa responsabilidade foi extraída para `MacroSequenceCursor`, que retorna uma etapa por chamada, repete até o limite normalizado de 1–20 ciclos e reporta término imediatamente após a última etapa do ciclo final. A lista é copiada na construção para manter snapshot estável.

`MappingEngine` continua sendo o executor de I/O: agenda WAIT, injeta TAP/HOLD, interpola SWIPE e cancela ao soltar o gatilho, pausar ou executar `releaseAll()`. O cursor não conhece Android Handler nem TouchInjector e pode ser testado isoladamente.

## Compatibilidade

- `HudButton.macro: List<Int>` continua reservado ao Combo simultâneo.
- `HudButton.sequence: List<MacroStep>` continua reservado à Macro sequencial.
- O cursor trabalha somente com etapas já normalizadas pela `MacroStepPolicy`.
- A estrutura JSON de perfis existente não foi alterada, exceto pelo campo de sequência introduzido na 0.14.62.

## Testes

- JUnit adiciona cobertura de ordem, repetição, sequência vazia e término da execução.
- Harness Kotlin local compilou `Rot`, `MacroStep`, `HudButton`, `MappingDataPolicy`, `MappingEditorPolicy` e um programa de verificação com stubs mínimos para Android `KeyEvent` e JSON. O round-trip JSON e a separação Combo/Macro passaram.
- Os verificadores host de C++17/evdev/JNI, Kadb/Jetifier, Shizuku, HUD/overlay, tipos de controles, contrato Macro e toolchain passaram.
- O Wrapper Gradle não conseguiu baixar Gradle 8.8 por `UnknownHostException: services.gradle.org`; portanto Lint, JUnit executado pelo Gradle e APK não estão validados.

## Limitações restantes

A execução Macro é temporizada pelo relógio do processo, não sincronizada com frames ou estado interno do jogo. Macro é cancelada na soltura/pausa; nenhuma repetição indefinida é permitida. Entrada analógica física de gamepad não foi implementada. Build Android e teste físico no Android 13/One UI/DeX permanecem necessários.

## Integração remota

Consulta ao GitHub confirma que `main` continua no commit `ebc7c148f3a51af8b6614513aa558950580a4186` (versão 0.14.55). A tentativa anterior de escrita/criação de branch recebeu HTTP 403 `Resource not accessible by integration`; esta entrega, portanto, permanece local e não abre PR nem altera `main`.
