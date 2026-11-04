# Compatibilidade do toolchain — KeyMapper 0.14.66

| Componente | Versão | Critério |
|---|---:|---|
| Android Gradle Plugin | 8.5.2 | AGP requer Gradle >= 8.7 |
| Kotlin Gradle Plugin | 2.4.21 | Gradle 7.6.3–9.7.0; AGP 8.5.2–9.3.1 |
| Compose plugin | 2.4.21 | Alinhado ao Kotlin |
| Gradle Wrapper | 8.14.6 | >= 8.14.4 pedido pelo aviso do pipeline; dentro da faixa KGP |
| JDK / Java / Kotlin JVM | 17 | Alinhados |
| Android SDK | 34 | compileSdk 34 |
| NDK | 26.3.11579264 | Alinhado ao workflow |
| CMake | 3.22.1 | Alinhado ao workflow |

## Justificativa

O log do Actions avisou que Kotlin 2.5.0 elevaria o mínimo recomendado para Gradle 8.14.4. Embora o build.gradle.kts consultado esteja em Kotlin 2.4.21, o Wrapper foi atualizado para Gradle 8.14.6, a correção de manutenção publicada mais recente da linha 8.14 verificada. A matriz de KGP 2.4.20–2.4.21 permite Gradle 7.6.3–9.7.0 e AGP 8.5.2–9.3.1.

Fontes consultadas em 4 de novembro de 2026:
- https://kotlinlang.org/docs/gradle-configure-project.html
- https://gradle.org/release-checksums/

O Wrapper contém o checksum oficial SHA-256 da distribuição `gradle-8.14.6-bin.zip`. A verificação de toolchain valida os limites numéricos em vez de fixar a antiga versão 8.8.
