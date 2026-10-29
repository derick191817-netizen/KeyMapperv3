# Relatório técnico — KeyMapper 0.10.0

## Base e escopo

Esta atualização foi feita sobre a última cópia de projeto disponível no ambiente (`keymapper_v80_work`, linha 0.9.1), não sobre um checkout remoto verificado. O pacote anterior `KeyMapper-v9.2-editor-sobre-jogo.zip` existe no ambiente, mas não há credenciais nem conexão confirmada com o repositório remoto; não afirmo que o código corresponde ao estado mais recente do GitHub.

## Mudanças realizadas

- `app/build.gradle.kts`: versão incrementada para `versionCode 11`, `versionName 0.10.0`.
- `app/src/test/java/com/example/keymapper/input/VirtualCursorControllerTest.kt`: adicionados dois testes de regressão para wrap do cursor lógico e mudanças rápidas de direção.
- `CHANGELOG-v0.10.0.md`: changelog da atualização.
- `RELATORIO-V0.10.0.md`: este relatório.

## Revisão do código existente

- `OverlayManager` cria janelas `TYPE_APPLICATION_OVERLAY` e mantém uma bolha flutuante para abrir o menu/editor.
- `HudView` desenha e permite manipular controles no overlay; durante edição, a janela HUD é tocável, e durante execução é marcada como não tocável para deixar a interação normal atravessar.
- `CaptureView` processa `AXIS_RELATIVE_X/Y`, soma amostras históricas e usa diferença de coordenadas como fallback somente quando não há captura de ponteiro e os deltas relativos estão zerados.
- `MappingEngine` encaminha deltas processados e utiliza `VirtualCursorController` como estado lógico separado da posição visual.
- A injeção continua condicionada ao mecanismo existente e às permissões/compatibilidade do dispositivo; overlay não é sinônimo de autorização para injetar entradas em todo app.

## Validações executadas

- O comando `./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace` foi tentado.
- **Resultado:** falhou antes da configuração do projeto porque o Gradle Wrapper não conseguiu baixar Gradle 8.8: `UnknownHostException: services.gradle.org`.
- Não foi possível executar Gradle/JUnit, compilar Kotlin Android, gerar APK ou fazer teste instrumentado nesta sessão.
- Os arquivos do pacote serão validados estruturalmente após o empacotamento; isso não equivale a compilação.

## Estado final honesto

**Ainda não compilado nesta sessão.** Há testes unitários adicionados ao projeto, mas não executados pelo Gradle. O overlay e o pipeline de entrada foram inspecionados estaticamente, não validados em dispositivo. A principal pendência é executar o workflow no GitHub Actions ou em ambiente com Gradle 8.8 e Android SDK disponíveis e depois testar sobre um jogo real.
