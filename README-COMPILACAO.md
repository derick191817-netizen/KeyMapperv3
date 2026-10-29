# Compilar o KeyMapper 0.8.0

## Pré-requisitos

- JDK 17
- Android SDK com `platforms;android-34` e `build-tools;34.0.0`
- Acesso à rede na primeira compilação para baixar Gradle 8.8 e dependências

O Gradle Wrapper oficial está incluído na raiz: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` e `gradle/wrapper/gradle-wrapper.properties`. O arquivo de propriedades fixa a distribuição Gradle 8.8 e seu checksum SHA-256 publicado oficialmente.

## Comandos

Linux/macOS/GitHub Actions:

```bash
chmod +x ./gradlew
./gradlew --no-daemon :app:tasks --all
./gradlew --no-daemon :app:compileDebugKotlin --stacktrace
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
./gradlew --no-daemon :app:assembleDebug --stacktrace
```

Windows:

```bat
gradlew.bat :app:compileDebugKotlin --stacktrace
gradlew.bat :app:testDebugUnitTest --stacktrace
gradlew.bat :app:assembleDebug --stacktrace
```

## GitHub Actions

1. Envie o conteúdo deste ZIP para a raiz do repositório, sem criar uma pasta externa extra.
2. Abra **Actions → Build APK → Run workflow** ou faça push.
3. Confirme que as etapas de validação do Wrapper, compilação Kotlin, testes, montagem e validação do APK foram concluídas.
4. Baixe o artefato `keymapper-debug`.

Saída esperada: `app/build/outputs/apk/debug/app-debug.apk`.
O workflow verifica a integridade do APK, confirma o `applicationId` `com.example.keymapper`, gera `SHA256SUMS.txt` e falha quando nenhum APK válido é encontrado.

## Limites de validação desta entrega

O Wrapper foi extraído do artefato produzido pelo workflow v7.8 que terminou com sucesso e seu JAR corresponde ao checksum publicado pelo Gradle. A interface 0.8.0 é uma nova revisão: a compilação de Kotlin, os testes JUnit, o APK e os testes em dispositivo precisam ser executados de novo para esta revisão. O ambiente local de criação não dispõe de Android SDK/Gradle, então não declarar o novo APK compilado antes do workflow passar.
