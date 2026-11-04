# Compatibilidade do toolchain — KeyMapper 0.14.52

## Matriz validada

| Componente | Projeto | Regra verificada |
|---|---:|---|
| Android Gradle Plugin | 8.5.2 | AGP 8.5 exige Gradle 8.7 ou superior |
| Kotlin Gradle Plugin | 2.4.21 | Faixa oficial KGP 2.4.20–2.4.21: Gradle 7.6.3–9.7.0 e AGP 8.5.2–9.3.1 |
| Compose Compiler Plugin | 2.4.21 | Deve usar a mesma versão do Kotlin |
| Gradle Wrapper | 8.8 | Dentro da faixa KGP e acima do mínimo do AGP |
| JDK / Java / Kotlin JVM | 17 | Alinhados entre CI e módulo Android |
| Android SDK | 34 | `compileSdk` e pacote do runner alinhados |
| NDK | 26.3.11579264 | Alinhado entre módulo e runner |
| CMake | 3.22.1 | Alinhado entre módulo e runner |

## Fontes oficiais consultadas em 4 de novembro de 2026

- Kotlin — tabela de compatibilidade KGP/Gradle/AGP: https://kotlinlang.org/docs/gradle-configure-project.html
- Android Developers — versão mínima do Gradle por AGP: https://developer.android.com/build/releases/about-agp

A hipótese de incompatibilidade entre Kotlin 2.4.21 e Gradle 8.8 foi descartada. A matriz oficial suporta essa combinação; o AGP 8.5 exige Gradle 8.7 ou posterior. Portanto, a versão 8.8 já usada pelo projeto é compatível.

## Implementação

`tools/build-tests/check-toolchain-compatibility.py` faz uma verificação estática independente de Gradle e de bibliotecas Android. `tools/build-tests/test-toolchain-compatibility.py` verifica que a barreira falha quando o Compose se desalinha, o Gradle cai abaixo do mínimo, o NDK desaparece do runner ou o nome do artefato diverge. Ela valida versões, igualdade Kotlin/Compose, faixa suportada, JDK/JVM, SDK, NDK, CMake, presença do checksum SHA-256 configurado no wrapper e nome do artefato. O workflow executa a verificação antes dos testes nativos e das tarefas Gradle.

A verificação é executada imediatamente após o checkout, antes de preparar JDK, Android SDK e Gradle. É intencionalmente conservadora: ao trocar a linha principal do Kotlin, ela falha solicitando revisão da matriz oficial, em vez de assumir compatibilidade de uma versão não auditada.
