pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Kadb 2.1.4 resolve SPAKE2 por esta coordenada publicada no JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "KeyMapper"
include(":app")

// Fixa explicitamente o arquivo de build do módulo Android.
project(":app").buildFileName = "build.gradle.kts"
