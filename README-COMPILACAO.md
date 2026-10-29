# Como validar e compilar o KeyMapper 0.13.1

## Requisitos

- JDK 17.
- Android SDK com platform 34 e build-tools 34.0.0.
- Acesso à rede para baixar Gradle 8.8 e dependências Maven na primeira execução.
- Dispositivo Android para validar overlay, foco de teclado/mouse e injeção de eventos.

## Build e testes

Na raiz do projeto:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

O workflow `.github/workflows/build.yml` compila Kotlin, executa testes, gera o APK Debug, valida o arquivo e publica o APK como artefato se todas as etapas passarem.

## Estado na criação do pacote 0.13.1

O build foi tentado. O Gradle Wrapper falhou antes da configuração do projeto, ao tentar baixar Gradle 8.8: `UnknownHostException: services.gradle.org`. Nenhum APK foi gerado nesta sessão.

Separadamente, políticas Kotlin puras (`MappingDataPolicy` e `InputBindingPolicy`) foram compiladas e executadas com um harness independente: 12 assertions passaram. Isso não substitui o build Android nem os testes JUnit via Gradle.
