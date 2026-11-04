plugins {
    // AGP 8.5.2 + Kotlin 2.4.21 + Gradle 8.8: KGP 2.4.x aceita esta faixa; o plugin Compose acompanha o Kotlin.
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
