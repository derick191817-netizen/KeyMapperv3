# Auditoria KeyMapper 0.14.47 — correção do bloqueio CI

## Resultado

A falha exibida nas capturas foi localizada e corrigida na configuração do módulo Android. O Gradle falhava enquanto compilava `app/build.gradle.kts`, na linha que configurava `jvmTarget` como `String` pelo bloco legado `kotlinOptions`. A falha ocorria durante `:app:lintDebug` porque o script nem sequer conseguia ser avaliado; não era um erro do código do Lint nem um erro de compilação Kotlin das classes do aplicativo.

## Alteração aplicada

Antes:

```kotlin
android {
    kotlinOptions {
        jvmTarget = "17"
    }
}
```

Depois:

```kotlin
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17")
    }
}
```

As opções Java foram preservadas:

```kotlin
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
```

A forma migrada é a documentada pelo Kotlin Gradle Plugin para projetos Android/JVM e converte o alvo de string para o tipo `JvmTarget`.

## Toolchain preservada

| Componente | Versão |
|---|---:|
| Android Gradle Plugin | 8.5.2 |
| Kotlin Gradle Plugin | 2.4.21 |
| Gradle Wrapper | 8.8 |
| JDK no workflow | 17 |
| Android compileSdk | 34 |
| NDK | 26.3.11579264 |
| Java/Kotlin target | 17 |

O aviso do runner sobre Gradle 8.8 e uma versão futura do Kotlin não deve ser confundido com a falha fatal. Nesta entrega, as versões não foram alteradas para evitar misturar a migração do DSL com uma atualização independente do AGP/Gradle.

## Arquivos alterados

- `app/build.gradle.kts`: migração para `compilerOptions` e atualização da versão.
- `.github/workflows/build.yml`: nome do artefato de debug atualizado.
- `README.md`: histórico e estado atualizado.
- `CHANGELOG-KEYMAPPER-V0.14.47.md`: causa raiz e correção.

## Validação executada

| Validação | Resultado |
|---|---|
| Testes nativos host (`tools/native-tests/run.sh`) | Aprovado |
| Sintaxe C++ JNI/NDK (`check-syntax.sh`) | Aprovado |
| Contrato CMake/JNI/diagnóstico | Aprovado |
| Ausência de bloco `kotlinOptions` legado | Aprovado |
| Ausência de atribuição `jvmTarget` em string | Aprovado |
| Java target 17 / Kotlin target 17 | Coerentes |
| versionName/versionCode/CI artifact | Coerentes |
| Workflow YAML | Aprovado |
| Gradle `lintDebug` / `testDebugUnitTest` / `assembleDebug` | Não executados nesta sessão |

## Limitação de execução

O Gradle Wrapper foi executado após tornar `gradlew` executável. Ele tentou baixar Gradle 8.8 e falhou com `UnknownHostException: services.gradle.org`. O ambiente também não tem `ANDROID_HOME` ou `ANDROID_SDK_ROOT` configurados. Portanto, não há prova de compilação Android completa, não há APK e não há execução remota do GitHub Actions nesta sessão.

A correção trata diretamente a mensagem `Using 'jvmTarget: String' is an error` das capturas. A confirmação final deve vir do próximo workflow no GitHub, que agora não deverá falhar nessa linha de configuração. Se surgir outro erro depois da avaliação do script, ele será uma nova falha e precisará ser tratado separadamente.
