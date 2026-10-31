# KeyMapper 0.14.14 — projeto Android

Este ZIP contém o projeto Android na raiz e o workflow do GitHub Actions para compilar e publicar o APK Debug.

## Configuração de compilação

- Android Gradle Plugin: 8.5.2
- Gradle Wrapper: 8.8
- Kotlin / Compose Compiler Plugin: 2.0.20
- JDK: 17
- compileSdk / targetSdk: 34
- minSdk: 26
- applicationId: `com.example.keymapper`
- versionName / versionCode: `0.14.14` / `29`

O Wrapper 8.8 e o plugin Kotlin 2.0.20 foram mantidos em conjunto. Não substitua o plugin por Kotlin 1.9.24 nem altere isoladamente o Gradle para 8.9 sem atualizar e validar a matriz de compatibilidade do projeto.

## Gerar localmente

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

APK esperado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Compilar pelo GitHub Actions

1. Crie um repositório GitHub vazio.
2. Extraia este ZIP. **Não envie o ZIP como um único arquivo**: o GitHub precisa receber os arquivos e pastas extraídos na raiz do repositório.
3. Envie `app/`, `gradle/`, `.github/` e os arquivos de configuração da raiz (`gradlew`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` etc.). Preserve a pasta oculta `.github/workflows/`.
4. Faça commit. O workflow `Build APK` será iniciado automaticamente; também pode ser iniciado manualmente em Actions usando `workflow_dispatch`.
5. Quando a execução terminar com sucesso, abra a execução em Actions e baixe o artefato `keymapper-debug` na seção Artifacts.

O workflow executa os testes unitários, compila o APK Debug, verifica o pacote e publica o APK com checksum. Um build bem-sucedido no GitHub ainda precisa de teste funcional em aparelho Android antes de ser considerado validado em hardware.
