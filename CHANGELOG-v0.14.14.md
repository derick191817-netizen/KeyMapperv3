# KeyMapper 0.14.14

## Atualização de CI
- Versão Android: `versionCode 30`, `versionName 0.14.14`.
- Mantida a combinação AGP 8.5.2, Kotlin 2.0.20 e Gradle Wrapper 8.8.
- Mantido `app/build.gradle.kts` aplicando `com.android.application`, Kotlin Android e Kotlin Compose.
- Workflow agora faz pré-validação explícita dos projetos, tarefas de `:app` e propriedades Android antes dos testes e do APK.
- Mantidos AndroidX, Compose, Shizuku e Material Icons Extended.

## Limitação
A configuração e o empacotamento podem ser verificados localmente, mas o build Android definitivo depende da execução no GitHub Actions com acesso ao SDK e aos repositórios Maven.
