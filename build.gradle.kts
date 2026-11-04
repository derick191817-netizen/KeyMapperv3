plugins {
    // AGP 8.5 exige Gradle 8.7+; KGP 2.4.21 suporta Gradle 7.6.3–9.7.0 e AGP 8.5.2–9.3.1. Compose acompanha Kotlin.
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
